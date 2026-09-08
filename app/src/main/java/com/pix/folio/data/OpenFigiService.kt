package com.pix.folio.data

import com.pix.folio.model.InvestmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small OpenFIGI v3 client used only for identifier resolution.
 * No API key is required for normal Folio usage; unauthenticated requests
 * simply use OpenFIGI's lower public rate limit.
 */
object OpenFigiService {
    private const val MAPPING_URL = "https://api.openfigi.com/v3/mapping"

    data class Instrument(
        val name: String,
        val ticker: String,
        val figi: String,
        val shareClassFigi: String,
        val compositeFigi: String,
        val exchange: String,
        val securityType: String,
        val securityType2: String,
        val marketSector: String,
    ) {
        val kind: InvestmentKind
            get() {
                val text = "$securityType $securityType2 $marketSector".lowercase()
                return when {
                    "etf" in text || "exchange traded" in text || "etp" in text -> InvestmentKind.ETF
                    "fund" in text || "mutual" in text -> InvestmentKind.FUND
                    "common stock" in text || "equity" in text -> InvestmentKind.STOCK
                    else -> InvestmentKind.OTHER
                }
            }
    }

    suspend fun lookupIsin(rawIsin: String): Result<List<Instrument>> = withContext(Dispatchers.IO) {
        runCatching {
            val isin = rawIsin.trim().uppercase()
            require(isValidIsin(isin)) { "Enter a valid 12-character ISIN." }

            val connection = (URL(MAPPING_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val body = JSONArray().put(
                JSONObject()
                    .put("idType", "ID_ISIN")
                    .put("idValue", isin)
            ).toString()

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (code == 429) error("OpenFIGI rate limit reached. Try again in a moment.")
            if (code !in 200..299) error("OpenFIGI lookup failed ($code).")

            val root = JSONArray(text)
            if (root.length() == 0) return@runCatching emptyList()
            val first = root.optJSONObject(0) ?: return@runCatching emptyList()
            first.optString("error").takeIf { it.isNotBlank() }?.let { error(it) }
            val data = first.optJSONArray("data") ?: return@runCatching emptyList()

            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val name = item.optString("name").ifBlank { item.optString("securityDescription") }
                    if (name.isBlank()) continue
                    add(
                        Instrument(
                            name = name,
                            ticker = item.optString("ticker"),
                            figi = item.optString("figi"),
                            shareClassFigi = item.optString("shareClassFIGI"),
                            compositeFigi = item.optString("compositeFIGI"),
                            exchange = item.optString("exchCode"),
                            securityType = item.optString("securityType"),
                            securityType2 = item.optString("securityType2"),
                            marketSector = item.optString("marketSector"),
                        )
                    )
                }
            }
                .distinctBy { listOf(it.figi, it.ticker, it.exchange).joinToString("|") }
                .sortedWith(
                    compareBy<Instrument> { if (it.kind == InvestmentKind.ETF || it.kind == InvestmentKind.FUND) 0 else 1 }
                        .thenBy { it.exchange }
                )
                .take(12)
        }
    }

    fun isValidIsin(value: String): Boolean {
        val isin = value.trim().uppercase()
        if (!Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]").matches(isin)) return false

        val expanded = buildString {
            isin.forEach { ch ->
                if (ch.isDigit()) append(ch) else append(ch.code - 'A'.code + 10)
            }
        }

        var sum = 0
        var doubleDigit = false
        for (i in expanded.lastIndex downTo 0) {
            var digit = expanded[i].digitToInt()
            if (doubleDigit) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            doubleDigit = !doubleDigit
        }
        return sum % 10 == 0
    }
}
