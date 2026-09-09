package com.pix.folio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pix.folio.data.FolioStore
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.InvestmentTag
import com.pix.folio.model.PaymentCategory
import com.pix.folio.widget.FolioBalanceWidget
import kotlinx.coroutines.launch
import java.time.LocalDate

class FolioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FolioStore(application)

    var summary by mutableStateOf(store.summary())
        private set

    var appLockEnabled by mutableStateOf(store.isAppLockEnabled())
        private set

    var canUndo by mutableStateOf(store.canUndo())
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

    fun addIncome(name: String, amount: Double, dayOfMonth: Int) {
        store.addIncome(name, amount, dayOfMonth)
        refresh()
    }

    fun toggleIncomeReceived(id: String) {
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
    ) {
        store.addInvestment(kind, name, symbol, amount, isin, figi, exchange, units, unitPrice)
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

    fun setEmergencyFundBalance(value: Double) {
        store.setEmergencyFundBalance(value)
        refresh()
    }

    fun updateInvestmentDetails(id: String, tags: Set<InvestmentTag>, note: String) {
        store.updateInvestmentDetails(id, tags, note)
        refresh()
    }

    fun addInvestmentPrice(
        holdingId: String,
        close: Double,
        currency: String = "EUR",
        symbol: String = "",
        source: String = "Manual",
        date: LocalDate = LocalDate.now(),
    ) {
        store.addInvestmentPrice(holdingId, close, currency, symbol, source, date)
        refresh()
    }

    fun removeInvestment(id: String) {
        store.removeInvestment(id)
        refresh()
    }

    fun setCashBalance(value: Double) {
        store.setCashBalance(value)
        refresh()
    }

    fun updateAppLockEnabled(enabled: Boolean) {
        store.setAppLockEnabled(enabled)
        appLockEnabled = enabled
    }

    fun undoLastChange(): Boolean {
        val restored = store.undoLastChange()
        if (restored) refresh()
        return restored
    }

    fun clearAll() {
        store.clearAll()
        refresh()
    }

    private fun refresh() {
        summary = store.summary()
        appLockEnabled = store.isAppLockEnabled()
        canUndo = store.canUndo()
        viewModelScope.launch {
            FolioBalanceWidget().updateAll(getApplication())
        }
    }
}
