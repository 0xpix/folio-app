package com.pix.folio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pix.folio.data.FolioStore
import com.pix.folio.data.MarketPriceService
import com.pix.folio.data.RecurringMoneyProcessor
import com.pix.folio.model.AppFontChoice
import com.pix.folio.model.Cs2AssetType
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
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
    ) {
        store.addInvestment(
            kind, name, symbol, amount, isin, figi, exchange,
            units, unitPrice, marketHashName, cs2AssetType
        )
        refresh()
    }

    fun addInvestmentContribution(
        holdingId: String,
        amount: Double,
        units: Double = 0.0,
        unitPrice: Double = 0.0,
    ) {
        store.addInvestmentContribution(holdingId, amount, units, unitPrice)
        refresh()
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
    }

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

    fun removeInvestment(id: String) {
        store.removeInvestment(id)
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
                else -> "Add a ticker or Steam market name to enable live prices"
            }
            marketRefreshing = false
            refresh()
        }
    }

    fun undoLastChange() {
        if (store.undoLastChange()) refresh()
    }

    fun clearAll() {
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
