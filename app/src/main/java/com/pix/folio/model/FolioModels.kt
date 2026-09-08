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
) {
    val group: String get() = kind.group
}

data class InvestmentGroup(
    val name: String,
    val amount: Double,
)

data class ValueSnapshot(
    val atMillis: Long,
    val value: Double,
)

data class FolioSummary(
    val cashBalance: Double,
    val investments: List<InvestmentHolding>,
    val expenses: List<Expense>,
    val payments: List<MonthlyPayment>,
    val incomes: List<RecurringIncome>,
    val balanceHistory: List<ValueSnapshot>,
    val investmentHistory: List<ValueSnapshot>,
) {
    val currentMonthExpenses: List<Expense>
        get() = expenses.filter { YearMonth.from(it.date) == YearMonth.now() }

    val investmentTotal: Double get() = investments.sumOf { it.amount }
    val totalBalance: Double get() = cashBalance + investmentTotal
    val monthlyExpenses: Double get() = currentMonthExpenses.sumOf { it.amount }
    val monthlyIncome: Double get() = incomes.sumOf { it.amount }
    val receivedIncome: Double get() = incomes.filter { it.received }.sumOf { it.amount }
    val paidPayments: Double get() = payments.filter { it.paid }.sumOf { it.amount }
    val scheduledPayments: Double get() = payments.sumOf { it.amount }
    val monthlyChange: Double get() = monthlyIncome - monthlyExpenses - paidPayments

    val allocation: List<InvestmentGroup>
        get() = investments
            .groupBy { it.group }
            .map { (name, rows) -> InvestmentGroup(name, rows.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
}
