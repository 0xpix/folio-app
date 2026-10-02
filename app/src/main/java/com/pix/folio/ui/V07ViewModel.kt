package com.pix.folio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pix.folio.data.BudgetEnvelope
import com.pix.folio.data.FinanceEditor
import com.pix.folio.data.FolioStore
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.data.MarketPriceService
import com.pix.folio.data.MonthlyPlanStore
import com.pix.folio.data.RecurringMoneyProcessor
import com.pix.folio.data.RecurringSavingsRule
import com.pix.folio.model.AppFontChoice
import com.pix.folio.model.Cs2AssetType
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.InvestmentTag
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.SavingsBucketType
import com.pix.folio.widget.FolioWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class V07ViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FolioStore(application)
    private val trackingStore = InvestmentTrackingStore(application)
    private val planStore = MonthlyPlanStore(application)
    private val editor = FinanceEditor(application)

    var summary by mutableStateOf(store.summary())
        private set

    var recurringSavingsRules by mutableStateOf(planStore.recurringSavings())
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

    var recurringStatusLabel by mutableStateOf<String?>(null)
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

    fun budgetEnvelope(month: YearMonth): BudgetEnvelope = planStore.envelope(summary, month)

    fun suggestedBudgetMonth(): YearMonth = planStore.suggestedBudgetMonth(summary)

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        store.addExpense(category, amount, note)
        refresh()
    }

    fun updateExpense(id: String, category: ExpenseCategory, amount: Double, date: LocalDate, note: String) {
        editor.updateExpense(id, category, amount, date, note)
        refresh()
    }

    fun removeExpense(id: String) {
        editor.removeExpense(id)
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

    fun updatePayment(id: String, category: PaymentCategory, name: String, amount: Double, dayOfMonth: Int) {
        editor.updatePayment(id, category, name, amount, dayOfMonth)
        refresh()
    }

    fun removePayment(id: String) {
        editor.removePayment(id)
        refresh()
    }

    fun togglePayment(id: String) {
        val row = summary.payments.firstOrNull { it.id == id } ?: return
        val month = YearMonth.now()
        if (row.lastPaidMonth != month && summary.cashBalance + 0.005 < row.amount) {
            recurringStatusLabel = "Not enough available cash for ${row.name}"
            return
        }
        store.togglePayment(id)
        refresh()
    }

    fun addIncome(name: String, amount: Double, dayOfMonth: Int, useForNextMonth: Boolean) {
        store.addIncome(name, amount, dayOfMonth, if (useForNextMonth) 1 else 0)
        refresh()
    }

    fun updateIncome(id: String, name: String, amount: Double, dayOfMonth: Int, useForNextMonth: Boolean) {
        editor.updateIncome(id, name, amount, dayOfMonth, if (useForNextMonth) 1 else 0)
        refresh()
    }

    fun removeIncome(id: String) {
        editor.removeIncome(id)
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
        purchaseTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    ) {
        store.addInvestment(
            kind = kind,
            name = name,
            symbol = symbol,
            amount = amount,
            isin = isin,
            figi = figi,
            exchange = exchange,
            units = units,
            unitPrice = unitPrice,
            marketHashName = marketHashName,
            cs2AssetType = cs2AssetType,
            purchasedAt = LocalDateTime.of(purchaseDate, purchaseTime),
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
            summary.transactionsFor(holding.id)
                .minByOrNull { it.purchasedAt }
                ?.let { trackingStore.setPurchaseDateTime(holding.id, it.purchasedAt) }
                ?: trackingStore.setPurchaseDate(holding.id, purchaseDate.coerceAtMost(LocalDate.now()))
            refreshTrackedInvestment(holding.id)
        }
    }

    fun addInvestmentContribution(
        holdingId: String,
        amount: Double,
        units: Double = 0.0,
        unitPrice: Double = 0.0,
        purchasedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        if (amount <= 0.0) return
        if (summary.cashBalance + 0.005 < amount) {
            return
        }

        val previousUnits = trackingStore.ownedUnits(holdingId)
            ?: summary.unitsFor(holdingId).takeIf { it > 0.0 }
            ?: 0.0
        val resolvedUnitPrice = when {
            unitPrice > 0.0 -> unitPrice
            units > 0.0 -> amount / units
            else -> 0.0
        }

        store.addInvestmentContribution(
            holdingId = holdingId,
            amount = amount,
            units = units,
            unitPrice = resolvedUnitPrice,
            purchasedAt = purchasedAt,
        )
        if (units > 0.0) {
            trackingStore.setOwnedUnits(holdingId, previousUnits + units)
        }
        refresh()
        refreshTrackedInvestment(holdingId)
    }

    fun updateInvestmentTransaction(
        id: String,
        amount: Double,
        units: Double,
        purchasedAt: LocalDateTime,
    ) {
        store.updateInvestmentTransaction(id, amount, units, purchasedAt)
        refresh()
        summary.investmentTransactions.firstOrNull { it.id == id }?.holdingId?.let(::refreshTrackedInvestment)
    }

    fun addRecurringInvestment(holdingId: String, amount: Double, dayOfMonth: Int) {
        store.addRecurringInvestment(holdingId, amount, dayOfMonth)
        refresh()
    }

    fun updateRecurringInvestment(id: String, amount: Double, dayOfMonth: Int) {
        editor.updateRecurringInvestment(id, amount, dayOfMonth)
        refresh()
    }

    fun removeRecurringInvestment(id: String) {
        editor.removeRecurringInvestment(id)
        refresh()
    }

    fun toggleRecurringInvestment(id: String) {
        val row = summary.recurringInvestments.firstOrNull { it.id == id } ?: return
        val month = YearMonth.now()
        if (row.lastAppliedMonth != month && summary.cashBalance + 0.005 < row.amount) {
            recurringStatusLabel = "Not enough available cash for this investment"
            return
        }
        store.toggleRecurringInvestment(id)
        refresh()
    }

    fun addRecurringSaving(bucket: SavingsBucketType, amount: Double, dayOfMonth: Int) {
        planStore.addRecurringSaving(bucket, amount, dayOfMonth)
        refresh()
    }

    fun updateRecurringSaving(id: String, bucket: SavingsBucketType, amount: Double, dayOfMonth: Int) {
        planStore.updateRecurringSaving(id, bucket, amount, dayOfMonth)
        refresh()
    }

    fun removeRecurringSaving(id: String) {
        planStore.removeRecurringSaving(id)
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
        val safeDate = date.coerceAtMost(LocalDate.now())
        val firstPurchase = summary.transactionsFor(id).minByOrNull { it.purchasedAt }
        if (firstPurchase != null) {
            store.updateInvestmentTransaction(
                id = firstPurchase.id,
                amount = firstPurchase.amount,
                units = firstPurchase.units,
                purchasedAt = LocalDateTime.of(safeDate, firstPurchase.time),
            )
            refresh()
        } else {
            trackingStore.setPurchaseDate(id, safeDate)
        }
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
     * Combined portfolio curve built from individual purchase lots.
     *
     * A later contribution must not appear before its own transaction date. When market history is
     * available, each lot follows the relative price move from its purchase date; otherwise the lot
     * stays at cost basis. Today's endpoint is normalized by V081Valuation to the current portfolio
     * value, including exact broker-owned units when available.
     */
    val trackedPortfolioHistory: List<Pair<LocalDate, Double>>
        get() {
            val holdings = summary.investments
            if (holdings.isEmpty()) return emptyList()

            val dates = sortedSetOf<LocalDate>()
            summary.investmentTransactions.forEach { dates += it.date }
            holdings.forEach { holding ->
                purchaseDateFor(holding.id)?.let(dates::add)
                trackedHistories[holding.id]?.points?.forEach { dates += it.date }
            }
            dates += LocalDate.now()
            if (dates.size < 2) return emptyList()

            return dates.mapNotNull { date ->
                var hasStartedHolding = false
                val total = holdings.sumOf { holding ->
                    val transactions = summary.transactionsFor(holding.id)
                        .filter { !it.date.isAfter(date) }

                    if (transactions.isEmpty()) {
                        val legacyPurchaseDate = purchaseDateFor(holding.id)
                        if (legacyPurchaseDate == null || date.isBefore(legacyPurchaseDate)) {
                            0.0
                        } else {
                            hasStartedHolding = true
                            holding.amount
                        }
                    } else {
                        hasStartedHolding = true
                        val history = trackedHistories[holding.id]
                        transactions.sumOf { transaction ->
                            val point = history?.points?.lastOrNull {
                                !it.date.isAfter(date) && it.close > 0.0
                            }
                            val purchasePoint = history?.points?.firstOrNull {
                                !it.date.isBefore(transaction.date) && it.close > 0.0
                            } ?: history?.points?.lastOrNull {
                                !it.date.isAfter(transaction.date) && it.close > 0.0
                            }

                            v081PurchaseLotValueAt(
                                amount = transaction.amount,
                                purchaseDate = transaction.date,
                                date = date,
                                purchaseClose = purchasePoint?.close,
                                close = point?.close,
                            )
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
                RecurringMoneyProcessor.process(getApplication(), requireAutomationEnabled = false)
            }
            recurringStatusLabel = if (result.totalApplied == 0) {
                "Nothing due right now, or an item is waiting for cash"
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
        planStore.clearAll()
        store.clearAll()
        refresh()
    }

    fun refresh() {
        summary = store.summary()
        recurringSavingsRules = planStore.recurringSavings()
        fontChoice = store.fontChoice()
        appLockEnabled = store.isAppLockEnabled()
        autoRecurringEnabled = store.autoRecurringEnabled()
        canUndo = store.canUndo()
        FolioWidgetUpdater.request(getApplication())
    }
}
