package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.AppFontChoice
import com.pix.folio.model.Budget
import com.pix.folio.model.Cs2AssetType
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
import com.pix.folio.model.SavingsBucketType
import com.pix.folio.model.SavingsTransfer
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
        "crash_reserve_balance",
        "general_savings_balance",
        "emergency_fund_target",
        "crash_reserve_target",
        "savings_transfers",
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
        migrateV07Once()
    }

    fun summary(): FolioSummary = FolioSummary(
        cashBalance = cashBalance(),
        emergencyFundBalance = savingsBalance(SavingsBucketType.EMERGENCY),
        crashReserveBalance = savingsBalance(SavingsBucketType.CRASH_RESERVE),
        generalSavingsBalance = savingsBalance(SavingsBucketType.GENERAL),
        emergencyFundTarget = savingsTarget(SavingsBucketType.EMERGENCY),
        crashReserveTarget = savingsTarget(SavingsBucketType.CRASH_RESERVE),
        savingsTransfers = savingsTransfers(),
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

    fun fontChoice(): AppFontChoice = runCatching {
        AppFontChoice.valueOf(prefs.getString("app_font_choice", null) ?: AppFontChoice.PIXELIFY.name)
    }.getOrDefault(AppFontChoice.PIXELIFY)

    fun setFontChoice(choice: AppFontChoice) {
        prefs.edit().putString("app_font_choice", choice.name).apply()
    }

    fun autoRecurringEnabled(): Boolean = prefs.getBoolean("auto_recurring_enabled", true)

    fun setAutoRecurringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_recurring_enabled", enabled).apply()
    }

    fun canUndo(): Boolean = prefs.getString("undo_snapshot", null) != null

    fun undoLastChange(): Boolean {
        val raw = prefs.getString("undo_snapshot", null) ?: return false
        val snapshot = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val editor = prefs.edit()
        undoKeys.forEach(editor::remove)
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
                    editor.putStringSet(key, buildSet {
                        for (index in 0 until values.length()) add(values.optString(index))
                    })
                }
            }
        }
        editor.remove("undo_snapshot").apply()
        return true
    }

    fun expenses(): List<Expense> = parseArray("expenses") { json ->
        Expense(
            id = json.getString("id"),
            category = runCatching { ExpenseCategory.valueOf(json.getString("category")) }.getOrDefault(ExpenseCategory.OTHER),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
            note = json.optString("note"),
        )
    }

    fun budgets(): List<Budget> = parseArray("budgets") { json ->
        Budget(
            category = runCatching { ExpenseCategory.valueOf(json.getString("category")) }.getOrDefault(ExpenseCategory.OTHER),
            monthlyLimit = json.getDouble("monthlyLimit"),
        )
    }

    fun payments(): List<MonthlyPayment> = parseArray("payments") { json ->
        val legacyDueDate = json.optString("dueDate").takeIf(String::isNotBlank)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val storedMonth = json.optString("lastPaidMonth").takeIf(String::isNotBlank)?.let {
            runCatching { YearMonth.parse(it) }.getOrNull()
        }
        MonthlyPayment(
            id = json.getString("id"),
            category = runCatching { PaymentCategory.valueOf(json.getString("category")) }.getOrDefault(PaymentCategory.OTHER),
            name = json.getString("name"),
            amount = json.getDouble("amount"),
            dayOfMonth = (if (json.has("dayOfMonth")) json.optInt("dayOfMonth", 1) else legacyDueDate?.dayOfMonth ?: 1).coerceIn(1, 31),
            lastPaidMonth = storedMonth ?: if (json.optBoolean("paid", false)) YearMonth.now() else null,
            enabled = json.optBoolean("enabled", true),
        )
    }

    fun incomes(): List<RecurringIncome> = parseArray("incomes") { json ->
        val name = json.getString("name")
        val legacyOffset = if (
            name.contains("salary", true) || name.contains("stipend", true) || name.contains("wage", true)
        ) 1 else 0
        RecurringIncome(
            id = json.getString("id"),
            name = name,
            amount = json.getDouble("amount"),
            dayOfMonth = json.optInt("dayOfMonth", 1).coerceIn(1, 31),
            glyph = json.optString("glyph").ifBlank { "💼" },
            lastReceivedMonth = json.optString("lastReceivedMonth").takeIf(String::isNotBlank)?.let {
                runCatching { YearMonth.parse(it) }.getOrNull()
            },
            budgetMonthOffset = json.optInt("budgetMonthOffset", legacyOffset).coerceIn(0, 12),
            enabled = json.optBoolean("enabled", true),
        )
    }

    fun investments(): List<InvestmentHolding> = parseArray("investments") { json ->
        val kind = json.optString("kind").takeIf(String::isNotBlank)?.let {
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
                json.optString("tags").split(',').map(String::trim).filter(String::isNotBlank).forEach { value ->
                    runCatching { InvestmentTag.valueOf(value) }.getOrNull()?.let(::add)
                }
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
            marketHashName = json.optString("marketHashName"),
            cs2AssetType = runCatching { Cs2AssetType.valueOf(json.optString("cs2AssetType")) }.getOrDefault(Cs2AssetType.OTHER),
        )
    }

    fun investmentTransactions(): List<InvestmentTransaction> = parseArray("investment_transactions") { json ->
        InvestmentTransaction(
            id = json.getString("id"),
            holdingId = json.getString("holdingId"),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
            source = runCatching { InvestmentEntrySource.valueOf(json.optString("source")) }.getOrDefault(InvestmentEntrySource.MANUAL),
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
            lastAppliedMonth = json.optString("lastAppliedMonth").takeIf(String::isNotBlank)?.let {
                runCatching { YearMonth.parse(it) }.getOrNull()
            },
            enabled = json.optBoolean("enabled", true),
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
            budgetMonth = json.optString("budgetMonth").takeIf(String::isNotBlank)?.let {
                runCatching { YearMonth.parse(it) }.getOrNull()
            },
        )
    }.sortedByDescending { it.date }

    fun savingsTransfers(): List<SavingsTransfer> = parseArray("savings_transfers") { json ->
        SavingsTransfer(
            id = json.getString("id"),
            bucket = runCatching { SavingsBucketType.valueOf(json.getString("bucket")) }.getOrDefault(SavingsBucketType.GENERAL),
            amount = json.getDouble("amount"),
            date = LocalDate.parse(json.getString("date")),
            note = json.optString("note"),
        )
    }.sortedByDescending { it.date }

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        if (amount <= 0.0) return
        captureUndo()
        writeExpenses(expenses() + Expense(UUID.randomUUID().toString(), category, amount, LocalDate.now(), note.trim()))
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
        writePayments(payments() + MonthlyPayment(UUID.randomUUID().toString(), category, name.trim(), amount, dayOfMonth.coerceIn(1, 31)))
    }

    fun togglePayment(id: String) {
        val current = payments()
        val row = current.firstOrNull { it.id == id } ?: return
        captureUndo()
        val month = YearMonth.now()
        val nextPaid = row.lastPaidMonth != month
        val marker = "payment:${row.id}:$month"
        val entries = ledger().toMutableList()
        writePayments(current.map { if (it.id == id) it.copy(lastPaidMonth = if (nextPaid) month else null) else it })
        if (nextPaid) {
            if (entries.none { it.referenceId == marker }) {
                entries += LedgerEntry(UUID.randomUUID().toString(), LedgerEntryType.PAYMENT, marker, row.name, row.amount, row.dueDateFor(month), month)
            }
            setCashBalanceInternal((cashBalance() - row.amount).coerceAtLeast(0.0))
        } else {
            entries.removeAll { it.referenceId == marker }
            setCashBalanceInternal(cashBalance() + row.amount)
        }
        writeLedger(entries)
        recordSnapshots()
    }

    fun addIncome(name: String, amount: Double, dayOfMonth: Int, budgetMonthOffset: Int = 0) {
        if (amount <= 0.0 || name.isBlank()) return
        captureUndo()
        writeIncomes(incomes() + RecurringIncome(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            amount = amount,
            dayOfMonth = dayOfMonth.coerceIn(1, 31),
            budgetMonthOffset = budgetMonthOffset.coerceIn(0, 12),
        ))
    }

    fun toggleIncomeReceived(id: String) {
        val current = incomes()
        val row = current.firstOrNull { it.id == id } ?: return
        captureUndo()
        val month = YearMonth.now()
        val nextReceived = row.lastReceivedMonth != month
        val marker = "income:${row.id}:$month"
        val entries = ledger().toMutableList()
        writeIncomes(current.map { if (it.id == id) it.copy(lastReceivedMonth = if (nextReceived) month else null) else it })
        if (nextReceived) {
            if (entries.none { it.referenceId == marker }) {
                entries += LedgerEntry(
                    UUID.randomUUID().toString(),
                    LedgerEntryType.INCOME,
                    marker,
                    row.name,
                    row.amount,
                    row.dueDateFor(month),
                    row.budgetMonthFor(month),
                )
            }
            setCashBalanceInternal(cashBalance() + row.amount)
        } else {
            entries.removeAll { it.referenceId == marker }
            setCashBalanceInternal((cashBalance() - row.amount).coerceAtLeast(0.0))
        }
        writeLedger(entries)
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
        marketHashName: String = "",
        cs2AssetType: Cs2AssetType = Cs2AssetType.OTHER,
    ) {
        if (amount <= 0.0 || name.isBlank()) return
        val current = investments()
        val normalizedSymbol = symbol.trim().uppercase()
        val normalizedIsin = isin.trim().uppercase()
        val existing = current.firstOrNull {
            (normalizedIsin.isNotBlank() && it.isin.equals(normalizedIsin, true)) ||
                (normalizedSymbol.isNotBlank() && it.symbol.equals(normalizedSymbol, true)) ||
                (marketHashName.isNotBlank() && it.marketHashName.equals(marketHashName.trim(), true)) ||
                it.name.equals(name.trim(), true)
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
                priceSymbol = normalizedSymbol,
                marketHashName = marketHashName.trim(),
                cs2AssetType = cs2AssetType,
            )
        } else {
            holdingId = existing.id
            source = InvestmentEntrySource.MANUAL
            current.map {
                if (it.id == existing.id) it.copy(
                    amount = it.amount + amount,
                    isin = it.isin.ifBlank { normalizedIsin },
                    figi = it.figi.ifBlank { figi.trim() },
                    exchange = it.exchange.ifBlank { exchange.trim() },
                    symbol = it.symbol.ifBlank { normalizedSymbol },
                    priceSymbol = it.priceSymbol.ifBlank { normalizedSymbol },
                    marketHashName = it.marketHashName.ifBlank { marketHashName.trim() },
                    cs2AssetType = if (it.cs2AssetType == Cs2AssetType.OTHER) cs2AssetType else it.cs2AssetType,
                ) else it
            }
        }
        writeInvestments(next)
        appendInvestmentTransaction(holdingId, amount, source, units = units, unitPrice = unitPrice)
        recordSnapshots()
    }

    fun addInvestmentContribution(holdingId: String, amount: Double, units: Double = 0.0, unitPrice: Double = 0.0) {
        if (amount <= 0.0 || investments().none { it.id == holdingId }) return
        captureUndo()
        writeInvestments(investments().map { if (it.id == holdingId) it.copy(amount = it.amount + amount) else it })
        appendInvestmentTransaction(holdingId, amount, InvestmentEntrySource.MANUAL, units = units, unitPrice = unitPrice)
        setCashBalanceInternal((cashBalance() - amount).coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun addRecurringInvestment(holdingId: String, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0 || investments().none { it.id == holdingId }) return
        captureUndo()
        writeRecurringInvestments(recurringInvestments() + RecurringInvestment(
            UUID.randomUUID().toString(), holdingId, amount, dayOfMonth.coerceIn(1, 31)
        ))
    }

    fun toggleRecurringInvestment(id: String) {
        val current = recurringInvestments()
        val row = current.firstOrNull { it.id == id } ?: return
        val holding = investments().firstOrNull { it.id == row.holdingId } ?: return
        captureUndo()
        val month = YearMonth.now()
        val nextApplied = row.lastAppliedMonth != month
        val marker = "recurring-investment:${row.id}:$month"
        val tx = investmentTransactions().toMutableList()
        writeRecurringInvestments(current.map { if (it.id == id) it.copy(lastAppliedMonth = if (nextApplied) month else null) else it })
        if (nextApplied) {
            val latest = investmentPrices().filter { it.holdingId == holding.id }.maxByOrNull { it.date }?.close ?: 0.0
            val units = if (latest > 0.0) row.amount / latest else 0.0
            writeInvestments(investments().map { if (it.id == holding.id) it.copy(amount = it.amount + row.amount) else it })
            if (tx.none { it.referenceId == marker }) {
                tx += InvestmentTransaction(
                    UUID.randomUUID().toString(), holding.id, row.amount, row.dueDateFor(month),
                    InvestmentEntrySource.RECURRING, marker, units, latest
                )
            }
            setCashBalanceInternal((cashBalance() - row.amount).coerceAtLeast(0.0))
        } else {
            writeInvestments(investments().map { if (it.id == holding.id) it.copy(amount = (it.amount - row.amount).coerceAtLeast(0.0)) else it })
            tx.removeAll { it.referenceId == marker }
            setCashBalanceInternal(cashBalance() + row.amount)
        }
        writeInvestmentTransactions(tx)
        recordSnapshots()
    }

    fun setSavingsTarget(bucket: SavingsBucketType, value: Double) {
        if (bucket == SavingsBucketType.GENERAL) return
        captureUndo()
        prefs.edit().putString(targetKey(bucket), value.coerceAtLeast(0.0).toString()).apply()
    }

    fun transferSavings(bucket: SavingsBucketType, amount: Double, note: String = "") {
        if (amount == 0.0) return
        val currentBucket = savingsBalance(bucket)
        val actual = if (amount > 0.0) amount.coerceAtMost(cashBalance()) else -((-amount).coerceAtMost(currentBucket))
        if (actual == 0.0) return
        captureUndo()
        setSavingsBalanceInternal(bucket, (currentBucket + actual).coerceAtLeast(0.0))
        setCashBalanceInternal((cashBalance() - actual).coerceAtLeast(0.0))
        writeSavingsTransfers(savingsTransfers() + SavingsTransfer(
            UUID.randomUUID().toString(), bucket, actual, LocalDate.now(), note.trim().take(200)
        ))
        recordSnapshots()
    }

    fun setEmergencyFundBalance(value: Double) {
        captureUndo()
        setSavingsBalanceInternal(SavingsBucketType.EMERGENCY, value.coerceAtLeast(0.0))
        recordSnapshots()
    }

    fun updateInvestmentDetails(id: String, tags: Set<InvestmentTag>, note: String) {
        if (investments().none { it.id == id }) return
        captureUndo()
        writeInvestments(investments().map { if (it.id == id) it.copy(tags = tags, note = note.trim().take(500)) else it })
    }

    fun updateInvestmentTracking(id: String, priceSymbol: String, marketHashName: String = "", cs2AssetType: Cs2AssetType? = null) {
        if (investments().none { it.id == id }) return
        captureUndo()
        writeInvestments(investments().map {
            if (it.id == id) it.copy(
                priceSymbol = priceSymbol.trim().uppercase(),
                marketHashName = marketHashName.trim(),
                cs2AssetType = cs2AssetType ?: it.cs2AssetType,
            ) else it
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
        if (close <= 0.0 || investments().none { it.id == holdingId }) return
        captureUndo()
        recordInvestmentPriceInternal(holdingId, close, currency, symbol, source, date)
        recordSnapshots()
    }

    fun recordAutomatedInvestmentPrice(
        holdingId: String,
        close: Double,
        currency: String,
        symbol: String,
        source: String,
        date: LocalDate = LocalDate.now(),
    ) {
        if (close <= 0.0 || investments().none { it.id == holdingId }) return
        recordInvestmentPriceInternal(holdingId, close, currency, symbol, source, date)
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
        val keepFont = fontChoice()
        val keepAutomation = autoRecurringEnabled()
        captureUndo()
        val undo = prefs.getString("undo_snapshot", null)
        val editor = prefs.edit().clear()
            .putBoolean("legacy_demo_removed_v3", true)
            .putBoolean("recurring_ledger_migrated_v1", true)
            .putBoolean("v07_migrated", true)
            .putBoolean("app_lock_enabled", keepLock)
            .putString("app_font_choice", keepFont.name)
            .putBoolean("auto_recurring_enabled", keepAutomation)
        if (undo != null) editor.putString("undo_snapshot", undo)
        editor.apply()
    }

    private fun cashBalance(): Double = prefs.getString("cash_balance", null)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun balanceKey(bucket: SavingsBucketType): String = when (bucket) {
        SavingsBucketType.EMERGENCY -> "emergency_fund_balance"
        SavingsBucketType.CRASH_RESERVE -> "crash_reserve_balance"
        SavingsBucketType.GENERAL -> "general_savings_balance"
    }

    private fun targetKey(bucket: SavingsBucketType): String = when (bucket) {
        SavingsBucketType.EMERGENCY -> "emergency_fund_target"
        SavingsBucketType.CRASH_RESERVE -> "crash_reserve_target"
        SavingsBucketType.GENERAL -> "general_savings_target"
    }

    private fun savingsBalance(bucket: SavingsBucketType): Double =
        prefs.getString(balanceKey(bucket), null)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun savingsTarget(bucket: SavingsBucketType): Double = when (bucket) {
        SavingsBucketType.EMERGENCY -> prefs.getString(targetKey(bucket), null)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 3_500.0
        SavingsBucketType.CRASH_RESERVE -> prefs.getString(targetKey(bucket), null)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 5_000.0
        SavingsBucketType.GENERAL -> 0.0
    }

    private fun setCashBalanceInternal(value: Double) {
        prefs.edit().putString("cash_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    private fun setSavingsBalanceInternal(bucket: SavingsBucketType, value: Double) {
        prefs.edit().putString(balanceKey(bucket), value.coerceAtLeast(0.0).toString()).apply()
    }

    private fun appendInvestmentTransaction(
        holdingId: String,
        amount: Double,
        source: InvestmentEntrySource,
        referenceId: String = "",
        units: Double = 0.0,
        unitPrice: Double = 0.0,
        date: LocalDate = LocalDate.now(),
    ) {
        writeInvestmentTransactions(investmentTransactions() + InvestmentTransaction(
            UUID.randomUUID().toString(), holdingId, amount, date, source, referenceId,
            units.coerceAtLeast(0.0), unitPrice.coerceAtLeast(0.0)
        ))
    }

    private fun recordInvestmentPriceInternal(
        holdingId: String,
        close: Double,
        currency: String,
        symbol: String,
        source: String,
        date: LocalDate,
    ) {
        val holdings = investments()
        val holding = holdings.firstOrNull { it.id == holdingId } ?: return
        writeInvestmentPrices(
            investmentPrices().filterNot { it.holdingId == holdingId && it.date == date } +
                InvestmentPricePoint(holdingId, date, close, currency.trim().uppercase(), symbol.trim().uppercase())
        )
        writeInvestments(holdings.map {
            if (it.id == holdingId) it.copy(
                priceSymbol = symbol.trim().uppercase().ifBlank { holding.priceSymbol },
                priceCurrency = currency.trim().uppercase().ifBlank { holding.priceCurrency },
                priceSource = source.trim(),
                lastPriceRefreshMillis = System.currentTimeMillis(),
            ) else it
        })
    }

    private fun writeExpenses(rows: List<Expense>) = writeArray("expenses", rows) { row ->
        JSONObject().put("id", row.id).put("category", row.category.name).put("amount", row.amount)
            .put("date", row.date.toString()).put("note", row.note)
    }

    private fun writeBudgets(rows: List<Budget>) = writeArray("budgets", rows) { row ->
        JSONObject().put("category", row.category.name).put("monthlyLimit", row.monthlyLimit)
    }

    private fun writePayments(rows: List<MonthlyPayment>) = writeArray("payments", rows) { row ->
        JSONObject().put("id", row.id).put("category", row.category.name).put("name", row.name)
            .put("amount", row.amount).put("dayOfMonth", row.dayOfMonth)
            .put("lastPaidMonth", row.lastPaidMonth?.toString() ?: "").put("enabled", row.enabled)
    }

    private fun writeIncomes(rows: List<RecurringIncome>) = writeArray("incomes", rows) { row ->
        JSONObject().put("id", row.id).put("name", row.name).put("amount", row.amount)
            .put("dayOfMonth", row.dayOfMonth).put("glyph", row.glyph)
            .put("lastReceivedMonth", row.lastReceivedMonth?.toString() ?: "")
            .put("budgetMonthOffset", row.budgetMonthOffset).put("enabled", row.enabled)
    }

    private fun writeInvestments(rows: List<InvestmentHolding>) = writeArray("investments", rows) { row ->
        val tags = JSONArray().apply { row.tags.sortedBy { it.ordinal }.forEach { put(it.name) } }
        JSONObject().put("id", row.id).put("name", row.name).put("symbol", row.symbol)
            .put("kind", row.kind.name).put("amount", row.amount).put("isin", row.isin)
            .put("figi", row.figi).put("exchange", row.exchange).put("priceSymbol", row.priceSymbol)
            .put("priceCurrency", row.priceCurrency).put("priceSource", row.priceSource)
            .put("lastPriceRefreshMillis", row.lastPriceRefreshMillis).put("tags", tags).put("note", row.note)
            .put("marketHashName", row.marketHashName).put("cs2AssetType", row.cs2AssetType.name)
    }

    private fun writeInvestmentTransactions(rows: List<InvestmentTransaction>) = writeArray("investment_transactions", rows) { row ->
        JSONObject().put("id", row.id).put("holdingId", row.holdingId).put("amount", row.amount)
            .put("date", row.date.toString()).put("source", row.source.name).put("referenceId", row.referenceId)
            .put("units", row.units).put("unitPrice", row.unitPrice)
    }

    private fun writeInvestmentPrices(rows: List<InvestmentPricePoint>) = writeArray("investment_prices", rows) { row ->
        JSONObject().put("holdingId", row.holdingId).put("date", row.date.toString()).put("close", row.close)
            .put("currency", row.currency).put("symbol", row.symbol)
    }

    private fun writeRecurringInvestments(rows: List<RecurringInvestment>) = writeArray("recurring_investments", rows) { row ->
        JSONObject().put("id", row.id).put("holdingId", row.holdingId).put("amount", row.amount)
            .put("dayOfMonth", row.dayOfMonth).put("lastAppliedMonth", row.lastAppliedMonth?.toString() ?: "")
            .put("enabled", row.enabled)
    }

    private fun writeLedger(rows: List<LedgerEntry>) = writeArray("ledger", rows) { row ->
        JSONObject().put("id", row.id).put("type", row.type.name).put("referenceId", row.referenceId)
            .put("name", row.name).put("amount", row.amount).put("date", row.date.toString())
            .put("budgetMonth", row.budgetMonth?.toString() ?: "")
    }

    private fun writeSavingsTransfers(rows: List<SavingsTransfer>) = writeArray("savings_transfers", rows) { row ->
        JSONObject().put("id", row.id).put("bucket", row.bucket.name).put("amount", row.amount)
            .put("date", row.date.toString()).put("note", row.note)
    }

    private fun recordSnapshots() {
        val investmentTotal = investmentMarketTotal()
        val savings = SavingsBucketType.entries.sumOf(::savingsBalance)
        appendSnapshot("investment_history", investmentTotal)
        appendSnapshot("balance_history", cashBalance() + savings + investmentTotal)
    }

    private fun investmentMarketTotal(): Double {
        val transactions = investmentTransactions()
        val prices = investmentPrices()
        return investments().sumOf { holding ->
            val tx = transactions.filter { it.holdingId == holding.id }
            val latest = prices.filter { it.holdingId == holding.id }.maxByOrNull { it.date }?.close
            if (latest != null && latest > 0.0) {
                val pricedUnits = tx.filter { it.units > 0.0 }.sumOf { it.units }
                val unpricedCost = tx.filter { it.units <= 0.0 }.sumOf { it.amount }
                if (pricedUnits > 0.0) pricedUnits * latest + unpricedCost else holding.amount
            } else holding.amount
        }
    }

    private fun appendSnapshot(key: String, value: Double) {
        val now = System.currentTimeMillis()
        val rows = snapshots(key).toMutableList()
        val last = rows.lastOrNull()
        if (last != null && now - last.atMillis < 60_000L) rows[rows.lastIndex] = ValueSnapshot(now, value)
        else rows += ValueSnapshot(now, value)
        val array = JSONArray()
        rows.takeLast(1000).forEach { array.put(JSONObject().put("atMillis", it.atMillis).put("value", it.value)) }
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

    private fun migrateV07Once() {
        if (prefs.getBoolean("v07_migrated", false)) return
        if (!prefs.contains("emergency_fund_target")) prefs.edit().putString("emergency_fund_target", "3500.0").apply()
        if (!prefs.contains("crash_reserve_target")) prefs.edit().putString("crash_reserve_target", "5000.0").apply()
        if (!prefs.contains("app_font_choice")) prefs.edit().putString("app_font_choice", AppFontChoice.PIXELIFY.name).apply()
        if (!prefs.contains("auto_recurring_enabled")) prefs.edit().putBoolean("auto_recurring_enabled", true).apply()
        prefs.edit().putBoolean("v07_migrated", true).apply()
    }

    private fun migrateRecurringLedgerOnce() {
        if (prefs.getBoolean("recurring_ledger_migrated_v1", false)) return
        val now = YearMonth.now()
        val entries = ledger().toMutableList()
        payments().filter { it.lastPaidMonth == now }.forEach { row ->
            val marker = "payment:${row.id}:$now"
            if (entries.none { it.referenceId == marker }) entries += LedgerEntry(
                UUID.randomUUID().toString(), LedgerEntryType.PAYMENT, marker, row.name, row.amount, row.dueDateFor(now), now
            )
        }
        incomes().filter { it.lastReceivedMonth == now }.forEach { row ->
            val marker = "income:${row.id}:$now"
            if (entries.none { it.referenceId == marker }) entries += LedgerEntry(
                UUID.randomUUID().toString(), LedgerEntryType.INCOME, marker, row.name, row.amount,
                row.dueDateFor(now), row.budgetMonthFor(now)
            )
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
        prefs.edit().remove("monthly_income").remove("monthly_investment")
            .putBoolean("legacy_demo_removed_v3", true).apply()
    }
}
