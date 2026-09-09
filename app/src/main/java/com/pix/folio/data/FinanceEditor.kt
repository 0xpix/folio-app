package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.PaymentCategory
import com.pix.folio.widget.FolioWidgetUpdater
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Editing companion for FolioStore's persisted rows.
 *
 * Past posted transactions are preserved when a recurring rule is deleted. Editing a rule that is
 * already posted in the current month updates that current posting and reconciles cash; older months
 * stay historically accurate.
 */
class FinanceEditor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("folio_store_v2", Context.MODE_PRIVATE)

    fun updateExpense(id: String, category: ExpenseCategory, amount: Double, date: LocalDate, note: String) {
        if (amount <= 0.0) return
        val rows = array("expenses")
        val index = indexOf(rows, id) ?: return
        val old = rows.getJSONObject(index)
        val oldAmount = old.optDouble("amount", 0.0)
        captureUndo()
        old.put("category", category.name)
            .put("amount", amount)
            .put("date", date.toString())
            .put("note", note.trim())
        write("expenses", rows)
        setCash((cash() + oldAmount - amount).coerceAtLeast(0.0))
        recordBalanceSnapshot()
        FolioWidgetUpdater.request(appContext)
    }

    fun removeExpense(id: String) {
        val rows = array("expenses")
        val index = indexOf(rows, id) ?: return
        val oldAmount = rows.getJSONObject(index).optDouble("amount", 0.0)
        captureUndo()
        rows.remove(index)
        write("expenses", rows)
        setCash(cash() + oldAmount)
        recordBalanceSnapshot()
        FolioWidgetUpdater.request(appContext)
    }

    fun updatePayment(id: String, category: PaymentCategory, name: String, amount: Double, dayOfMonth: Int) {
        if (name.isBlank() || amount <= 0.0) return
        val rows = array("payments")
        val index = indexOf(rows, id) ?: return
        val row = rows.getJSONObject(index)
        val oldAmount = row.optDouble("amount", 0.0)
        val currentMonth = YearMonth.now()
        val postedThisMonth = row.optString("lastPaidMonth") == currentMonth.toString()
        captureUndo()

        row.put("category", category.name)
            .put("name", name.trim())
            .put("amount", amount)
            .put("dayOfMonth", dayOfMonth.coerceIn(1, 31))
        write("payments", rows)

        if (postedThisMonth) {
            val ledger = array("ledger")
            val marker = "payment:$id:$currentMonth"
            val ledgerIndex = indexOfReference(ledger, marker)
            if (ledgerIndex != null) {
                ledger.getJSONObject(ledgerIndex)
                    .put("name", name.trim())
                    .put("amount", amount)
                    .put("date", currentMonth.atDay(dayOfMonth.coerceIn(1, currentMonth.lengthOfMonth())).toString())
                write("ledger", ledger)
                setCash((cash() + oldAmount - amount).coerceAtLeast(0.0))
                recordBalanceSnapshot()
            }
        }
        FolioWidgetUpdater.request(appContext)
    }

    fun removePayment(id: String) {
        val rows = array("payments")
        val index = indexOf(rows, id) ?: return
        captureUndo()
        rows.remove(index)
        write("payments", rows)
        FolioWidgetUpdater.request(appContext)
    }

    fun updateIncome(id: String, name: String, amount: Double, dayOfMonth: Int, budgetMonthOffset: Int) {
        if (name.isBlank() || amount <= 0.0) return
        val rows = array("incomes")
        val index = indexOf(rows, id) ?: return
        val row = rows.getJSONObject(index)
        val oldAmount = row.optDouble("amount", 0.0)
        val currentMonth = YearMonth.now()
        val postedThisMonth = row.optString("lastReceivedMonth") == currentMonth.toString()
        captureUndo()

        row.put("name", name.trim())
            .put("amount", amount)
            .put("dayOfMonth", dayOfMonth.coerceIn(1, 31))
            .put("budgetMonthOffset", budgetMonthOffset.coerceIn(0, 12))
        write("incomes", rows)

        if (postedThisMonth) {
            val ledger = array("ledger")
            val marker = "income:$id:$currentMonth"
            val ledgerIndex = indexOfReference(ledger, marker)
            if (ledgerIndex != null) {
                ledger.getJSONObject(ledgerIndex)
                    .put("name", name.trim())
                    .put("amount", amount)
                    .put("date", currentMonth.atDay(dayOfMonth.coerceIn(1, currentMonth.lengthOfMonth())).toString())
                    .put("budgetMonth", currentMonth.plusMonths(budgetMonthOffset.coerceIn(0, 12).toLong()).toString())
                write("ledger", ledger)
                setCash((cash() + amount - oldAmount).coerceAtLeast(0.0))
                recordBalanceSnapshot()
            }
        }
        FolioWidgetUpdater.request(appContext)
    }

    fun removeIncome(id: String) {
        val rows = array("incomes")
        val index = indexOf(rows, id) ?: return
        captureUndo()
        rows.remove(index)
        write("incomes", rows)
        FolioWidgetUpdater.request(appContext)
    }

    fun updateRecurringInvestment(id: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0) return
        val rows = array("recurring_investments")
        val index = indexOf(rows, id) ?: return
        captureUndo()
        rows.getJSONObject(index)
            .put("amount", amount)
            .put("dayOfMonth", dayOfMonth.coerceIn(1, 31))
        write("recurring_investments", rows)
        FolioWidgetUpdater.request(appContext)
    }

    fun removeRecurringInvestment(id: String) {
        val rows = array("recurring_investments")
        val index = indexOf(rows, id) ?: return
        captureUndo()
        rows.remove(index)
        write("recurring_investments", rows)
        FolioWidgetUpdater.request(appContext)
    }

    private fun array(key: String): JSONArray = runCatching {
        JSONArray(prefs.getString(key, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun indexOf(rows: JSONArray, id: String): Int? {
        for (index in 0 until rows.length()) {
            if (rows.optJSONObject(index)?.optString("id") == id) return index
        }
        return null
    }

    private fun indexOfReference(rows: JSONArray, referenceId: String): Int? {
        for (index in 0 until rows.length()) {
            if (rows.optJSONObject(index)?.optString("referenceId") == referenceId) return index
        }
        return null
    }

    private fun write(key: String, rows: JSONArray) {
        prefs.edit().putString(key, rows.toString()).apply()
    }

    private fun cash(): Double = prefs.getString("cash_balance", null)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun setCash(value: Double) {
        prefs.edit().putString("cash_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    private fun recordBalanceSnapshot() {
        val value = FolioStore(appContext).summary().totalBalance
        val rows = array("balance_history")
        val now = System.currentTimeMillis()
        if (rows.length() > 0) {
            val last = rows.optJSONObject(rows.length() - 1)
            if (last != null && now - last.optLong("atMillis", 0L) < 60_000L) {
                last.put("atMillis", now).put("value", value)
                write("balance_history", rows)
                return
            }
        }
        rows.put(JSONObject().put("atMillis", now).put("value", value))
        while (rows.length() > 1000) rows.remove(0)
        write("balance_history", rows)
    }

    private fun captureUndo() {
        val keys = listOf(
            "cash_balance", "emergency_fund_balance", "crash_reserve_balance", "general_savings_balance",
            "emergency_fund_target", "crash_reserve_target", "savings_transfers", "expenses", "budgets",
            "payments", "incomes", "investments", "investment_transactions", "investment_prices",
            "recurring_investments", "ledger", "balance_history", "investment_history",
        )
        val snapshot = JSONObject()
        keys.forEach { key ->
            val value = prefs.all[key] ?: return@forEach
            val encoded = JSONObject()
            when (value) {
                is String -> encoded.put("type", "string").put("value", value)
                is Boolean -> encoded.put("type", "boolean").put("value", value)
                is Int -> encoded.put("type", "int").put("value", value)
                is Long -> encoded.put("type", "long").put("value", value)
                is Float -> encoded.put("type", "float").put("value", value.toDouble())
                is Set<*> -> {
                    val set = JSONArray()
                    value.filterIsInstance<String>().forEach(set::put)
                    encoded.put("type", "string_set").put("value", set)
                }
                else -> return@forEach
            }
            snapshot.put(key, encoded)
        }
        prefs.edit().putString("undo_snapshot", snapshot.toString()).apply()
    }
}
