package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentEntrySource
import com.pix.folio.model.LedgerEntryType
import com.pix.folio.model.SavingsBucketType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.math.max

/** Folio's finance timeline starts with the September 2026 setup month. */
val FolioStartMonth: YearMonth = YearMonth.of(2026, 9)

data class RecurringSavingsRule(
    val id: String,
    val bucket: SavingsBucketType,
    val amount: Double,
    val dayOfMonth: Int,
    val lastAppliedMonth: YearMonth? = null,
    val enabled: Boolean = true,
) {
    fun dueDateFor(month: YearMonth): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    fun appliedFor(month: YearMonth): Boolean = lastAppliedMonth == month
}

data class BudgetEnvelope(
    val month: YearMonth,
    val expectedIncome: Double,
    val receivedIncome: Double,
    val fixedPayments: Double,
    val recurringInvestments: Double,
    val manualInvestments: Double,
    val plannedVariableSpending: Double,
    val actualExpenses: Double,
    val plannedSavings: Double,
    val actualSavings: Double,
    val actualPayments: Double,
    val actualInvested: Double,
) {
    val plannedInvestments: Double get() = recurringInvestments + manualInvestments
    val savingsCommitment: Double get() = max(plannedSavings, actualSavings)
    val committed: Double
        get() = fixedPayments + plannedInvestments + plannedVariableSpending + savingsCommitment

    /** The envelope amount free to spend or allocate. It deliberately ignores prior-month cash. */
    val availableCash: Double get() = expectedIncome - committed

    /** What is physically left from income actually assigned to this month after real transactions. */
    val actualRemainder: Double
        get() = receivedIncome - actualPayments - actualInvested - actualExpenses - actualSavings
}

class MonthlyPlanStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("folio_monthly_plan_v1", Context.MODE_PRIVATE)
    private val key = "recurring_savings"

    fun recurringSavings(): List<RecurringSavingsRule> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val amount = json.optDouble("amount", 0.0)
                    if (amount <= 0.0) continue
                    add(
                        RecurringSavingsRule(
                            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                            bucket = runCatching {
                                SavingsBucketType.valueOf(json.optString("bucket"))
                            }.getOrDefault(SavingsBucketType.GENERAL),
                            amount = amount,
                            dayOfMonth = json.optInt("dayOfMonth", 1).coerceIn(1, 31),
                            lastAppliedMonth = json.optString("lastAppliedMonth")
                                .takeIf(String::isNotBlank)
                                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
                            enabled = json.optBoolean("enabled", true),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addRecurringSaving(bucket: SavingsBucketType, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0) return
        write(
            recurringSavings() + RecurringSavingsRule(
                id = UUID.randomUUID().toString(),
                bucket = bucket,
                amount = amount,
                dayOfMonth = dayOfMonth.coerceIn(1, 31),
            )
        )
    }

    fun updateRecurringSaving(id: String, bucket: SavingsBucketType, amount: Double, dayOfMonth: Int) {
        if (amount <= 0.0) return
        write(
            recurringSavings().map {
                if (it.id == id) it.copy(
                    bucket = bucket,
                    amount = amount,
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                ) else it
            }
        )
    }

    fun removeRecurringSaving(id: String) {
        write(recurringSavings().filterNot { it.id == id })
    }

    fun envelope(summary: FolioSummary, month: YearMonth): BudgetEnvelope {
        val base = summary.moneyPlan(month)
        val overview = summary.monthOverview(month)
        val variablePlan = ExpenseCategory.entries.sumOf { category ->
            max(summary.budgetFor(category)?.monthlyLimit ?: 0.0, summary.spentFor(category, month))
        }
        val plannedSavings = recurringSavings().filter { it.enabled }.sumOf { it.amount }
        val actualSavings = summary.savingsTransfers
            .filter { YearMonth.from(it.date) == month && it.amount > 0.0 }
            .sumOf { it.amount }

        return BudgetEnvelope(
            month = month,
            expectedIncome = base.expectedIncome,
            receivedIncome = overview.income,
            fixedPayments = base.fixedPayments,
            recurringInvestments = base.recurringInvestments,
            manualInvestments = base.manualInvestments,
            plannedVariableSpending = variablePlan,
            actualExpenses = overview.expenses,
            plannedSavings = plannedSavings,
            actualSavings = actualSavings,
            actualPayments = overview.payments,
            actualInvested = overview.invested,
        )
    }

    fun suggestedBudgetMonth(summary: FolioSummary, today: LocalDate = LocalDate.now()): YearMonth {
        val current = maxMonth(YearMonth.from(today), FolioStartMonth)
        val currentEnvelope = envelope(summary, current)
        val next = current.plusMonths(1)
        return if (currentEnvelope.expectedIncome <= 0.0 && envelope(summary, next).expectedIncome > 0.0) next else current
    }

    /**
     * Real money assigned to [month] that has not yet been consumed by actual transactions.
     * Previous-month leftovers are intentionally not part of this number.
     */
    fun budgetCashRemaining(summary: FolioSummary, month: YearMonth): Double {
        val income = summary.ledger.filter {
            it.type == LedgerEntryType.INCOME && (it.budgetMonth ?: YearMonth.from(it.date)) == month
        }.sumOf { it.amount }
        val payments = summary.ledger.filter {
            it.type == LedgerEntryType.PAYMENT && (it.budgetMonth ?: YearMonth.from(it.date)) == month
        }.sumOf { it.amount }
        val invested = summary.investmentTransactions.filter {
            YearMonth.from(it.date) == month && it.source == InvestmentEntrySource.RECURRING
        }.sumOf { it.amount }
        val expenses = summary.expenses.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
        val savings = summary.savingsTransfers.filter {
            YearMonth.from(it.date) == month && it.amount > 0.0
        }.sumOf { it.amount }
        return (income - payments - invested - expenses - savings).coerceAtLeast(0.0)
    }

    fun applyDueSavings(store: FolioStore, today: LocalDate = LocalDate.now()): Int {
        val month = YearMonth.from(today)
        var applied = 0
        recurringSavings()
            .filter { rule ->
                rule.enabled && !rule.appliedFor(month) && !rule.dueDateFor(month).isAfter(today)
            }
            .forEach { rule ->
                val summary = store.summary()
                val availableForMonth = budgetCashRemaining(summary, month)
                if (availableForMonth + 0.005 < rule.amount || summary.cashBalance + 0.005 < rule.amount) return@forEach
                store.transferSavings(rule.bucket, rule.amount, "Automatic monthly savings")
                markApplied(rule.id, month)
                applied += 1
            }
        return applied
    }

    private fun markApplied(id: String, month: YearMonth) {
        write(recurringSavings().map { if (it.id == id) it.copy(lastAppliedMonth = month) else it })
    }

    private fun write(rows: List<RecurringSavingsRule>) {
        val array = JSONArray()
        rows.forEach { row ->
            array.put(
                JSONObject()
                    .put("id", row.id)
                    .put("bucket", row.bucket.name)
                    .put("amount", row.amount)
                    .put("dayOfMonth", row.dayOfMonth)
                    .put("lastAppliedMonth", row.lastAppliedMonth?.toString() ?: "")
                    .put("enabled", row.enabled)
            )
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun maxMonth(a: YearMonth, b: YearMonth): YearMonth = if (a < b) b else a
}
