package com.pix.folio.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.pix.folio.data.FolioStore
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.PaymentCategory
import com.pix.folio.widget.FolioBalanceWidget
import kotlinx.coroutines.launch
import java.time.LocalDate

class FolioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FolioStore(application)

    var summary by mutableStateOf(store.summary())
        private set

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        store.addExpense(category, amount, note)
        refresh()
    }

    fun addPayment(category: PaymentCategory, name: String, amount: Double, dueDate: LocalDate) {
        store.addPayment(category, name, amount, dueDate)
        refresh()
    }

    fun togglePayment(id: String) {
        store.togglePayment(id)
        refresh()
    }

    fun resetDemo() {
        store.resetDemo()
        refresh()
    }

    private fun refresh() {
        summary = store.summary()
        viewModelScope.launch {
            FolioBalanceWidget().updateAll(getApplication())
        }
    }
}
