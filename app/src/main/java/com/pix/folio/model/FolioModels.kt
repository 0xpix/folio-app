package com.pix.folio.model

import java.time.LocalDate

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
    SALARY("Salary", "💼"),
    OTHER("Other", "•••"),
}

data class MonthlyPayment(
    val id: String,
    val category: PaymentCategory,
    val name: String,
    val amount: Double,
    val dueDate: LocalDate,
    val paid: Boolean,
)

data class InvestmentHolding(
    val id: String,
    val name: String,
    val symbol: String,
    val group: String,
    val amount: Double,
)

data class InvestmentGroup(
    val name: String,
    val amount: Double,
)

data class FolioSummary(
    val cashBalance: Double,
    val monthlyIncome: Double,
    val monthlyInvestmentContribution: Double,
    val investments: List<InvestmentHolding>,
    val expenses: List<Expense>,
    val payments: List<MonthlyPayment>,
) {
    val investmentTotal: Double get() = investments.sumOf { it.amount }
    val totalBalance: Double get() = cashBalance + investmentTotal
    val monthlyExpenses: Double get() = expenses.sumOf { it.amount }
    val paidPayments: Double get() = payments.filter { it.paid }.sumOf { it.amount }
    val monthlyChange: Double
        get() = monthlyIncome - monthlyExpenses - paidPayments - monthlyInvestmentContribution

    val allocation: List<InvestmentGroup>
        get() = investments
            .groupBy { it.group }
            .map { (name, rows) -> InvestmentGroup(name, rows.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
}
