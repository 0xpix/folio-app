package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.Budget
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentEntrySource
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.InvestmentTransaction
import com.pix.folio.model.LedgerEntry
import com.pix.folio.model.LedgerEntryType
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.RecurringIncome
import com.pix.folio.model.RecurringInvestment
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
        migrateRecurringLedgerOnce()
    }

    fun summary(): FolioSummary = FolioSummary(
        cashBalance = cashBalance(),
        investments = investments(),
        investmentTransactions = investmentTransactions(),
        recurringInvestments = recurringInvestments(),
        expenses = expenses(),
        budgets = budgets(),
        payments = payments(),
        incomes = incomes(),
        ledger = ledger(),
        balanceHistory = snapshots("balance_history"),
        investmentHistory = snapshots("investment_history"),
    )

    fun isAppLockEnabled(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    fun expenses(): List<Expense> = parseArray("expenses") { json ->
        Expense(
            id = json.getString("id"),
            category = ExpenseCategory.valueOf(json.getString("category")),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
            note = json.optString("note"),
        )
    }

    fun budgets(): List<Budget> = parseArray("budgets") { json ->
        Budget(
            category = ExpenseCategory.valueOf(json.getString("category")),
            monthlyLimit = json.getDouble("monthlyLimit"),
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
            isin = json.optString("isin"),
            figi = json.optString("figi"),
            exchange = json.optString("exchange"),
        )
    }

    fun investmentTransactions(): List<InvestmentTransaction> = parseArray("investment_transactions") { json ->
        InvestmentTransaction(
            id = json.getString("id"),
            holdingId = json.getString("holdingId"),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
            source = runCatching { InvestmentEntrySource.valueOf(json.optString("source")) }
                .getOrDefault(InvestmentEntrySource.MANUAL),
            referenceId = json.optString("referenceId"),
        )
    }.sortedByDescending { it.date }

    fun recurringInvestments(): List<RecurringInvestment> = parseArray("recurring_investments") { json ->
        RecurringInvestment(
            id = json.getString("id"),
            holdingId = json.getString("holdingId"),
            amount = json.getDouble("amount"),
            dayOfMonth = json.optInt("dayOfMonth", 1).coerceIn(1, 31),
            lastAppliedMonth = json.optString("lastAppliedMonth").takeIf { it.isNotBlank() }?.let {
                runCatching { YearMonth.parse(it) }.getOrNull()
            },
        )
    }

    fun ledger(): List<LedgerEntry> = parseArray("ledger") { json ->
        LedgerEntry(
            id = json.getString("id"),
            type = LedgerEntryType.valueOf(json.getString("type")),
            referenceId = json.optString("referenceId"),
            name = json.getString("name"),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
        )
    }.sortedByDescending { it.date }

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

    fun setBudget(category: ExpenseCategory, monthlyLimit: Double) {
        val current = budgets().filterNot { it.category == category }
        val next = if (monthlyLimit > 0.0) current + Budget(category, monthlyLimit) else current
        writeBudgets(next.sortedBy { it.category.ordinal })
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

        val currentLedger = ledger().toMutableList()
        val marker = "payment:${row.id}:$now"
        if (nextPaid) {
            if (currentLedger.none { it.referenceId == marker }) {
                currentLedger += LedgerEntry(
                    id = UUID.randomUUID().toString(),
                    type = LedgerEntryType.PAYMENT,
                    referenceId = marker,
                    name = row.name,
                    amount = row.amount,
                    date = row.dueDate,
                )
            }
            setCashBalanceInternal((cashBalance() - row.amount).coerceAtLeast(0.0))
        } else {
            currentLedger.removeAll { it.referenceId == marker }
            setCashBalanceInternal(cashBalance() + row.amount)
        }
        writeLedger(currentLedger)
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

        val currentLedger = ledger().toMutableList()
        val marker = "income:${row.id}:$now"
        if (nextReceived) {
            if (currentLedger.none { it.referenceId == marker }) {
                currentLedger += LedgerEntry(
                    id = UUID.randomUUID().toString(),
                    type = LedgerEntryType.INCOME,
                    referenceId = marker,
                    name = row.name,
                    amount = row.amount,
                    date = row.dueDate,
                )
            }
            setCashBalanceInternal(cashBalance() + row.amount)
        } else {
            currentLedger.removeAll { it.referenceId == marker }
            setCashBalanceInternal((cashBalance() - row.amount).coerceAtLeast(0.0))
        }
        writeLedger(currentLedger)
        recordSnapshots()
    }

    fun addInvestment(
        kind: InvestmentKind,
        name: String,
        symbol: String,
        amount: Double,
        isin: String = "",
        figi: String = "",
        exchange: String = "",
    ) {
        if (amount <= 0.0 || name.isBlank()) return
        val current = investments()
        val normalizedSymbol = symbol.trim().uppercase()
        val normalizedIsin = isin.trim().uppercase()
        val existing = current.firstOrNull {
            (normalizedIsin.isNotBlank() && it.isin.equals(normalizedIsin, ignoreCase = true)) ||
                (normalizedSymbol.isNotBlank() && it.symbol.equals(normalizedSymbol, ignoreCase = true)) ||
                it.name.equals(name.trim(), ignoreCase = true)
        }

        val holdingId: String
        val next = if (existing == null) {
            holdingId = UUID.randomUUID().toString()
            current + InvestmentHolding(
                id = holdingId,
                name = name.trim(),
                symbol = normalizedSymbol,
                kind = kind,
                amount = amount,
                isin = normalizedIsin,
                figi = figi.trim(),
                exchange = exchange.trim(),
            )
        } else {
            holdingId = existing.id
            current.map {
                if (it.id == existing.id) {
                    it.copy(
                        amount = it.amount + amount,
                        isin = it.isin.ifBlank { normalizedIsin },
                        figi = it.figi.ifBlank { figi.trim() },
                        exchange = it.exchange.ifBlank { exchange.trim() },
                        symbol = it.symbol.ifBlank { normalizedSymbol },
                    )
                } else it
            }
        }
        writeInvestments(next)
        appendInvestmentTransaction(holdingId, amount, InvestmentEntrySource.MANUAL)
        recordSnapshots()
    }

    fun addInvestmentContribution(holdingId: String, amount: Double) {
        if (amount <= 0.0) return
        val current = investments()
        if (current.none { it.id == holdingId }) return
        writeInvestments(current.map { if (it.id == holdingId) it.copy(amount = it.amount + amount) else it })
        appendInvestmentTransaction(holdingId, amount, InvestmentEntrySource.MANUAL)
        setCashBalanceInternal((cashBalance() - amount).coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun addRecurringInvestment(holdingId: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0 || investments().none { it.id == holdingId }) return
        writeRecurringInvestments(
            recurringInvestments() + RecurringInvestment(
                id = UUID.randomUUID().toString(),
                holdingId = holdingId,
                amount = amount,
                dayOfMonth = dayOfMonth.coerceIn(1, 31),
            )
        )
    }

    fun toggleRecurringInvestment(id: String) {
        val current = recurringInvestments()
        val row = current.firstOrNull { it.id == id } ?: return
        val holding = investments().firstOrNull { it.id == row.holdingId } ?: return
        val now = YearMonth.now()
        val nextApplied = !row.applied
        writeRecurringInvestments(current.map {
            if (it.id == id) it.copy(lastAppliedMonth = if (nextApplied) now else null) else it
        })

        val marker = "recurring-investment:${row.id}:$now"
        val tx = investmentTransactions().toMutableList()
        if (nextApplied) {
            writeInvestments(investments().map {
                if (it.id == holding.id) it.copy(amount = it.amount + row.amount) else it
            })
            if (tx.none { it.referenceId == marker }) {
                tx += InvestmentTransaction(
                    id = UUID.randomUUID().toString(),
                    holdingId = holding.id,
                    amount = row.amount,
                    date = row.dueDate,
                    source = InvestmentEntrySource.RECURRING,
                    referenceId = marker,
                )
            }
            setCashBalanceInternal((cashBalance() - row.amount).coerceAtLeast(0.0))
        } else {
            writeInvestments(investments().map {
                if (it.id == holding.id) it.copy(amount = (it.amount - row.amount).coerceAtLeast(0.0)) else it
            })
            tx.removeAll { it.referenceId == marker }
            setCashBalanceInternal(cashBalance() + row.amount)
        }
        writeInvestmentTransactions(tx)
        recordSnapshots()
    }

    fun removeInvestment(id: String) {
        writeInvestments(investments().filterNot { it.id == id })
        writeInvestmentTransactions(investmentTransactions().filterNot { it.holdingId == id })
        writeRecurringInvestments(recurringInvestments().filterNot { it.holdingId == id })
        recordSnapshots()
    }

    fun setCashBalance(value: Double) {
        setCashBalanceInternal(value.coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun clearAll() {
        val keepLock = isAppLockEnabled()
        prefs.edit().clear()
            .putBoolean("legacy_demo_removed_v3", true)
            .putBoolean("recurring_ledger_migrated_v1", true)
            .putBoolean("app_lock_enabled", keepLock)
            .apply()
    }

    private fun cashBalance(): Double = prefs.getString("cash_balance", null)?.toDoubleOrNull() ?: 0.0

    private fun setCashBalanceInternal(value: Double) {
        prefs.edit().putString("cash_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    private fun appendInvestmentTransaction(
        holdingId: String,
        amount: Double,
        source: InvestmentEntrySource,
        referenceId: String = "",
    ) {
        val next = investmentTransactions() + InvestmentTransaction(
            id = UUID.randomUUID().toString(),
            holdingId = holdingId,
            amount = amount,
            date = LocalDate.now(),
            source = source,
            referenceId = referenceId,
        )
        writeInvestmentTransactions(next)
    }

    private fun writeExpenses(rows: List<Expense>) = writeArray("expenses", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("category", row.category.name)
            .put("amount", row.amount)
            .put("date", row.date.toString())
            .put("note", row.note)
    }

    private fun writeBudgets(rows: List<Budget>) = writeArray("budgets", rows) { row ->
        JSONObject()
            .put("category", row.category.name)
            .put("monthlyLimit", row.monthlyLimit)
    }

    private fun writePayments(rows: List<MonthlyPayment>) = writeArray("payments", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("category", row.category.name)
            .put("name", row.name)
            .put("amount", row.amount)
            .put("dayOfMonth", row.dayOfMonth)
            .put("lastPaidMonth", row.lastPaidMonth?.toString() ?: "")
    }

    private fun writeIncomes(rows: List<RecurringIncome>) = writeArray("incomes", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("name", row.name)
            .put("amount", row.amount)
            .put("dayOfMonth", row.dayOfMonth)
            .put("glyph", row.glyph)
            .put("lastReceivedMonth", row.lastReceivedMonth?.toString() ?: "")
    }

    private fun writeInvestments(rows: List<InvestmentHolding>) = writeArray("investments", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("name", row.name)
            .put("symbol", row.symbol)
            .put("kind", row.kind.name)
            .put("amount", row.amount)
            .put("isin", row.isin)
            .put("figi", row.figi)
            .put("exchange", row.exchange)
    }

    private fun writeInvestmentTransactions(rows: List<InvestmentTransaction>) = writeArray("investment_transactions", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("holdingId", row.holdingId)
            .put("amount", row.amount)
            .put("date", row.date.toString())
            .put("source", row.source.name)
            .put("referenceId", row.referenceId)
    }

    private fun writeRecurringInvestments(rows: List<RecurringInvestment>) = writeArray("recurring_investments", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("holdingId", row.holdingId)
            .put("amount", row.amount)
            .put("dayOfMonth", row.dayOfMonth)
            .put("lastAppliedMonth", row.lastAppliedMonth?.toString() ?: "")
    }

    private fun writeLedger(rows: List<LedgerEntry>) = writeArray("ledger", rows) { row ->
        JSONObject()
            .put("id", row.id)
            .put("type", row.type.name)
            .put("referenceId", row.referenceId)
            .put("name", row.name)
            .put("amount", row.amount)
            .put("date", row.date.toString())
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
        val trimmed = rows.takeLast(1000)
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

    private fun migrateRecurringLedgerOnce() {
        if (prefs.getBoolean("recurring_ledger_migrated_v1", false)) return
        val now = YearMonth.now()
        val entries = ledger().toMutableList()
        payments().filter { it.lastPaidMonth == now }.forEach { row ->
            val marker = "payment:${row.id}:$now"
            if (entries.none { it.referenceId == marker }) {
                entries += LedgerEntry(UUID.randomUUID().toString(), LedgerEntryType.PAYMENT, marker, row.name, row.amount, row.dueDate)
            }
        }
        incomes().filter { it.lastReceivedMonth == now }.forEach { row ->
            val marker = "income:${row.id}:$now"
            if (entries.none { it.referenceId == marker }) {
                entries += LedgerEntry(UUID.randomUUID().toString(), LedgerEntryType.INCOME, marker, row.name, row.amount, row.dueDate)
            }
        }
        writeLedger(entries)
        prefs.edit().putBoolean("recurring_ledger_migrated_v1", true).apply()
    }

    private fun removeLegacyDemoDataOnce() {
        if (prefs.getBoolean("legacy_demo_removed_v3", false)) return

        listOf("expenses", "payments", "investments").forEach { key ->
            val raw = prefs.getString(key, null) ?: return@forEach
            val cleaned = runCatching {
                val source = JSONArray(raw)
                val target = JSONArray()
                for (i in 0 until source.length()) {
                    val row = source.optJSONObject(i) ?: continue
                    if (!row.optString("id").startsWith("seed-")) target.put(row)
                }
                target.toString()
            }.getOrNull()
            if (cleaned != null) prefs.edit().putString(key, cleaned).apply()
        }

        prefs.edit()
            .remove("monthly_income")
            .remove("monthly_investment")
            .putBoolean("legacy_demo_removed_v3", true)
            .apply()
    }
}
