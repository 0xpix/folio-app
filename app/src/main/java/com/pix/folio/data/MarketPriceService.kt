package com.pix.folio.data

import android.content.Context
import com.pix.folio.BuildConfig
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Best-effort, key-free market data for Folio.
 *
 * Securities use Yahoo Finance's public chart surface. Symbols are resolved generically from the
 * stored/exchange-aware ticker first, then Yahoo search using ISIN and security name. No individual
 * security or user holding is special-cased in code.
 *
 * CS2 items use the Steam Community Market price-overview endpoint.
 */
object MarketPriceService {
    data class Quote(
        val price: Double,
        val currency: String,
        val symbol: String,
        val source: String,
        val delayMinutes: Int = 0,
    )

    data class HistoryPoint(val date: LocalDate, val close: Double)

    data class MarketHistory(
        val points: List<HistoryPoint>,
        val currency: String,
        val symbol: String,
        val source: String,
        val delayMinutes: Int,
    ) {
        val firstPrice: Double? get() = points.firstOrNull()?.close
        val latestPrice: Double? get() = points.lastOrNull()?.close
    }

    data class RefreshReport(
        val updated: Int,
        val failed: Int,
        val messages: List<String>,
    )

    suspend fun refreshAll(context: Context): RefreshReport = withContext(Dispatchers.IO) {
        val store = FolioStore(context.applicationContext)
        val holdings = store.summary().investments.filter { it.canAutoPrice }
        var updated = 0
        var failed = 0
        val messages = mutableListOf<String>()

        holdings.forEach { holding ->
            val quote = runCatching { fetchQuote(holding) }
                .onFailure { messages += "${holding.name}: ${it.message ?: "price unavailable"}" }
                .getOrNull()

            if (quote == null || quote.price <= 0.0) {
                failed += 1
            } else {
                store.recordAutomatedInvestmentPrice(
                    holdingId = holding.id,
                    close = quote.price,
                    currency = quote.currency,
                    symbol = quote.symbol,
                    source = quote.source,
                    date = LocalDate.now(),
                )
                updated += 1
            }
        }

        RefreshReport(updated, failed, messages.take(8))
    }

    suspend fun fetchHistory(holding: InvestmentHolding, fromDate: LocalDate): MarketHistory =
        withContext(Dispatchers.IO) {
            require(holding.kind != InvestmentKind.CS2) {
                "Historical Steam Market data is not available from the public price endpoint"
            }
            fetchYahooHistory(holding, fromDate)
        }

    private fun fetchQuote(holding: InvestmentHolding): Quote =
        if (holding.kind == InvestmentKind.CS2) fetchSteamQuote(holding) else fetchYahooQuote(holding)

    private fun fetchYahooQuote(holding: InvestmentHolding): Quote {
        val errors = mutableListOf<String>()
        resolveYahooCandidates(holding).forEach { ticker ->
            runCatching { fetchYahooQuoteForSymbol(holding, ticker) }
                .onSuccess { return it }
                .onFailure { errors += "$ticker: ${it.message ?: "unavailable"}" }
        }
        error(errors.lastOrNull() ?: "market quote unavailable")
    }

    private fun fetchYahooQuoteForSymbol(holding: InvestmentHolding, ticker: String): Quote {
        val encoded = encode(ticker)
        val json = JSONObject(
            getText("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=1d&interval=5m&includePrePost=false")
        )
        val result = json.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
            ?: error("market quote unavailable")
        val meta = result.optJSONObject("meta") ?: JSONObject()
        val regular = meta.optDouble("regularMarketPrice", Double.NaN)
        val quoteArray = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close")
        val latestClose = latestPositive(quoteArray)
        val price = when {
            regular.isFinite() && regular > 0.0 -> regular
            latestClose.isFinite() && latestClose > 0.0 -> latestClose
            else -> error("market quote unavailable")
        }
        val delay = meta.optInt("exchangeDataDelayedBy", 0).coerceAtLeast(0)
        return Quote(
            price = price,
            currency = meta.optString("currency").ifBlank { holding.priceCurrency.ifBlank { "EUR" } },
            symbol = meta.optString("symbol").ifBlank { ticker },
            source = yahooSource(delay),
            delayMinutes = delay,
        )
    }

    private fun fetchYahooHistory(holding: InvestmentHolding, fromDate: LocalDate): MarketHistory {
        val errors = mutableListOf<String>()
        resolveYahooCandidates(holding).forEach { ticker ->
            runCatching { fetchYahooHistoryForSymbol(holding, fromDate, ticker) }
                .onSuccess { return it }
                .onFailure { errors += "$ticker: ${it.message ?: "unavailable"}" }
        }
        error(errors.lastOrNull() ?: "price history unavailable")
    }

    private fun fetchYahooHistoryForSymbol(
        holding: InvestmentHolding,
        fromDate: LocalDate,
        ticker: String,
    ): MarketHistory {
        val encoded = encode(ticker)
        val start = fromDate.coerceAtMost(LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?period1=$start&period2=$end&interval=1d&events=history&includeAdjustedClose=true"
        val json = JSONObject(getText(url))
        val result = json.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
            ?: error("price history unavailable")
        val meta = result.optJSONObject("meta") ?: JSONObject()
        val timestamps = result.optJSONArray("timestamp") ?: error("price history unavailable")
        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
        val closes = quote?.optJSONArray("close") ?: error("price history unavailable")
        val zone = runCatching {
            ZoneId.of(meta.optString("exchangeTimezoneName").ifBlank { "UTC" })
        }.getOrDefault(ZoneOffset.UTC)

        val points = buildList {
            val count = minOf(timestamps.length(), closes.length())
            for (index in 0 until count) {
                if (timestamps.isNull(index) || closes.isNull(index)) continue
                val epoch = timestamps.optLong(index, 0L)
                val close = closes.optDouble(index, Double.NaN)
                if (epoch <= 0L || !close.isFinite() || close <= 0.0) continue
                val date = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
                if (!date.isBefore(fromDate)) add(HistoryPoint(date, close))
            }
        }.distinctBy { it.date }.sortedBy { it.date }

        if (points.isEmpty()) error("no market history found from $fromDate")
        val delay = meta.optInt("exchangeDataDelayedBy", 0).coerceAtLeast(0)
        return MarketHistory(
            points = points,
            currency = meta.optString("currency").ifBlank { holding.priceCurrency.ifBlank { "EUR" } },
            symbol = meta.optString("symbol").ifBlank { ticker },
            source = yahooSource(delay),
            delayMinutes = delay,
        )
    }

    private fun resolveYahooCandidates(holding: InvestmentHolding): List<String> {
        val candidates = linkedSetOf<String>()
        val isin = holding.isin.trim().uppercase()

        listOf(holding.priceSymbol, holding.symbol)
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { raw ->
                val upper = raw.uppercase()
                candidates += yahooTickerForExchange(upper, holding.exchange)
                candidates += upper
                if (
                    '.' !in upper &&
                    holding.exchange.isBlank() &&
                    holding.kind in setOf(InvestmentKind.ETF, InvestmentKind.FUND, InvestmentKind.INDEX, InvestmentKind.BOND)
                ) {
                    searchYahooSymbols(if (isin.isNotBlank()) isin else holding.name, holding)
                        .forEach(candidates::add)
                }
            }

        if (isin.isNotBlank()) searchYahooSymbols(isin, holding).forEach(candidates::add)
        if (holding.name.isNotBlank()) searchYahooSymbols(holding.name, holding).forEach(candidates::add)
        return candidates.filter(String::isNotBlank)
    }

    private fun searchYahooSymbols(query: String, holding: InvestmentHolding): List<String> {
        if (query.isBlank()) return emptyList()
        val url = "https://query1.finance.yahoo.com/v1/finance/search?q=${encode(query)}&quotesCount=12&newsCount=0&listsCount=0"
        val json = runCatching { JSONObject(getText(url)) }.getOrNull() ?: return emptyList()
        val quotes = json.optJSONArray("quotes") ?: JSONArray()
        val rows = buildList {
            for (index in 0 until quotes.length()) {
                val row = quotes.optJSONObject(index) ?: continue
                val symbol = row.optString("symbol").trim().uppercase()
                if (symbol.isBlank()) continue
                val quoteType = row.optString("quoteType").uppercase()
                if (quoteType !in setOf("ETF", "EQUITY", "MUTUALFUND", "INDEX")) continue
                val name = listOf(row.optString("shortname"), row.optString("longname")).joinToString(" ").uppercase()
                val exchange = row.optString("exchange").uppercase()
                val score = buildScore(symbol, name, exchange, holding)
                add(score to symbol)
            }
        }
        return rows.sortedByDescending { it.first }.map { it.second }.distinct()
    }

    private fun buildScore(symbol: String, name: String, exchange: String, holding: InvestmentHolding): Int {
        var score = 0
        val expectedSuffix = yahooSuffixForExchange(holding.exchange)
        if (expectedSuffix != null && symbol.endsWith(expectedSuffix)) score += 12
        if (holding.exchange.isNotBlank() && exchange.contains(holding.exchange.trim().uppercase())) score += 6
        if (holding.kind == InvestmentKind.ETF && "ETF" in name) score += 5
        val words = holding.name.uppercase().split(Regex("[^A-Z0-9]+"))
            .filter { it.length >= 4 }
            .distinct()
        score += words.count { it in name } * 2
        if (holding.isin.isNotBlank() && holding.isin.uppercase() in name) score += 20
        return score
    }

    private fun yahooTickerForExchange(raw: String, exchange: String): String {
        if (raw.isBlank() || '.' in raw) return raw
        return yahooSuffixForExchange(exchange)?.let { suffix -> "$raw$suffix" } ?: raw
    }

    private fun yahooSuffixForExchange(exchange: String): String? = when (exchange.trim().uppercase()) {
        "GR", "GY", "GF", "GM", "GER", "XETR", "XETRA", "XFRA", "FRA" -> ".DE"
        "LN", "LSE" -> ".L"
        "FP", "PAR" -> ".PA"
        "NA", "AS" -> ".AS"
        "SW", "VX" -> ".SW"
        "IM", "MI" -> ".MI"
        "SM", "MC" -> ".MC"
        "SS" -> ".SS"
        "SZ" -> ".SZ"
        "HK" -> ".HK"
        "JT", "JP" -> ".T"
        else -> null
    }

    private fun fetchSteamQuote(holding: InvestmentHolding): Quote {
        val marketName = holding.marketHashName.ifBlank { holding.name }
        require(marketName.isNotBlank()) { "missing Steam market name" }
        val encoded = encode(marketName)
        val json = JSONObject(
            getText("https://steamcommunity.com/market/priceoverview/?appid=730&currency=3&market_hash_name=$encoded")
        )
        if (!json.optBoolean("success", false)) error("Steam market quote unavailable")
        val raw = json.optString("median_price").ifBlank { json.optString("lowest_price") }
        val price = parseLocalizedMoney(raw) ?: error("Steam market quote unavailable")
        return Quote(price, "EUR", marketName, "Steam Community Market · indicative")
    }

    private fun yahooSource(delayMinutes: Int): String =
        if (delayMinutes > 0) "Yahoo Finance · ${delayMinutes}m delayed"
        else "Yahoo Finance · indicative live"

    private fun latestPositive(array: JSONArray?): Double {
        if (array == null) return Double.NaN
        var value = Double.NaN
        for (index in 0 until array.length()) {
            if (!array.isNull(index)) {
                val candidate = array.optDouble(index, Double.NaN)
                if (candidate.isFinite() && candidate > 0.0) value = candidate
            }
        }
        return value
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun getText(url: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Folio/${BuildConfig.VERSION_NAME} Android")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLocalizedMoney(value: String): Double? {
        val compact = value.filter { it.isDigit() || it == ',' || it == '.' }
        if (compact.isBlank()) return null
        val comma = compact.lastIndexOf(',')
        val dot = compact.lastIndexOf('.')
        val normalized = when {
            comma >= 0 && dot >= 0 && comma > dot -> compact.replace(".", "").replace(',', '.')
            comma >= 0 && dot >= 0 -> compact.replace(",", "")
            comma >= 0 -> compact.replace(',', '.')
            else -> compact
        }
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
    }
}
