package com.pix.folio.ui

import android.app.Application
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentTransaction
import java.time.LocalDate
import java.time.YearMonth

internal data class V081HoldingValuation(
    val value: Double,
    val units: Double?,
    val currency: String?,
    val exact: Boolean,
) {
    val status: String
        get() = if (exact) "Exact from broker units" else "Estimated from purchase lots"
}

internal data class V081ValuationDecision(
    val value: Double,
    val exact: Boolean,
)

internal fun v081CumulativeMonthlyContributions(
    transactions: List<InvestmentTransaction>,
): List<Pair<LocalDate, Double>> {
    var cumulative = 0.0
    return transactions
        .groupBy { YearMonth.from(it.date) }
        .toSortedMap()
        .map { (month, rows) ->
            cumulative += rows.sumOf { it.amount }
            val pointDate = rows.maxOfOrNull { it.date } ?: month.atEndOfMonth()
            pointDate to cumulative
        }
}

internal fun v081PurchaseLotValueAt(
    amount: Double,
    units: Double,
    purchaseDate: LocalDate,
    date: LocalDate,
    purchaseClose: Double?,
    close: Double?,
): Double {
    if (date.isBefore(purchaseDate)) return 0.0
    if (units > 0.0 && close != null && close > 0.0) return units * close
    if (amount <= 0.0) return 0.0
    return if (
        purchaseClose != null && purchaseClose > 0.0 &&
        close != null && close > 0.0
    ) {
        amount * close / purchaseClose
    } else {
        amount
    }
}

internal fun v081ResolvedOwnedUnits(
    brokerUnits: Double?,
    transactions: List<InvestmentTransaction>,
): Double? {
    val transactionUnits = transactions.sumOf { it.units.coerceAtLeast(0.0) }.takeIf { it > 0.0 }
    return brokerUnits ?: transactionUnits
}


/** Pure valuation rule kept separate so CI can protect the broker-matching path. */
internal fun v081CurrentValue(
    fallbackValue: Double,
    units: Double?,
    latestPrice: Double?,
    quoteCurrency: String?,
    unitsAreComplete: Boolean,
): V081ValuationDecision {
    val normalizedCurrency = quoteCurrency?.trim()?.uppercase()
    val canUseExactUnits =
        latestPrice != null && latestPrice > 0.0 &&
            units != null && units > 0.0 &&
            normalizedCurrency == "EUR" &&
            unitsAreComplete

    return if (canUseExactUnits) {
        V081ValuationDecision(units * latestPrice, true)
    } else {
        V081ValuationDecision(fallbackValue, false)
    }
}

/**
 * Prefer broker-reported current units and the freshest EUR quote Folio has. When either side is
 * missing, keep the existing ratio-based estimate rather than presenting cross-currency units as
 * euros.
 */
internal fun V07ViewModel.v081Valuation(holding: InvestmentHolding): V081HoldingValuation {
    val history = trackedHistories[holding.id]
    val historyPoint = history?.points?.lastOrNull()
    val storedPoint = summary.priceHistoryFor(holding.id).lastOrNull()
    val useStoredPoint = storedPoint != null &&
        (historyPoint == null || storedPoint.date.isAfter(historyPoint.date))
    val latest = if (useStoredPoint) storedPoint?.close else historyPoint?.close
    val currency = if (useStoredPoint) {
        storedPoint?.currency?.ifBlank { holding.priceCurrency }
    } else {
        history?.currency?.ifBlank { holding.priceCurrency }
    }?.trim()?.uppercase()?.takeIf(String::isNotBlank)

    val tracking = InvestmentTrackingStore(getApplication<Application>())
    val brokerUnits = tracking.brokerOwnedUnits(holding.id)
    val transactions = summary.transactionsFor(holding.id)
    val units = v081ResolvedOwnedUnits(brokerUnits, transactions)

    val latestDate = historyPoint?.date ?: storedPoint?.date ?: LocalDate.now()
    val useUnitsForLots = currency == "EUR"
    val lotBasedFallback = if (transactions.isNotEmpty() && latest != null && latest > 0.0) {
        transactions.sumOf { transaction ->
            val purchasePoint = history?.points?.firstOrNull {
                !it.date.isBefore(transaction.date) && it.close > 0.0
            } ?: history?.points?.lastOrNull {
                !it.date.isAfter(transaction.date) && it.close > 0.0
            }
            v081PurchaseLotValueAt(
                amount = transaction.amount,
                units = if (useUnitsForLots) transaction.units else 0.0,
                purchaseDate = transaction.date,
                date = latestDate,
                purchaseClose = purchasePoint?.close,
                close = latest,
            )
        }
    } else {
        null
    }
    val fallbackValue = lotBasedFallback ?: trackedMarketValue(holding)
    val decision = v081CurrentValue(
        fallbackValue = fallbackValue,
        units = units,
        latestPrice = latest,
        quoteCurrency = currency,
        unitsAreComplete = brokerUnits != null && brokerUnits > 0.0,
    )

    return V081HoldingValuation(
        value = decision.value,
        units = brokerUnits,
        currency = currency,
        exact = decision.exact,
    )
}

internal val V07ViewModel.v081PortfolioTotal: Double
    get() = summary.investments.sumOf { v081Valuation(it).value }

internal val V07ViewModel.v081PortfolioGain: Double
    get() = v081PortfolioTotal - summary.portfolioCostBasis

internal val V07ViewModel.v081PortfolioGainPct: Double
    get() = if (summary.portfolioCostBasis > 0.0) {
        v081PortfolioGain / summary.portfolioCostBasis * 100.0
    } else {
        0.0
    }

internal val V07ViewModel.v081NetWorth: Double
    get() = summary.cashBalance + summary.totalSavings + v081PortfolioTotal

/** Keep the reconstructed historical curve, but make today's endpoint match the current total. */
internal val V07ViewModel.v081PortfolioHistory: List<Pair<LocalDate, Double>>
    get() {
        val current = v081PortfolioTotal
        val today = LocalDate.now()
        val base = trackedPortfolioHistory.toMutableList()
        if (base.isEmpty()) return if (summary.investments.isEmpty()) emptyList() else listOf(today to current)
        if (base.last().first == today) {
            base[base.lastIndex] = today to current
        } else {
            base += today to current
        }
        return base
    }
