package com.pix.folio.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class ScalablePerformanceSnapshot(
    val timeframe: String,
    val absoluteReturn: Double,
)

data class ScalableHoldingSnapshot(
    val isin: String,
    val name: String,
    val securityType: String,
    val quantity: Double?,
    val valuation: Double,
    val valuationCurrency: String,
    val quoteMidPrice: Double?,
    val quoteCurrency: String?,
    val quoteTimestampUtc: String?,
    val quoteOutdated: Boolean,
)

data class ScalableSnapshot(
    val createdAtUtc: Instant,
    val valuationTimestampUtc: String?,
    val cliVersion: String?,
    val currency: String,
    val totalValue: Double,
    val securitiesValue: Double,
    val cryptoValue: Double,
    val performance: List<ScalablePerformanceSnapshot>,
    val holdings: List<ScalableHoldingSnapshot>,
) {
    /** Investment value only. Broker total may also include broker cash/credit. */
    val investmentValue: Double get() = securitiesValue + cryptoValue

    val primaryAbsoluteReturn: ScalablePerformanceSnapshot?
        get() {
            val priority = listOf(
                "MAX",
                "ALL",
                "ALL_TIME",
                "SINCE_INCEPTION",
                "SINCE_BUY",
                "SINCE_BUYING",
            )
            return priority.firstNotNullOfOrNull { wanted ->
                performance.firstOrNull { it.timeframe.equals(wanted, ignoreCase = true) }
            }
        }

    fun holdingForIsin(isin: String): ScalableHoldingSnapshot? =
        holdings.firstOrNull { it.isin.equals(isin.trim(), ignoreCase = true) }
}

object ScalableSnapshotCodec {
    const val FORMAT = "folio-scalable-snapshot"
    const val VERSION = 1

    /**
     * Parse only Folio's deliberately minimal Scalable snapshot format.
     *
     * Unknown fields are ignored and are never written back to encrypted storage. This means even
     * if somebody imports a file containing account_id/portfolio_id/name metadata, Folio stores only
     * the allowlisted valuation fields below.
     */
    fun parse(raw: String): ScalableSnapshot {
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Not a Folio Scalable snapshot" }
        require(root.optInt("version", 0) == VERSION) { "Unsupported Scalable snapshot version" }
        require(root.optString("source") == "scalable-cli") { "Snapshot must come from Scalable CLI" }

        val createdAt = Instant.parse(root.getString("created_at_utc"))
        val currency = root.optString("currency").trim().uppercase()
        require(currency == "EUR") { "Only EUR Scalable broker snapshots are supported" }

        val valuation = root.getJSONObject("valuation")
        val total = valuation.requireFiniteMoney("total")
        val securities = valuation.requireFiniteMoney("securities")
        val crypto = valuation.optFiniteMoney("crypto") ?: 0.0
        require(total >= 0.0 && securities >= 0.0 && crypto >= 0.0) {
            "Scalable valuation cannot be negative"
        }

        val performance = buildList {
            val rows = root.optJSONArray("performance") ?: JSONArray()
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val timeframe = row.optString("timeframe").trim()
                val absolute = row.optFiniteMoney("simple_absolute_return") ?: continue
                if (timeframe.isNotBlank()) add(ScalablePerformanceSnapshot(timeframe, absolute))
            }
        }

        val holdings = buildList {
            val rows = root.optJSONArray("holdings") ?: JSONArray()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val isin = row.getString("isin").trim().uppercase()
                require(isin.matches(Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]"))) {
                    "Invalid holding ISIN"
                }
                val holdingCurrency = row.getString("valuation_currency").trim().uppercase()
                require(holdingCurrency == currency) {
                    "Holding valuation currency does not match portfolio currency"
                }
                val holdingValue = row.requireFiniteMoney("valuation")
                require(holdingValue >= 0.0) { "Holding valuation cannot be negative" }
                val quantity = row.optFiniteMoney("quantity")?.takeIf { it >= 0.0 }
                val quotePrice = row.optFiniteMoney("quote_mid_price")?.takeIf { it >= 0.0 }

                add(
                    ScalableHoldingSnapshot(
                        isin = isin,
                        name = row.optString("name").trim().ifBlank { isin },
                        securityType = row.optString("security_type").trim(),
                        quantity = quantity,
                        valuation = holdingValue,
                        valuationCurrency = holdingCurrency,
                        quoteMidPrice = quotePrice,
                        quoteCurrency = row.optString("quote_currency")
                            .trim()
                            .uppercase()
                            .takeIf(String::isNotBlank),
                        quoteTimestampUtc = row.optString("quote_timestamp_utc")
                            .trim()
                            .takeIf(String::isNotBlank),
                        quoteOutdated = row.optBoolean("quote_is_outdated", false),
                    )
                )
            }
        }

        require(holdings.map { it.isin }.distinct().size == holdings.size) {
            "Scalable snapshot contains duplicate holdings"
        }

        return ScalableSnapshot(
            createdAtUtc = createdAt,
            valuationTimestampUtc = root.optString("valuation_timestamp_utc")
                .trim()
                .takeIf(String::isNotBlank),
            cliVersion = root.optString("cli_version").trim().takeIf(String::isNotBlank),
            currency = currency,
            totalValue = total,
            securitiesValue = securities,
            cryptoValue = crypto,
            performance = performance,
            holdings = holdings.sortedBy { it.isin },
        )
    }

    /** Persist only the allowlisted sanitized fields. */
    fun encode(snapshot: ScalableSnapshot): String {
        val performance = JSONArray().apply {
            snapshot.performance.forEach { row ->
                put(
                    JSONObject()
                        .put("timeframe", row.timeframe)
                        .put("simple_absolute_return", row.absoluteReturn)
                )
            }
        }
        val holdings = JSONArray().apply {
            snapshot.holdings.forEach { row ->
                put(
                    JSONObject()
                        .put("isin", row.isin)
                        .put("name", row.name)
                        .put("security_type", row.securityType)
                        .put("quantity", row.quantity ?: JSONObject.NULL)
                        .put("valuation", row.valuation)
                        .put("valuation_currency", row.valuationCurrency)
                        .put("quote_mid_price", row.quoteMidPrice ?: JSONObject.NULL)
                        .put("quote_currency", row.quoteCurrency ?: JSONObject.NULL)
                        .put("quote_timestamp_utc", row.quoteTimestampUtc ?: JSONObject.NULL)
                        .put("quote_is_outdated", row.quoteOutdated)
                )
            }
        }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("source", "scalable-cli")
            .put("created_at_utc", snapshot.createdAtUtc.toString())
            .put("valuation_timestamp_utc", snapshot.valuationTimestampUtc ?: JSONObject.NULL)
            .put("cli_version", snapshot.cliVersion ?: JSONObject.NULL)
            .put("currency", snapshot.currency)
            .put(
                "valuation",
                JSONObject()
                    .put("total", snapshot.totalValue)
                    .put("securities", snapshot.securitiesValue)
                    .put("crypto", snapshot.cryptoValue)
            )
            .put("performance", performance)
            .put("holdings", holdings)
            .toString()
    }

    private fun JSONObject.requireFiniteMoney(key: String): Double =
        optFiniteMoney(key) ?: error("Missing or invalid Scalable value: $key")

    private fun JSONObject.optFiniteMoney(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = when (val raw = get(key)) {
            is Number -> raw.toDouble()
            is String -> raw.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return value?.takeIf(Double::isFinite)
    }
}
