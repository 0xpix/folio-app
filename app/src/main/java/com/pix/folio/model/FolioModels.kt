package com.pix.folio.model

import java.time.LocalDate
import java.time.YearMonth

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

data class Budget(
    val category: ExpenseCategory,
    val monthlyLimit: Double,
)

enum class PaymentCategory(val label: String, val glyph: String) {
    HOME("Home", "🏠"),
    PHONE("Phone", "📱"),
    GYM("Gym", "🏋️"),
    STREAMING("Streaming", "📺"),
    ELECTRICITY("Electricity", "💡"),
    INTERNET("Internet", "🌐"),
    OTHER("Other", "•••"),
}

data class MonthlyPayment(
    val id: String,
    val category: PaymentCategory,
    val name: String,
    val amount: Double,
    val dayOfMonth: Int,
    val lastPaidMonth: YearMonth? = null,
) {
    val dueDate: LocalDate
        get() {
            val month = YearMonth.now()
            return month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))
        }

    val paid: Boolean get() = lastPaidMonth == YearMonth.now()
}

data class RecurringIncome(
    val id: String,
    val name: String,
    val amount: Double,
    val dayOfMonth: Int,
    val glyph: String = "💼",
    val lastReceivedMonth: YearMonth? = null,
) {
    val dueDate: LocalDate
        get() {
            val month = YearMonth.now()
            return month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))
        }

    val received: Boolean get() = lastReceivedMonth == YearMonth.now()
}

enum class InvestmentKind(val label: String, val group: String, val glyph: String) {
    ETF("ETF", "ETFs", "▦"),
    STOCK("Stock", "Stocks", "↑"),
    FUND("Fund", "Funds", "◫"),
    OTHER("Other", "Others", "•"),
}

data class InvestmentHolding(
    val id: String,
    val name: String,
    val symbol: String,
    val kind: InvestmentKind,
    val amount: Double,
    val isin: String = "",
    val figi: String = "",
    val exchange: String = "",
) {
    val group: String get() = kind.group
}

enum class InvestmentEntrySource { MANUAL, RECURRING }

data class InvestmentTransaction(
    val id: String,
    val holdingId: String,
    val amount: Double,
    val date: LocalDate,
    val source: InvestmentEntrySource = InvestmentEntrySource.MANUAL,
    val referenceId: String = "",
)

data class RecurringInvestment(
    val id: String,
    val holdingId: String,
    val amount: Double,
    val dayOfMonth: Int,
    val lastAppliedMonth: YearMonth? = null,
) {
    val dueDate: LocalDate
        get() {
            val month = YearMonth.now()
            return month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))
        }

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
)

data class InvestmentGroup(
    val name: String,
    val amount: Double,
)

data class ValueSnapshot(
    val atMillis: Long,
    val value: Double,
)

data class MonthOverview(
    val month: YearMonth,
    val income: Double,
    val expenses: Double,
    val payments: Double,
    val invested: Double,
) {
    val left: Double get() = income - expenses - payments - invested
}

data class FolioSummary(
    val cashBalance: Double,
    val investments: List<InvestmentHolding>,
    val investmentTransactions: List<InvestmentTransaction>,
    val recurringInvestments: List<RecurringInvestment>,
    val expenses: List<Expense>,
    val budgets: List<Budget>,
    val payments: List<MonthlyPayment>,
    val incomes: List<RecurringIncome>,
    val ledger: List<LedgerEntry>,
    val balanceHistory: List<ValueSnapshot>,
    val investmentHistory: List<ValueSnapshot>,
) {
    val currentMonthExpenses: List<Expense>
        get() = expenses.filter { YearMonth.from(it.date) == YearMonth.now() }

    val investmentTotal: Double get() = investments.sumOf { it.amount }
    val totalBalance: Double get() = cashBalance + investmentTotal
    val monthlyExpenses: Double get() = currentMonthExpenses.sumOf { it.amount }
    val monthlyIncome: Double get() = monthOverview(YearMonth.now()).income
    val receivedIncome: Double get() = monthlyIncome
    val paidPayments: Double get() = monthOverview(YearMonth.now()).payments
    val scheduledPayments: Double get() = payments.sumOf { it.amount }
    val monthlyInvested: Double get() = monthOverview(YearMonth.now()).invested
    val monthlyChange: Double get() = monthlyIncome - monthlyExpenses - paidPayments

    val allocation: List<InvestmentGroup>
        get() = investments
            .groupBy { it.group }
            .map { (name, rows) -> InvestmentGroup(name, rows.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

    fun budgetFor(category: ExpenseCategory): Budget? = budgets.firstOrNull { it.category == category }

    fun spentFor(category: ExpenseCategory, month: YearMonth = YearMonth.now()): Double =
        expenses.filter { it.category == category && YearMonth.from(it.date) == month }.sumOf { it.amount }

    fun monthOverview(month: YearMonth): MonthOverview {
        val monthExpenses = expenses.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
        val monthIncome = ledger.filter { it.type == LedgerEntryType.INCOME && YearMonth.from(it.date) == month }.sumOf { it.amount }
        val monthPayments = ledger.filter { it.type == LedgerEntryType.PAYMENT && YearMonth.from(it.date) == month }.sumOf { it.amount }
        val monthInvested = investmentTransactions.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
        return MonthOverview(month, monthIncome, monthExpenses, monthPayments, monthInvested)
    }
}
