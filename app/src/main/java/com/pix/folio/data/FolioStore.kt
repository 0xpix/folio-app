package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.RecurringIncome
import com.pix.folio.model.ValueSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class FolioStore(context: Context) {
    private val prefs = context.getSharedPreferences("folio_store_v2", Context.MODE_PRIVATE)

    init {
        removeLegacyDemoDataOnce()
    }

    fun summary(): FolioSummary = FolioSummary(
        cashBalance = cashBalance(),
        investments = investments(),
        expenses = expenses(),
        payments = payments(),
        incomes = incomes(),
        balanceHistory = snapshots("balance_history"),
        investmentHistory = snapshots("investment_history"),
    )

    fun expenses(): List<Expense> = parseArray("expenses") { json ->
        Expense(
            id = json.getString("id"),
            category = ExpenseCategory.valueOf(json.getString("category")),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
            note = json.optString("note"),
        )
    }

    fun payments(): List<MonthlyPayment> = parseArray("payments") { json ->
        val legacyDueDate = json.optString("dueDate").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val day = if (json.has("dayOfMonth")) json.optInt("dayOfMonth", 1) else legacyDueDate?.dayOfMonth ?: 1
        val storedMonth = json.optString("lastPaidMonth").takeIf { it.isNotBlank() }?.let {
            runCatching { YearMonth.parse(it) }.getOrNull()
        }
        val legacyPaid = json.optBoolean("paid", false)
        MonthlyPayment(
            id = json.getString("id"),
            category = runCatching { PaymentCategory.valueOf(json.getString("category")) }.getOrDefault(PaymentCategory.OTHER),
            name = json.getString("name"),
            amount = json.getDouble("amount"),
            dayOfMonth = day.coerceIn(1, 31),
            lastPaidMonth = storedMonth ?: if (legacyPaid) YearMonth.now() else null,
        )
    }

    fun incomes(): List<RecurringIncome> = parseArray("incomes") { json ->
        RecurringIncome(
            id = json.getString("id"),
            name = json.getString("name"),
            amount = json.getDouble("amount"),
            dayOfMonth = json.optInt("dayOfMonth", 1).coerceIn(1, 31),
            glyph = json.optString("glyph").ifBlank { "💼" },
            lastReceivedMonth = json.optString("lastReceivedMonth").takeIf { it.isNotBlank() }?.let {
                runCatching { YearMonth.parse(it) }.getOrNull()
            },
        )
    }

    fun investments(): List<InvestmentHolding> = parseArray("investments") { json ->
        val kind = json.optString("kind").takeIf { it.isNotBlank() }?.let {
            runCatching { InvestmentKind.valueOf(it) }.getOrNull()
        } ?: when (json.optString("group")) {
            "ETFs" -> InvestmentKind.ETF
            "Stocks" -> InvestmentKind.STOCK
            "Funds" -> InvestmentKind.FUND
            else -> InvestmentKind.OTHER
        }
        InvestmentHolding(
            id = json.getString("id"),
            name = json.getString("name"),
            symbol = json.optString("symbol"),
            kind = kind,
            amount = json.getDouble("amount"),
        )
    }

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        if (amount <= 0.0) return
        writeExpenses(
            expenses() + Expense(
                id = UUID.randomUUID().toString(),
                category = category,
                amount = amount,
                date = LocalDate.now(),
                note = note.trim(),
            )
        )
        setCashBalanceInternal((cashBalance() - amount).coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun addPayment(category: PaymentCategory, name: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0 || name.isBlank()) return
        writePayments(
            payments() + MonthlyPayment(
                id = UUID.randomUUID().toString(),
                category = category,
                name = name.trim(),
                amount = amount,
                dayOfMonth = dayOfMonth.coerceIn(1, 31),
            )
        )
    }

    fun togglePayment(id: String) {
        val current = payments()
        val row = current.firstOrNull { it.id == id } ?: return
        val now = YearMonth.now()
        val nextPaid = !row.paid
        writePayments(current.map {
            if (it.id == id) it.copy(lastPaidMonth = if (nextPaid) now else null) else it
        })
        setCashBalanceInternal(
            if (nextPaid) (cashBalance() - row.amount).coerceAtLeast(0.0)
            else cashBalance() + row.amount
        )
        recordSnapshots()
    }

    fun addIncome(name: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0 || name.isBlank()) return
        writeIncomes(
            incomes() + RecurringIncome(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                amount = amount,
                dayOfMonth = dayOfMonth.coerceIn(1, 31),
            )
        )
    }

    fun toggleIncomeReceived(id: String) {
        val current = incomes()
        val row = current.firstOrNull { it.id == id } ?: return
        val now = YearMonth.now()
        val nextReceived = !row.received
        writeIncomes(current.map {
            if (it.id == id) it.copy(lastReceivedMonth = if (nextReceived) now else null) else it
        })
        setCashBalanceInternal(
            if (nextReceived) cashBalance() + row.amount
            else (cashBalance() - row.amount).coerceAtLeast(0.0)
        )
        recordSnapshots()
    }

    fun addInvestment(kind: InvestmentKind, name: String, symbol: String, amount: Double) {
        if (amount <= 0.0 || name.isBlank()) return
        val current = investments()
        val normalizedSymbol = symbol.trim().uppercase()
        val existing = current.firstOrNull {
            it.kind == kind && (
                (normalizedSymbol.isNotBlank() && it.symbol.equals(normalizedSymbol, ignoreCase = true)) ||
                    it.name.equals(name.trim(), ignoreCase = true)
                )
        }
        val next = if (existing == null) {
            current + InvestmentHolding(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                symbol = normalizedSymbol,
                kind = kind,
                amount = amount,
            )
        } else {
            current.map { if (it.id == existing.id) it.copy(amount = it.amount + amount) else it }
        }
        writeInvestments(next)
        recordSnapshots()
    }

    fun removeInvestment(id: String) {
        val next = investments().filterNot { it.id == id }
        writeInvestments(next)
        recordSnapshots()
    }

    fun setCashBalance(value: Double) {
        setCashBalanceInternal(value.coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun clearAll() {
        prefs.edit().clear().putBoolean("legacy_demo_removed_v3", true).apply()
    }

    private fun cashBalance(): Double = prefs.getString("cash_balance", null)?.toDoubleOrNull() ?: 0.0

    private fun setCashBalanceInternal(value: Double) {
        prefs.edit().putString("cash_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    private fun writeExpenses(rows: List<Expense>) {
        writeArray("expenses", rows) { row ->
            JSONObject()
                .put("id", row.id)
                .put("category", row.category.name)
                .put("amount", row.amount)
                .put("date", row.date.toString())
                .put("note", row.note)
        }
    }

    private fun writePayments(rows: List<MonthlyPayment>) {
        writeArray("payments", rows) { row ->
            JSONObject()
                .put("id", row.id)
                .put("category", row.category.name)
                .put("name", row.name)
                .put("amount", row.amount)
                .put("dayOfMonth", row.dayOfMonth)
                .put("lastPaidMonth", row.lastPaidMonth?.toString() ?: "")
        }
    }

    private fun writeIncomes(rows: List<RecurringIncome>) {
        writeArray("incomes", rows) { row ->
            JSONObject()
                .put("id", row.id)
                .put("name", row.name)
                .put("amount", row.amount)
                .put("dayOfMonth", row.dayOfMonth)
                .put("glyph", row.glyph)
                .put("lastReceivedMonth", row.lastReceivedMonth?.toString() ?: "")
        }
    }

    private fun writeInvestments(rows: List<InvestmentHolding>) {
        writeArray("investments", rows) { row ->
            JSONObject()
                .put("id", row.id)
                .put("name", row.name)
                .put("symbol", row.symbol)
                .put("kind", row.kind.name)
                .put("amount", row.amount)
        }
    }

    private fun recordSnapshots() {
        val investmentTotal = investments().sumOf { it.amount }
        appendSnapshot("investment_history", investmentTotal)
        appendSnapshot("balance_history", cashBalance() + investmentTotal)
    }

    private fun appendSnapshot(key: String, value: Double) {
        val now = System.currentTimeMillis()
        val rows = snapshots(key).toMutableList()
        val last = rows.lastOrNull()
        if (last != null && now - last.atMillis < 60_000L) {
            rows[rows.lastIndex] = ValueSnapshot(now, value)
        } else {
            rows += ValueSnapshot(now, value)
        }
        val trimmed = rows.takeLast(500)
        val array = JSONArray()
        trimmed.forEach { row ->
            array.put(JSONObject().put("atMillis", row.atMillis).put("value", row.value))
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun snapshots(key: String): List<ValueSnapshot> = parseArray(key) { json ->
        ValueSnapshot(json.getLong("atMillis"), json.getDouble("value"))
    }.sortedBy { it.atMillis }

    private fun <T> parseArray(key: String, parser: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.optJSONObject(i) ?: continue
                    runCatching { parser(json) }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun <T> writeArray(key: String, rows: List<T>, encode: (T) -> JSONObject) {
        val array = JSONArray()
        rows.forEach { array.put(encode(it)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun removeLegacyDemoDataOnce() {
        if (prefs.getBoolean("legacy_demo_removed_v3", false)) return

        val expenseRaw = prefs.getString("expenses", null)
        if (expenseRaw != null) {
            val cleaned = runCatching {
                val source = JSONArray(expenseRaw)
                val target = JSONArray()
                for (i in 0 until source.length()) {
                    val row = source.optJSONObject(i) ?: continue
                    if (!row.optString("id").startsWith("seed-")) target.put(row)
                }
                target.toString()
            }.getOrNull()
            if (cleaned != null) prefs.edit().putString("expenses", cleaned).apply()
        }

        val paymentRaw = prefs.getString("payments", null)
        if (paymentRaw != null) {
            val cleaned = runCatching {
                val source = JSONArray(paymentRaw)
                val target = JSONArray()
                for (i in 0 until source.length()) {
                    val row = source.optJSONObject(i) ?: continue
                    if (!row.optString("id").startsWith("seed-")) target.put(row)
                }
                target.toString()
            }.getOrNull()
            if (cleaned != null) prefs.edit().putString("payments", cleaned).apply()
        }

        prefs.edit()
            .remove("cash_balance")
            .remove("monthly_income")
            .remove("monthly_investment")
            .putBoolean("legacy_demo_removed_v3", true)
            .apply()
    }
}
