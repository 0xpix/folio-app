package com.pix.folio.ui

import android.app.Application
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.model.InvestmentHolding
import java.time.LocalDate

internal data class V081HoldingValuation(
    val value: Double,
    val units: Double?,
    val currency: String?,
    val exact: Boolean,
) {
    val status: String
        get() = if (exact) "Exact from owned units" else "Estimated"
}

internal data class V081ValuationDecision(
    val value: Double,
    val exact: Boolean,
)

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
        (historyPoint == null || !storedPoint.date.isBefore(historyPoint.date))
    val latest = if (useStoredPoint) storedPoint?.close else historyPoint?.close
    val currency = if (useStoredPoint) {
        storedPoint?.currency?.ifBlank { holding.priceCurrency }
    } else {
        history?.currency?.ifBlank { holding.priceCurrency }
    }?.trim()?.uppercase()?.takeIf(String::isNotBlank)

    val tracking = InvestmentTrackingStore(getApplication<Application>())
    val brokerUnits = tracking.ownedUnits(holding.id)
    val transactions = summary.transactionsFor(holding.id)
    val transactionUnits = transactions.sumOf { it.units.coerceAtLeast(0.0) }.takeIf { it > 0.0 }
    val completeTransactionUnits = transactions.isNotEmpty() && transactions.all { it.units > 0.0 }
    val units = brokerUnits ?: transactionUnits
    val fallbackValue = trackedMarketValue(holding)
    val decision = v081CurrentValue(
        fallbackValue = fallbackValue,
        units = units,
        latestPrice = latest,
        quoteCurrency = currency,
        unitsAreComplete = brokerUnits != null || completeTransactionUnits,
    )

    return V081HoldingValuation(
        value = decision.value,
        units = units,
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
