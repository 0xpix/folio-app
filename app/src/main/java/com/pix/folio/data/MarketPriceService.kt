package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * Best-effort public market data used by Folio for portfolio tracking.
 *
 * Securities use Yahoo Finance's public chart endpoint with the resolved ticker.
 * CS2 items use the Steam Community Market price-overview endpoint.
 * Both are intentionally optional: failed refreshes never block local finance tracking.
 */
object MarketPriceService {
    data class Quote(
        val price: Double,
        val currency: String,
        val symbol: String,
        val source: String,
    )

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

    private fun fetchQuote(holding: InvestmentHolding): Quote =
        if (holding.kind == InvestmentKind.CS2) fetchSteamQuote(holding) else fetchYahooQuote(holding)

    private fun fetchYahooQuote(holding: InvestmentHolding): Quote {
        val ticker = yahooTicker(holding)
        require(ticker.isNotBlank()) { "missing price symbol" }
        val encoded = URLEncoder.encode(ticker, StandardCharsets.UTF_8.toString())
        val json = JSONObject(getText("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=5d&interval=1d"))
        val result = json.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
            ?: error("market quote unavailable")
        val meta = result.optJSONObject("meta") ?: JSONObject()
        val regular = meta.optDouble("regularMarketPrice", Double.NaN)
        val quoteArray = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close")
        val latestClose = if (quoteArray == null) Double.NaN else {
            var value = Double.NaN
            for (index in 0 until quoteArray.length()) {
                if (!quoteArray.isNull(index)) {
                    val candidate = quoteArray.optDouble(index, Double.NaN)
                    if (candidate.isFinite() && candidate > 0.0) value = candidate
                }
            }
            value
        }
        val price = when {
            regular.isFinite() && regular > 0.0 -> regular
            latestClose.isFinite() && latestClose > 0.0 -> latestClose
            else -> error("market quote unavailable")
        }
        return Quote(
            price = price,
            currency = meta.optString("currency").ifBlank { holding.priceCurrency.ifBlank { "EUR" } },
            symbol = ticker,
            source = "Yahoo Finance",
        )
    }

    private fun fetchSteamQuote(holding: InvestmentHolding): Quote {
        val marketName = holding.marketHashName.ifBlank { holding.name }
        require(marketName.isNotBlank()) { "missing Steam market name" }
        val encoded = URLEncoder.encode(marketName, StandardCharsets.UTF_8.toString())
        val json = JSONObject(
            getText("https://steamcommunity.com/market/priceoverview/?appid=730&currency=3&market_hash_name=$encoded")
        )
        if (!json.optBoolean("success", false)) error("Steam market quote unavailable")
        val raw = json.optString("median_price").ifBlank { json.optString("lowest_price") }
        val price = parseLocalizedMoney(raw) ?: error("Steam market quote unavailable")
        return Quote(price, "EUR", marketName, "Steam Community Market")
    }

    private fun yahooTicker(holding: InvestmentHolding): String {
        val base = holding.priceSymbol.ifBlank { holding.symbol }.trim().uppercase()
        if (base.isBlank() || '.' in base) return base
        return when (holding.exchange.trim().uppercase()) {
            "GR", "GY", "GF", "GM", "XETR", "XFRA" -> "$base.DE"
            "LN", "LSE" -> "$base.L"
            "FP", "PAR" -> "$base.PA"
            "NA", "AS" -> "$base.AS"
            "SW", "VX" -> "$base.SW"
            "IM", "MI" -> "$base.MI"
            "SM", "MC" -> "$base.MC"
            "SS" -> "$base.SS"
            "SZ" -> "$base.SZ"
            "HK" -> "$base.HK"
            "JT", "JP" -> "$base.T"
            else -> base
        }
    }

    private fun getText(url: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Folio/0.7 Android")
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
