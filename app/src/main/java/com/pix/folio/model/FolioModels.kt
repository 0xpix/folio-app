package com.pix.folio.model

import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max

enum class ExpenseCategory(val label: String, val glyph: String) {
    FOOD("Food", "🍴"),
    HOME("Home", "🏠"),
    TRANSPORT("Transport", "🚗"),
    SHOPPING("Shopping", "🛍️"),
    HEALTH("Health", "💪"),
    LEISURE("Leisure", "🎮"),
    SUBSCRIPTIONS("Subscriptions", "🎬"),
    OTHER("Other", "•••"),
}

data class Expense(
    val id: String,
    val category: ExpenseCategory,
    val amount: Double,
    val date: LocalDate,
    val note: String = "",
)

data class Budget(val category: ExpenseCategory, val monthlyLimit: Double)

enum class PaymentCategory(val label: String, val glyph: String) {
    HOME("Home", "🏠"),
    PHONE("Phone", "📱"),
    GYM("Gym", "🏋️"),
    STREAMING("Streaming", "📺"),
    ELECTRICITY("Electricity", "💡"),
    INTERNET("Internet", "🌐"),
    TRANSPORT("Transport", "🚆"),
    INSURANCE("Insurance", "◇"),
    OTHER("Other", "•••"),
}

data class MonthlyPayment(
    val id: String,
    val category: PaymentCategory,
    val name: String,
    val amount: Double,
    val dayOfMonth: Int,
    val lastPaidMonth: YearMonth? = null,
    val enabled: Boolean = true,
) {
    fun dueDateFor(month: YearMonth): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    val dueDate: LocalDate get() = dueDateFor(YearMonth.now())
    val paid: Boolean get() = lastPaidMonth == YearMonth.now()
}

data class RecurringIncome(
    val id: String,
    val name: String,
    val amount: Double,
    val dayOfMonth: Int,
    val glyph: String = "💼",
    val lastReceivedMonth: YearMonth? = null,
    /** 0 = use in the month received, 1 = use for next month's budget. */
    val budgetMonthOffset: Int = 0,
    val enabled: Boolean = true,
) {
    fun dueDateFor(month: YearMonth): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    val dueDate: LocalDate get() = dueDateFor(YearMonth.now())
    val received: Boolean get() = lastReceivedMonth == YearMonth.now()
    fun budgetMonthFor(receiveMonth: YearMonth): YearMonth = receiveMonth.plusMonths(budgetMonthOffset.toLong())
}

enum class InvestmentKind(val label: String, val group: String, val glyph: String) {
    ETF("ETF", "ETFs", "▦"),
    STOCK("Stock", "Stocks", "↑"),
    FUND("Fund", "Funds", "◫"),
    BOND("Bond", "Bonds", "≡"),
    INDEX("Index", "Indexes", "⌁"),
    CS2("CS2", "CS2", "◇"),
    OTHER("Other", "Others", "•"),
}

enum class Cs2AssetType(val label: String) {
    CASE("Case"),
    STICKER("Sticker"),
    SKIN("Skin"),
    OTHER("Other"),
}

enum class InvestmentTag(val label: String) {
    LONG_TERM("Long term"),
    SPECULATIVE("Speculative"),
    RETIREMENT("Retirement"),
    CS2_CASE("CS2 case"),
    HIGH_RISK("High risk"),
}

data class InvestmentHolding(
    val id: String,
    val name: String,
    val symbol: String,
    val kind: InvestmentKind,
    /** Money contributed / cost basis. Market value is derived from units + latest price when possible. */
    val amount: Double,
    val isin: String = "",
    val figi: String = "",
    val exchange: String = "",
    val priceSymbol: String = "",
    val priceCurrency: String = "",
    val priceSource: String = "",
    val lastPriceRefreshMillis: Long = 0L,
    val tags: Set<InvestmentTag> = emptySet(),
    val note: String = "",
    val marketHashName: String = "",
    val cs2AssetType: Cs2AssetType = Cs2AssetType.OTHER,
) {
    val group: String get() = kind.group
    val canAutoPrice: Boolean get() = if (kind == InvestmentKind.CS2) marketHashName.isNotBlank() else priceSymbol.isNotBlank() || symbol.isNotBlank()
}

enum class InvestmentEntrySource { INITIAL, MANUAL, RECURRING }

data class InvestmentTransaction(
    val id: String,
    val holdingId: String,
    val amount: Double,
    val date: LocalDate,
    val source: InvestmentEntrySource = InvestmentEntrySource.MANUAL,
    val referenceId: String = "",
    val units: Double = 0.0,
    val unitPrice: Double = 0.0,
)

data class InvestmentPricePoint(
    val holdingId: String,
    val date: LocalDate,
    val close: Double,
    val currency: String = "",
    val symbol: String = "",
)

data class RecurringInvestment(
    val id: String,
    val holdingId: String,
    val amount: Double,
    val dayOfMonth: Int,
    val lastAppliedMonth: YearMonth? = null,
    val enabled: Boolean = true,
) {
    fun dueDateFor(month: YearMonth): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    val dueDate: LocalDate get() = dueDateFor(YearMonth.now())
    val applied: Boolean get() = lastAppliedMonth == YearMonth.now()
}

enum class LedgerEntryType { INCOME, PAYMENT }

data class LedgerEntry(
    val id: String,
    val type: LedgerEntryType,
    val referenceId: String,
    val name: String,
    val amount: Double,
    val date: LocalDate,
    /** Budget attribution can differ from transaction date (e.g. Sep salary funds October). */
    val budgetMonth: YearMonth? = null,
)

enum class SavingsBucketType(val label: String, val glyph: String) {
    EMERGENCY("Emergency", "◇"),
    CRASH_RESERVE("Crash reserve", "↓"),
    GENERAL("Savings", "○"),
}

data class SavingsTransfer(
    val id: String,
    val bucket: SavingsBucketType,
    /** Positive moves cash into savings; negative returns savings to cash. */
    val amount: Double,
    val date: LocalDate,
    val note: String = "",
)

enum class AppFontChoice(val label: String) {
    PIXELIFY("Pixify"),
    SYSTEM("System"),
    MONO("Mono"),
    SERIF("Serif"),
}

enum class TimelineEntryType { INCOME, EXPENSE, PAYMENT, INVESTMENT }

data class TimelineEntry(
    val id: String,
    val type: TimelineEntryType,
    val name: String,
    val amount: Double,
    val date: LocalDate,
    val detail: String = "",
)

data class InvestmentGroup(val name: String, val amount: Double)
data class ValueSnapshot(val atMillis: Long, val value: Double)

data class MonthOverview(
    val month: YearMonth,
    val income: Double,
    val expenses: Double,
    val payments: Double,
    val invested: Double,
) {
    val left: Double get() = income - expenses - payments - invested
    val outflow: Double get() = expenses + payments
}

data class MonthPlan(
    val month: YearMonth,
    val expectedIncome: Double,
    val fixedPayments: Double,
    val recurringInvestments: Double,
    val manualInvestments: Double,
    val expenses: Double,
    val savingsAllocated: Double,
) {
    val plannedInvestments: Double get() = recurringInvestments + manualInvestments
    val committed: Double get() = fixedPayments + plannedInvestments + expenses + savingsAllocated
    val projectedLeft: Double get() = expectedIncome - committed
}

data class AnnualReview(
    val year: Int,
    val income: Double,
    val expenses: Double,
    val payments: Double,
    val invested: Double,
    val netWorthStart: Double,
    val netWorthEnd: Double,
    val activeMonths: Int,
    val contributionCount: Int,
) {
    val spent: Double get() = expenses + payments
    val left: Double get() = income - spent - invested
    val netWorthChange: Double get() = netWorthEnd - netWorthStart
}

data class ProjectionPoint(val year: Int, val value: Double, val contributed: Double)
data class Milestone(val amount: Double, val reached: Boolean, val progress: Double)

data class FolioSummary(
    val cashBalance: Double,
    val emergencyFundBalance: Double,
    val investments: List<InvestmentHolding>,
    val investmentTransactions: List<InvestmentTransaction>,
    val investmentPrices: List<InvestmentPricePoint>,
    val recurringInvestments: List<RecurringInvestment>,
    val expenses: List<Expense>,
    val budgets: List<Budget>,
    val payments: List<MonthlyPayment>,
    val incomes: List<RecurringIncome>,
    val ledger: List<LedgerEntry>,
    val balanceHistory: List<ValueSnapshot>,
    val investmentHistory: List<ValueSnapshot>,
    val crashReserveBalance: Double = 0.0,
    val generalSavingsBalance: Double = 0.0,
    val emergencyFundTarget: Double = 3_500.0,
    val crashReserveTarget: Double = 5_000.0,
    val savingsTransfers: List<SavingsTransfer> = emptyList(),
) {
    val currentMonthExpenses: List<Expense>
        get() = expenses.filter { YearMonth.from(it.date) == YearMonth.now() }

    val portfolioCostBasis: Double get() = investments.sumOf { it.amount }
    val investmentTotal: Double get() = investments.sumOf(::marketValueFor)
    val portfolioGain: Double get() = investmentTotal - portfolioCostBasis
    val portfolioGainPct: Double get() = if (portfolioCostBasis > 0.0) portfolioGain / portfolioCostBasis * 100.0 else 0.0
    val totalSavings: Double get() = emergencyFundBalance + crashReserveBalance + generalSavingsBalance
    /** Full net worth: spendable cash + ring-fenced savings + investments (including CS2 assets). */
    val totalBalance: Double get() = cashBalance + totalSavings + investmentTotal
    val monthlyExpenses: Double get() = currentMonthExpenses.sumOf { it.amount }
    val monthlyIncome: Double get() = monthOverview(YearMonth.now()).income
    val receivedIncome: Double get() = monthlyIncome
    val paidPayments: Double get() = monthOverview(YearMonth.now()).payments
    val scheduledPayments: Double get() = payments.filter { it.enabled }.sumOf { it.amount }
    val monthlyInvested: Double get() = monthOverview(YearMonth.now()).invested
    val monthlyChange: Double get() = monthlyIncome - monthlyExpenses - paidPayments
    val emergencyFundProgress: Double get() = if (emergencyFundTarget > 0.0) (emergencyFundBalance / emergencyFundTarget).coerceIn(0.0, 1.0) else 0.0
    val crashReserveProgress: Double get() = if (crashReserveTarget > 0.0) (crashReserveBalance / crashReserveTarget).coerceIn(0.0, 1.0) else 0.0
    val savedThisMonth: Double get() = savingsTransfers.filter { YearMonth.from(it.date) == YearMonth.now() }.sumOf { it.amount }.coerceAtLeast(0.0)

    val allocation: List<InvestmentGroup>
        get() = investments.groupBy { it.group }
            .map { (name, rows) -> InvestmentGroup(name, rows.sumOf(::marketValueFor)) }
            .sortedByDescending { it.amount }

    val investmentContributionStreak: Int
        get() {
            val months = investmentTransactions.map { YearMonth.from(it.date) }.toSet()
            if (months.isEmpty()) return 0
            var cursor = if (YearMonth.now() in months) YearMonth.now() else YearMonth.now().minusMonths(1)
            var streak = 0
            while (cursor in months) {
                streak += 1
                cursor = cursor.minusMonths(1)
            }
            return streak
        }

    val averageMonthlyOutflow: Double
        get() {
            val months = buildSet {
                expenses.forEach { add(YearMonth.from(it.date)) }
                ledger.filter { it.type == LedgerEntryType.PAYMENT }.forEach { add(it.budgetMonth ?: YearMonth.from(it.date)) }
            }.sortedDescending().take(3)
            if (months.isEmpty()) return scheduledPayments
            return months.map { monthOverview(it).outflow }.average()
        }

    val emergencyFundMonths: Double
        get() = if (averageMonthlyOutflow > 0.0) emergencyFundBalance / averageMonthlyOutflow else 0.0

    val availableReviewYears: List<Int>
        get() = buildSet {
            expenses.forEach { add(it.date.year) }
            ledger.forEach { add((it.budgetMonth ?: YearMonth.from(it.date)).year) }
            investmentTransactions.forEach { add(it.date.year) }
            balanceHistory.forEach { add(Instant.ofEpochMilli(it.atMillis).atZone(ZoneId.systemDefault()).year) }
            if (isEmpty()) add(Year.now().value)
        }.sortedDescending()

    val automaticMilestones: List<Milestone>
        get() {
            val levels = listOf(1_000.0, 5_000.0, 10_000.0, 25_000.0, 50_000.0, 100_000.0, 250_000.0, 500_000.0, 1_000_000.0)
            return levels.map { level -> Milestone(level, totalBalance >= level, (totalBalance / level).coerceIn(0.0, 1.0)) }
        }

    fun budgetFor(category: ExpenseCategory): Budget? = budgets.firstOrNull { it.category == category }

    fun spentFor(category: ExpenseCategory, month: YearMonth = YearMonth.now()): Double =
        expenses.filter { it.category == category && YearMonth.from(it.date) == month }.sumOf { it.amount }

    fun monthOverview(month: YearMonth): MonthOverview {
        val monthExpenses = expenses.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
        val monthIncome = ledger.filter {
            it.type == LedgerEntryType.INCOME && (it.budgetMonth ?: YearMonth.from(it.date)) == month
        }.sumOf { it.amount }
        val monthPayments = ledger.filter {
            it.type == LedgerEntryType.PAYMENT && (it.budgetMonth ?: YearMonth.from(it.date)) == month
        }.sumOf { it.amount }
        val monthInvested = investmentTransactions.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
        return MonthOverview(month, monthIncome, monthExpenses, monthPayments, monthInvested)
    }

    /**
     * A forward-looking budget month. Recurring salary can fund a later month while keeping its real receive date.
     */
    fun moneyPlan(month: YearMonth): MonthPlan {
        val plannedRecurringIncome = incomes.filter { income ->
            income.enabled && income.budgetMonthFor(month.minusMonths(income.budgetMonthOffset.toLong())) == month
        }.sumOf { it.amount }
        val actualIncome = ledger.filter {
            it.type == LedgerEntryType.INCOME && (it.budgetMonth ?: YearMonth.from(it.date)) == month
        }.sumOf { it.amount }
        val expectedIncome = max(plannedRecurringIncome, actualIncome)
        val fixedPayments = payments.filter { it.enabled }.sumOf { it.amount }
        val recurring = recurringInvestments.filter { it.enabled }.sumOf { it.amount }
        val manualInvestments = investmentTransactions.filter {
            YearMonth.from(it.date) == month && it.source != InvestmentEntrySource.RECURRING
        }.sumOf { it.amount }
        val monthExpenses = expenses.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
        val saved = savingsTransfers.filter { YearMonth.from(it.date) == month && it.amount > 0.0 }.sumOf { it.amount }
        return MonthPlan(month, expectedIncome, fixedPayments, recurring, manualInvestments, monthExpenses, saved)
    }

    fun annualReview(year: Int): AnnualReview {
        val overviews = (1..12).map { monthOverview(YearMonth.of(year, it)) }
        val activeMonths = overviews.count { it.income != 0.0 || it.expenses != 0.0 || it.payments != 0.0 || it.invested != 0.0 }
        val zone = ZoneId.systemDefault()
        val startMillis = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val beforeStart = balanceHistory.lastOrNull { it.atMillis < startMillis }?.value
        val firstInYear = balanceHistory.firstOrNull { it.atMillis in startMillis..endMillis }?.value
        val lastInYear = balanceHistory.lastOrNull { it.atMillis in startMillis..endMillis }?.value
        val start = beforeStart ?: firstInYear ?: 0.0
        val end = lastInYear ?: if (year == Year.now().value) totalBalance else start
        return AnnualReview(
            year = year,
            income = overviews.sumOf { it.income },
            expenses = overviews.sumOf { it.expenses },
            payments = overviews.sumOf { it.payments },
            invested = overviews.sumOf { it.invested },
            netWorthStart = start,
            netWorthEnd = end,
            activeMonths = activeMonths,
            contributionCount = investmentTransactions.count { it.date.year == year },
        )
    }

    fun projectedGrowth(monthlyContribution: Double, annualRatePct: Double, years: Int): List<ProjectionPoint> {
        val safeYears = years.coerceIn(1, 50)
        val rate = (annualRatePct / 100.0).coerceIn(-0.99, 1.0)
        val monthlyRate = Math.pow(1.0 + rate, 1.0 / 12.0) - 1.0
        var value = investmentTotal
        var contributed = portfolioCostBasis
        val result = mutableListOf(ProjectionPoint(0, value, contributed))
        for (month in 1..(safeYears * 12)) {
            value *= (1.0 + monthlyRate)
            value += monthlyContribution
            contributed += monthlyContribution
            if (month % 12 == 0) result += ProjectionPoint(month / 12, value, contributed)
        }
        return result
    }

    fun priceHistoryFor(holdingId: String): List<InvestmentPricePoint> =
        investmentPrices.filter { it.holdingId == holdingId }.sortedBy { it.date }

    fun transactionsFor(holdingId: String): List<InvestmentTransaction> =
        investmentTransactions.filter { it.holdingId == holdingId }.sortedBy { it.date }

    fun unitsFor(holdingId: String): Double = transactionsFor(holdingId).sumOf { it.units }

    fun latestPriceFor(holdingId: String): Double? = priceHistoryFor(holdingId).lastOrNull()?.close?.takeIf { it > 0.0 }

    fun marketValueFor(holding: InvestmentHolding): Double {
        val transactions = transactionsFor(holding.id)
        val latestPrice = latestPriceFor(holding.id) ?: return holding.amount
        val pricedUnits = transactions.filter { it.units > 0.0 }.sumOf { it.units }
        val unpricedCost = transactions.filter { it.units <= 0.0 }.sumOf { it.amount }
        return if (pricedUnits > 0.0) pricedUnits * latestPrice + unpricedCost else holding.amount
    }

    fun gainFor(holding: InvestmentHolding): Double = marketValueFor(holding) - holding.amount

    fun gainPctFor(holding: InvestmentHolding): Double =
        if (holding.amount > 0.0) gainFor(holding) / holding.amount * 100.0 else 0.0

    fun transactionTimeline(month: YearMonth? = null): List<TimelineEntry> {
        val holdingNames = investments.associate { it.id to it.name }
        val rows = buildList {
            expenses.forEach { expense ->
                add(TimelineEntry("expense:${expense.id}", TimelineEntryType.EXPENSE, expense.note.ifBlank { expense.category.label }, -expense.amount, expense.date, expense.category.label))
            }
            ledger.forEach { entry ->
                val attributedMonth = entry.budgetMonth ?: YearMonth.from(entry.date)
                if (month == null || attributedMonth == month) {
                    add(
                        TimelineEntry(
                            id = "ledger:${entry.id}",
                            type = if (entry.type == LedgerEntryType.INCOME) TimelineEntryType.INCOME else TimelineEntryType.PAYMENT,
                            name = entry.name,
                            amount = if (entry.type == LedgerEntryType.INCOME) entry.amount else -entry.amount,
                            date = entry.date,
                            detail = if (entry.type == LedgerEntryType.INCOME && entry.budgetMonth != null && entry.budgetMonth != YearMonth.from(entry.date)) {
                                "Income · funds ${entry.budgetMonth}"
                            } else if (entry.type == LedgerEntryType.INCOME) "Income" else "Payment",
                        )
                    )
                }
            }
            investmentTransactions.forEach { tx ->
                if (month == null || YearMonth.from(tx.date) == month) {
                    add(
                        TimelineEntry(
                            id = "investment:${tx.id}",
                            type = TimelineEntryType.INVESTMENT,
                            name = holdingNames[tx.holdingId] ?: "Investment",
                            amount = -tx.amount,
                            date = tx.date,
                            detail = when (tx.source) {
                                InvestmentEntrySource.INITIAL -> "Initial purchase"
                                InvestmentEntrySource.RECURRING -> "Recurring investment"
                                InvestmentEntrySource.MANUAL -> "Investment contribution"
                            },
                        )
                    )
                }
            }
        }
        return rows.filter { row -> month == null || row.type == TimelineEntryType.INCOME || row.type == TimelineEntryType.PAYMENT || YearMonth.from(row.date) == month }
            .sortedWith(compareByDescending<TimelineEntry> { it.date }.thenByDescending { it.id })
    }
}
