package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.Budget
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentEntrySource
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.InvestmentPricePoint
import com.pix.folio.model.InvestmentTag
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

    private val undoKeys = listOf(
        "cash_balance",
        "emergency_fund_balance",
        "expenses",
        "budgets",
        "payments",
        "incomes",
        "investments",
        "investment_transactions",
        "investment_prices",
        "recurring_investments",
        "ledger",
        "balance_history",
        "investment_history",
    )

    init {
        removeLegacyDemoDataOnce()
        migrateRecurringLedgerOnce()
    }

    fun summary(): FolioSummary = FolioSummary(
        cashBalance = cashBalance(),
        emergencyFundBalance = emergencyFundBalance(),
        investments = investments(),
        investmentTransactions = investmentTransactions(),
        investmentPrices = investmentPrices(),
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

    fun canUndo(): Boolean = prefs.getString("undo_snapshot", null) != null

    fun undoLastChange(): Boolean {
        val raw = prefs.getString("undo_snapshot", null) ?: return false
        val snapshot = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val editor = prefs.edit()
        undoKeys.forEach { editor.remove(it) }

        val keys = snapshot.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val encoded = snapshot.optJSONObject(key) ?: continue
            when (encoded.optString("type")) {
                "string" -> editor.putString(key, encoded.optString("value"))
                "boolean" -> editor.putBoolean(key, encoded.optBoolean("value"))
                "int" -> editor.putInt(key, encoded.optInt("value"))
                "long" -> editor.putLong(key, encoded.optLong("value"))
                "float" -> editor.putFloat(key, encoded.optDouble("value").toFloat())
                "string_set" -> {
                    val values = encoded.optJSONArray("value") ?: JSONArray()
                    val set = buildSet {
                        for (index in 0 until values.length()) add(values.optString(index))
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.remove("undo_snapshot").apply()
        return true
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
            "Bonds" -> InvestmentKind.BOND
            "Indexes" -> InvestmentKind.INDEX
            "CS2" -> InvestmentKind.CS2
            else -> InvestmentKind.OTHER
        }

        val tags = buildSet {
            val array = json.optJSONArray("tags")
            if (array != null) {
                for (index in 0 until array.length()) {
                    runCatching { InvestmentTag.valueOf(array.optString(index)) }.getOrNull()?.let(::add)
                }
            } else {
                json.optString("tags")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach { value -> runCatching { InvestmentTag.valueOf(value) }.getOrNull()?.let(::add) }
            }
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
            priceSymbol = json.optString("priceSymbol"),
            priceCurrency = json.optString("priceCurrency"),
            priceSource = json.optString("priceSource"),
            lastPriceRefreshMillis = json.optLong("lastPriceRefreshMillis", 0L),
            tags = tags,
            note = json.optString("note"),
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
            units = json.optDouble("units", 0.0),
            unitPrice = json.optDouble("unitPrice", 0.0),
        )
    }.sortedByDescending { it.date }

    fun investmentPrices(): List<InvestmentPricePoint> = parseArray("investment_prices") { json ->
        InvestmentPricePoint(
            holdingId = json.getString("holdingId"),
            date = LocalDate.parse(json.getString("date")),
            close = json.getDouble("close"),
            currency = json.optString("currency"),
            symbol = json.optString("symbol"),
        )
    }.sortedWith(compareBy<InvestmentPricePoint> { it.holdingId }.thenBy { it.date })

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
        captureUndo()
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
        captureUndo()
        val current = budgets().filterNot { it.category == category }
        val next = if (monthlyLimit > 0.0) current + Budget(category, monthlyLimit) else current
        writeBudgets(next.sortedBy { it.category.ordinal })
    }

    fun addPayment(category: PaymentCategory, name: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0 || name.isBlank()) return
        captureUndo()
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
        captureUndo()
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
        captureUndo()
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
        captureUndo()
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
        units: Double = 0.0,
        unitPrice: Double = 0.0,
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
        captureUndo()

        val holdingId: String
        val source: InvestmentEntrySource
        val next = if (existing == null) {
            holdingId = UUID.randomUUID().toString()
            source = InvestmentEntrySource.INITIAL
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
            source = InvestmentEntrySource.MANUAL
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
        appendInvestmentTransaction(holdingId, amount, source, units = units, unitPrice = unitPrice)
        recordSnapshots()
    }

    fun addInvestmentContribution(
        holdingId: String,
        amount: Double,
        units: Double = 0.0,
        unitPrice: Double = 0.0,
    ) {
        if (amount <= 0.0) return
        val current = investments()
        if (current.none { it.id == holdingId }) return
        captureUndo()
        writeInvestments(current.map { if (it.id == holdingId) it.copy(amount = it.amount + amount) else it })
        appendInvestmentTransaction(
            holdingId = holdingId,
            amount = amount,
            source = InvestmentEntrySource.MANUAL,
            units = units,
            unitPrice = unitPrice,
        )
        setCashBalanceInternal((cashBalance() - amount).coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun addRecurringInvestment(holdingId: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0 || investments().none { it.id == holdingId }) return
        captureUndo()
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
        captureUndo()
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

    fun setEmergencyFundBalance(value: Double) {
        captureUndo()
        prefs.edit().putString("emergency_fund_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    fun updateInvestmentDetails(id: String, tags: Set<InvestmentTag>, note: String) {
        val current = investments()
        if (current.none { it.id == id }) return
        captureUndo()
        writeInvestments(current.map {
            if (it.id == id) it.copy(tags = tags, note = note.trim().take(500)) else it
        })
    }

    fun addInvestmentPrice(
        holdingId: String,
        close: Double,
        currency: String = "",
        symbol: String = "",
        source: String = "Manual",
        date: LocalDate = LocalDate.now(),
    ) {
        if (close <= 0.0) return
        val holdings = investments()
        val holding = holdings.firstOrNull { it.id == holdingId } ?: return
        captureUndo()

        val prices = investmentPrices()
            .filterNot { it.holdingId == holdingId && it.date == date } + InvestmentPricePoint(
            holdingId = holdingId,
            date = date,
            close = close,
            currency = currency.trim().uppercase(),
            symbol = symbol.trim().uppercase(),
        )
        writeInvestmentPrices(prices)
        writeInvestments(holdings.map {
            if (it.id == holdingId) {
                it.copy(
                    priceSymbol = symbol.trim().uppercase().ifBlank { holding.priceSymbol },
                    priceCurrency = currency.trim().uppercase().ifBlank { holding.priceCurrency },
                    priceSource = source.trim(),
                    lastPriceRefreshMillis = System.currentTimeMillis(),
                )
            } else it
        })
        recordSnapshots()
    }

    fun removeInvestment(id: String) {
        if (investments().none { it.id == id }) return
        captureUndo()
        writeInvestments(investments().filterNot { it.id == id })
        writeInvestmentTransactions(investmentTransactions().filterNot { it.holdingId == id })
        writeInvestmentPrices(investmentPrices().filterNot { it.holdingId == id })
        writeRecurringInvestments(recurringInvestments().filterNot { it.holdingId == id })
        recordSnapshots()
    }

    fun setCashBalance(value: Double) {
        captureUndo()
        setCashBalanceInternal(value.coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun clearAll() {
        val keepLock = isAppLockEnabled()
        captureUndo()
        val undo = prefs.getString("undo_snapshot", null)
        val editor = prefs.edit().clear()
            .putBoolean("legacy_demo_removed_v3", true)
            .putBoolean("recurring_ledger_migrated_v1", true)
            .putBoolean("app_lock_enabled", keepLock)
        if (undo != null) editor.putString("undo_snapshot", undo)
        editor.apply()
    }

    private fun cashBalance(): Double = prefs.getString("cash_balance", null)?.toDoubleOrNull() ?: 0.0

    private fun emergencyFundBalance(): Double =
        prefs.getString("emergency_fund_balance", null)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun setCashBalanceInternal(value: Double) {
        prefs.edit().putString("cash_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    private fun appendInvestmentTransaction(
        holdingId: String,
        amount: Double,
        source: InvestmentEntrySource,
        referenceId: String = "",
        units: Double = 0.0,
        unitPrice: Double = 0.0,
    ) {
        val next = investmentTransactions() + InvestmentTransaction(
            id = UUID.randomUUID().toString(),
            holdingId = holdingId,
            amount = amount,
            date = LocalDate.now(),
            source = source,
            referenceId = referenceId,
            units = units.coerceAtLeast(0.0),
            unitPrice = unitPrice.coerceAtLeast(0.0),
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
        val tags = JSONArray().apply {
            row.tags.sortedBy { it.ordinal }.forEach { put(it.name) }
        }
        JSONObject()
            .put("id", row.id)
            .put("name", row.name)
            .put("symbol", row.symbol)
            .put("kind", row.kind.name)
            .put("amount", row.amount)
            .put("isin", row.isin)
            .put("figi", row.figi)
            .put("exchange", row.exchange)
            .put("priceSymbol", row.priceSymbol)
            .put("priceCurrency", row.priceCurrency)
            .put("priceSource", row.priceSource)
            .put("lastPriceRefreshMillis", row.lastPriceRefreshMillis)
            .put("tags", tags)
            .put("note", row.note)
    }

    private fun writeInvestmentTransactions(rows: List<InvestmentTransaction>) =
        writeArray("investment_transactions", rows) { row ->
            JSONObject()
                .put("id", row.id)
                .put("holdingId", row.holdingId)
                .put("amount", row.amount)
                .put("date", row.date.toString())
                .put("source", row.source.name)
                .put("referenceId", row.referenceId)
                .put("units", row.units)
                .put("unitPrice", row.unitPrice)
        }

    private fun writeInvestmentPrices(rows: List<InvestmentPricePoint>) = writeArray("investment_prices", rows) { row ->
        JSONObject()
            .put("holdingId", row.holdingId)
            .put("date", row.date.toString())
            .put("close", row.close)
            .put("currency", row.currency)
            .put("symbol", row.symbol)
    }

    private fun writeRecurringInvestments(rows: List<RecurringInvestment>) =
        writeArray("recurring_investments", rows) { row ->
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
        val investmentTotal = investmentMarketTotal()
        appendSnapshot("investment_history", investmentTotal)
        appendSnapshot("balance_history", cashBalance() + investmentTotal)
    }

    private fun investmentMarketTotal(): Double {
        val transactions = investmentTransactions()
        val prices = investmentPrices()
        return investments().sumOf { holding ->
            val units = transactions.filter { it.holdingId == holding.id }.sumOf { it.units }
            val latest = prices.filter { it.holdingId == holding.id }.maxByOrNull { it.date }?.close
            if (units > 0.0 && latest != null && latest > 0.0) units * latest else holding.amount
        }
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

    private fun captureUndo() {
        val snapshot = JSONObject()
        undoKeys.forEach { key ->
            val value = prefs.all[key] ?: return@forEach
            val encoded = JSONObject()
            when (value) {
                is String -> encoded.put("type", "string").put("value", value)
                is Boolean -> encoded.put("type", "boolean").put("value", value)
                is Int -> encoded.put("type", "int").put("value", value)
                is Long -> encoded.put("type", "long").put("value", value)
                is Float -> encoded.put("type", "float").put("value", value.toDouble())
                is Set<*> -> {
                    val array = JSONArray()
                    value.filterIsInstance<String>().forEach(array::put)
                    encoded.put("type", "string_set").put("value", array)
                }
                else -> return@forEach
            }
            snapshot.put(key, encoded)
        }
        prefs.edit().putString("undo_snapshot", snapshot.toString()).apply()
    }

    private fun <T> parseArray(key: String, parser: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
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
                entries += LedgerEntry(
                    UUID.randomUUID().toString(),
                    LedgerEntryType.PAYMENT,
                    marker,
                    row.name,
                    row.amount,
                    row.dueDate,
                )
            }
        }
        incomes().filter { it.lastReceivedMonth == now }.forEach { row ->
            val marker = "income:${row.id}:$now"
            if (entries.none { it.referenceId == marker }) {
                entries += LedgerEntry(
                    UUID.randomUUID().toString(),
                    LedgerEntryType.INCOME,
                    marker,
                    row.name,
                    row.amount,
                    row.dueDate,
                )
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
                for (index in 0 until source.length()) {
                    val row = source.optJSONObject(index) ?: continue
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
