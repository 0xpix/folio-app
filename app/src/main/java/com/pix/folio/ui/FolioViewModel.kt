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
import com.pix.folio.model.PaymentCategory
import com.pix.folio.widget.FolioBalanceWidget
import kotlinx.coroutines.launch

class FolioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FolioStore(application)

    var summary by mutableStateOf(store.summary())
        private set

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        store.addExpense(category, amount, note)
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

    fun addInvestment(kind: InvestmentKind, name: String, symbol: String, amount: Double) {
        store.addInvestment(kind, name, symbol, amount)
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

    fun clearAll() {
        store.clearAll()
        refresh()
    }

    private fun refresh() {
        summary = store.summary()
        viewModelScope.launch {
            FolioBalanceWidget().updateAll(getApplication())
        }
    }
}
