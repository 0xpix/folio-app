package com.pix.folio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pix.folio.data.FolioStore
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.data.MarketPriceService
import com.pix.folio.data.RecurringMoneyProcessor
import com.pix.folio.model.AppFontChoice
import com.pix.folio.model.Cs2AssetType
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.InvestmentTag
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.SavingsBucketType
import com.pix.folio.widget.FolioBalanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class V07ViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FolioStore(application)
    private val trackingStore = InvestmentTrackingStore(application)

    var summary by mutableStateOf(store.summary())
        private set

    var fontChoice by mutableStateOf(store.fontChoice())
        private set

    var appLockEnabled by mutableStateOf(store.isAppLockEnabled())
        private set

    var autoRecurringEnabled by mutableStateOf(store.autoRecurringEnabled())
        private set

    var canUndo by mutableStateOf(store.canUndo())
        private set

    var marketRefreshLabel by mutableStateOf<String?>(null)
        private set

    var marketRefreshing by mutableStateOf(false)
        private set

    var trackedHistories by mutableStateOf<Map<String, MarketPriceService.MarketHistory>>(emptyMap())
        private set

    var trackingErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var trackingRefreshing by mutableStateOf(false)
        private set

    init {
        refreshTrackedInvestments()
    }

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        store.addExpense(category, amount, note)
        refresh()
    }

    fun setBudget(category: ExpenseCategory, amount: Double) {
        store.setBudget(category, amount)
        refresh()
    }

    fun addPayment(category: PaymentCategory, name: String, amount: Double, dayOfMonth: Int) {
        store.addPayment(category, name, amount, dayOfMonth)
        refresh()
    }

    fun togglePayment(id: String) {
        store.togglePayment(id)
        refresh()
    }

    fun addIncome(name: String, amount: Double, dayOfMonth: Int, useForNextMonth: Boolean) {
        store.addIncome(name, amount, dayOfMonth, if (useForNextMonth) 1 else 0)
        refresh()
    }

    fun toggleIncome(id: String) {
        store.toggleIncomeReceived(id)
        refresh()
    }

    fun addInvestment(
        kind: InvestmentKind,
        name: String,
        symbol: String,
        amount: Double,
        isin: String = "",
        figi: String = "",
        exchange: String = "",
        units: Double = 0.0,
        unitPrice: Double = 0.0,
        marketHashName: String = "",
        cs2AssetType: Cs2AssetType = Cs2AssetType.OTHER,
        purchaseDate: LocalDate = LocalDate.now(),
    ) {
        store.addInvestment(
            kind, name, symbol, amount, isin, figi, exchange,
            units, unitPrice, marketHashName, cs2AssetType
        )
        refresh()
        val normalizedIsin = isin.trim().uppercase()
        val normalizedSymbol = symbol.trim().uppercase()
        val holding = summary.investments.firstOrNull {
            (normalizedIsin.isNotBlank() && it.isin.equals(normalizedIsin, true)) ||
                (normalizedSymbol.isNotBlank() && it.symbol.equals(normalizedSymbol, true)) ||
                it.name.equals(name.trim(), true)
        }
        if (holding != null) {
            trackingStore.setPurchaseDate(holding.id, purchaseDate.coerceAtMost(LocalDate.now()))
            refreshTrackedInvestment(holding.id)
        }
    }

    fun addInvestmentContribution(
        holdingId: String,
        amount: Double,
        units: Double = 0.0,
        unitPrice: Double = 0.0,
    ) {
        store.addInvestmentContribution(holdingId, amount, units, unitPrice)
        refresh()
        refreshTrackedInvestment(holdingId)
    }

    fun addRecurringInvestment(holdingId: String, amount: Double, dayOfMonth: Int) {
        store.addRecurringInvestment(holdingId, amount, dayOfMonth)
        refresh()
    }

    fun toggleRecurringInvestment(id: String) {
        store.toggleRecurringInvestment(id)
        refresh()
    }

    fun transferSavings(bucket: SavingsBucketType, amount: Double, note: String = "") {
        store.transferSavings(bucket, amount, note)
        refresh()
    }

    fun setExistingEmergencyFundBalance(amount: Double) {
        store.setEmergencyFundBalance(amount.coerceAtLeast(0.0))
        refresh()
    }

    fun setSavingsTarget(bucket: SavingsBucketType, amount: Double) {
        store.setSavingsTarget(bucket, amount)
        refresh()
    }

    fun setCashBalance(amount: Double) {
        store.setCashBalance(amount)
        refresh()
    }

    fun updateInvestmentDetails(id: String, tags: Set<InvestmentTag>, note: String) {
        store.updateInvestmentDetails(id, tags, note)
        refresh()
    }

    fun updateInvestmentTracking(
        id: String,
        priceSymbol: String,
        marketHashName: String,
        cs2AssetType: Cs2AssetType?,
    ) {
        store.updateInvestmentTracking(id, priceSymbol, marketHashName, cs2AssetType)
        refresh()
        refreshTrackedInvestment(id)
    }

    fun setInvestmentPurchaseDate(id: String, date: LocalDate) {
        trackingStore.setPurchaseDate(id, date.coerceAtMost(LocalDate.now()))
        refreshTrackedInvestment(id)
    }

    fun purchaseDateFor(id: String): LocalDate? = trackingStore.purchaseDate(id)

    fun addInvestmentPrice(
        holdingId: String,
        price: Double,
        currency: String = "EUR",
        symbol: String = "",
        date: LocalDate = LocalDate.now(),
    ) {
        store.addInvestmentPrice(holdingId, price, currency, symbol, "Manual", date)
        refresh()
    }

    fun trackedMarketValue(holding: InvestmentHolding): Double {
        val history = trackedHistories[holding.id]
        val first = history?.firstPrice
        val latest = history?.latestPrice
        return if (first != null && first > 0.0 && latest != null && latest > 0.0) {
            holding.amount * (latest / first)
        } else {
            summary.marketValueFor(holding)
        }
    }

    fun trackedGain(holding: InvestmentHolding): Double = trackedMarketValue(holding) - holding.amount

    fun trackedGainPct(holding: InvestmentHolding): Double =
        if (holding.amount > 0.0) trackedGain(holding) / holding.amount * 100.0 else 0.0

    fun trackedValueHistory(holding: InvestmentHolding): List<Pair<LocalDate, Double>> {
        val history = trackedHistories[holding.id] ?: return emptyList()
        val first = history.firstPrice?.takeIf { it > 0.0 } ?: return emptyList()
        return history.points.map { it.date to (holding.amount * it.close / first) }
    }

    /**
     * Combined portfolio curve. Every purchase starts contributing on its own purchase date.
     * Tracked securities follow their market history; holdings without history stay at cost basis
     * rather than disappearing from the total. The last point therefore matches the portfolio total.
     */
    val trackedPortfolioHistory: List<Pair<LocalDate, Double>>
        get() {
            val holdings = summary.investments
            if (holdings.isEmpty()) return emptyList()

            val dates = sortedSetOf<LocalDate>()
            holdings.forEach { holding ->
                purchaseDateFor(holding.id)?.let(dates::add)
                trackedHistories[holding.id]?.points?.forEach { dates += it.date }
            }
            dates += LocalDate.now()
            if (dates.size < 2) return emptyList()

            return dates.mapNotNull { date ->
                var hasStartedHolding = false
                val total = holdings.sumOf { holding ->
                    val purchaseDate = purchaseDateFor(holding.id)
                    if (purchaseDate != null && date.isBefore(purchaseDate)) {
                        0.0
                    } else {
                        hasStartedHolding = true
                        val history = trackedHistories[holding.id]
                        val first = history?.firstPrice?.takeIf { it > 0.0 }
                        val point = history?.points?.lastOrNull { !it.date.isAfter(date) }
                        if (first != null && point != null && point.close > 0.0) {
                            holding.amount * point.close / first
                        } else {
                            holding.amount
                        }
                    }
                }
                if (hasStartedHolding) date to total else null
            }
        }

    val trackedPortfolioTotal: Double
        get() = summary.investments.sumOf(::trackedMarketValue)

    val trackedPortfolioGain: Double
        get() = trackedPortfolioTotal - summary.portfolioCostBasis

    val trackedPortfolioGainPct: Double
        get() = if (summary.portfolioCostBasis > 0.0) trackedPortfolioGain / summary.portfolioCostBasis * 100.0 else 0.0

    val trackedNetWorth: Double
        get() = summary.cashBalance + summary.totalSavings + trackedPortfolioTotal

    fun trackingSourceFor(id: String): String? = trackedHistories[id]?.source

    fun refreshTrackedInvestments() {
        if (trackingRefreshing) return
        viewModelScope.launch {
            trackingRefreshing = true
            val holdings = summary.investments
            val next = trackedHistories.toMutableMap()
            val errors = trackingErrors.toMutableMap()
            holdings.forEach { holding ->
                val date = trackingStore.purchaseDate(holding.id) ?: return@forEach
                if (holding.kind == InvestmentKind.CS2) return@forEach
                runCatching { MarketPriceService.fetchHistory(holding, date) }
                    .onSuccess {
                        next[holding.id] = it
                        errors.remove(holding.id)
                    }
                    .onFailure {
                        errors[holding.id] = it.message ?: "history unavailable"
                    }
            }
            trackedHistories = next.filterKeys { id -> holdings.any { it.id == id } }
            trackingErrors = errors.filterKeys { id -> holdings.any { it.id == id } }
            trackingRefreshing = false
        }
    }

    fun refreshTrackedInvestment(id: String) {
        val holding = summary.investments.firstOrNull { it.id == id } ?: return
        val date = trackingStore.purchaseDate(id) ?: return
        if (holding.kind == InvestmentKind.CS2) return
        viewModelScope.launch {
            runCatching { MarketPriceService.fetchHistory(holding, date) }
                .onSuccess {
                    trackedHistories = trackedHistories + (id to it)
                    trackingErrors = trackingErrors - id
                }
                .onFailure {
                    trackingErrors = trackingErrors + (id to (it.message ?: "history unavailable"))
                }
        }
    }

    fun removeInvestment(id: String) {
        store.removeInvestment(id)
        trackingStore.clearPurchaseDate(id)
        trackedHistories = trackedHistories - id
        trackingErrors = trackingErrors - id
        refresh()
    }

    fun updateFontChoice(choice: AppFontChoice) {
        store.setFontChoice(choice)
        fontChoice = choice
    }

    fun updateAppLockEnabled(enabled: Boolean) {
        store.setAppLockEnabled(enabled)
        appLockEnabled = enabled
    }

    fun updateAutoRecurringEnabled(enabled: Boolean) {
        store.setAutoRecurringEnabled(enabled)
        autoRecurringEnabled = enabled
        if (enabled) runRecurringNow()
    }

    fun runRecurringNow() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                RecurringMoneyProcessor.process(getApplication())
            }
            marketRefreshLabel = if (result.totalApplied == 0) {
                "Recurring plan is current"
            } else {
                "Applied ${result.totalApplied} recurring item${if (result.totalApplied == 1) "" else "s"}"
            }
            refresh()
        }
    }

    fun refreshMarketPrices() {
        if (marketRefreshing) return
        viewModelScope.launch {
            marketRefreshing = true
            val report = MarketPriceService.refreshAll(getApplication())
            marketRefreshLabel = when {
                report.updated > 0 && report.failed == 0 -> "Updated ${report.updated} market price${if (report.updated == 1) "" else "s"}"
                report.updated > 0 -> "Updated ${report.updated} · ${report.failed} unavailable"
                report.failed > 0 -> "No prices updated · ${report.failed} unavailable"
                else -> "Add a ticker or Steam market name to enable price tracking"
            }
            marketRefreshing = false
            refresh()
            refreshTrackedInvestments()
        }
    }

    fun undoLastChange() {
        if (store.undoLastChange()) {
            refresh()
            refreshTrackedInvestments()
        }
    }

    fun clearAll() {
        summary.investments.forEach { trackingStore.clearPurchaseDate(it.id) }
        trackedHistories = emptyMap()
        trackingErrors = emptyMap()
        store.clearAll()
        refresh()
    }

    fun refresh() {
        summary = store.summary()
        fontChoice = store.fontChoice()
        appLockEnabled = store.isAppLockEnabled()
        autoRecurringEnabled = store.autoRecurringEnabled()
        canUndo = store.canUndo()
        viewModelScope.launch {
            FolioBalanceWidget().updateAll(getApplication())
        }
    }
}
