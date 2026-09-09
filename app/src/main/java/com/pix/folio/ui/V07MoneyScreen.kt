package com.pix.folio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.model.InvestmentHolding
import java.time.YearMonth

@Composable
internal fun V07MoneyScreen(vm: V07ViewModel) {
    val summary = vm.summary
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showExpense by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showAllocation by remember { mutableStateOf(false) }
    var showTargets by remember { mutableStateOf(false) }

    val plan = summary.moneyPlan(month)
    val overview = summary.monthOverview(month)
    val expenseRows = summary.expenses.filter { YearMonth.from(it.date) == month }.sortedByDescending { it.date }
    val monthIsCurrent = month == YearMonth.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MONEY", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (vm.autoRecurringEnabled) "Recurring flows run automatically" else "Recurring automation is off",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (monthIsCurrent) {
                TextButton(onClick = vm::runRecurringNow) { Text("Run now") }
            }
        }

        Spacer(Modifier.height(18.dp))
        V07MonthPicker(month, onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })

        Spacer(Modifier.height(22.dp))
        V07SectionLabel("MONTH PLAN")
        Spacer(Modifier.height(8.dp))
        V07Metric("Income", v07Euro(plan.expectedIncome), "Salary can fund a later budget month")
        V07Divider()
        V07Metric("Fixed payments", "−${v07Euro(plan.fixedPayments)}")
        V07Divider()
        V07Metric("Investments", "−${v07Euro(plan.plannedInvestments)}", "Includes recurring and manual contributions")
        V07Divider()
        V07Metric("Expenses", "−${v07Euro(plan.expenses)}")
        V07Divider()
        V07Metric("Savings allocated", "−${v07Euro(plan.savingsAllocated)}")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                V07SectionLabel("PROJECTED LEFT")
                Text(v07SignedEuro(plan.projectedLeft), fontSize = 34.sp, fontWeight = FontWeight.Medium)
            }
            if (monthIsCurrent && plan.projectedLeft > 0.0) {
                TextButton(onClick = { showAllocation = true }) { Text("Allocate →") }
            }
        }

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V07SectionLabel("SAVINGS", Modifier.weight(1f))
            TextButton(onClick = { showTargets = true }) { Text("Targets") }
        }
        V07Goal(
            "Emergency fund",
            summary.emergencyFundBalance,
            summary.emergencyFundTarget,
            if (summary.averageMonthlyOutflow > 0.0) "${String.format("%.1f", summary.emergencyFundMonths)} months covered" else "Coverage needs spending history",
            onClick = { if (monthIsCurrent) showAllocation = true },
        )
        V07Goal(
            "Crash reserve",
            summary.crashReserveBalance,
            summary.crashReserveTarget,
            "Reserve for market drawdowns",
            onClick = { if (monthIsCurrent) showAllocation = true },
        )
        V07Metric("General savings", v07Euro(summary.generalSavingsBalance), "Flexible savings without a target", onClick = {
            if (monthIsCurrent) showAllocation = true
        })
        V07Metric("Saved this month", v07Euro(summary.savedThisMonth))

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V07SectionLabel("INCOME", Modifier.weight(1f))
            TextButton(onClick = { showIncome = true }) { Text("+ Add") }
        }
        if (summary.incomes.isEmpty()) {
            V07EmptyMoney("Add salary or another recurring income.")
        } else {
            summary.incomes.forEachIndexed { index, income ->
                val fundedMonth = income.budgetMonthFor(month)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (monthIsCurrent) Modifier.clickable { vm.toggleIncome(income.id) }
                            else Modifier
                        )
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(income.name, fontSize = 14.sp)
                        Text(
                            "Day ${income.dayOfMonth} · ${if (income.budgetMonthOffset == 1) "funds next month" else "funds same month"}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(v07Euro(income.amount), fontSize = 13.sp)
                        if (monthIsCurrent) Text(
                            if (income.received) "received" else "scheduled",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) else Text(
                            "→ ${fundedMonth.month.name.take(3)}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != summary.incomes.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V07SectionLabel("FIXED PAYMENTS", Modifier.weight(1f))
            TextButton(onClick = { showPayment = true }) { Text("+ Add") }
        }
        if (summary.payments.isEmpty()) {
            V07EmptyMoney("Add rent, gym, ticket, phone or other recurring bills.")
        } else {
            summary.payments.forEachIndexed { index, payment ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (monthIsCurrent) Modifier.clickable { vm.togglePayment(payment.id) } else Modifier)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(payment.category.glyph, fontSize = 15.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(payment.name, fontSize = 14.sp)
                        Text("Day ${payment.dayOfMonth}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(v07Euro(payment.amount), fontSize = 13.sp)
                        if (monthIsCurrent) Text(
                            if (payment.paid) "paid" else "scheduled",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != summary.payments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(30.dp))
        V07SectionLabel("AUTO INVESTMENTS")
        Spacer(Modifier.height(4.dp))
        if (summary.recurringInvestments.isEmpty()) {
            V07EmptyMoney("Recurring portfolio contributions appear here after you create one from Portfolio.")
        } else {
            summary.recurringInvestments.forEachIndexed { index, recurring ->
                val holding: InvestmentHolding? = summary.investments.firstOrNull { it.id == recurring.holdingId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (monthIsCurrent) Modifier.clickable { vm.toggleRecurringInvestment(recurring.id) } else Modifier)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(holding?.name ?: "Investment", fontSize = 14.sp)
                        Text("Day ${recurring.dayOfMonth} · monthly", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(v07Euro(recurring.amount), fontSize = 13.sp)
                        if (monthIsCurrent) Text(
                            if (recurring.applied) "invested" else "scheduled",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != summary.recurringInvestments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V07SectionLabel("EXPENSES · ${v07Euro(overview.expenses)}", Modifier.weight(1f))
            if (monthIsCurrent) TextButton(onClick = { showExpense = true }) { Text("+ Add") }
        }
        if (expenseRows.isEmpty()) {
            V07EmptyMoney("No expenses recorded for this month.")
        } else {
            expenseRows.forEachIndexed { index, expense ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(expense.category.glyph, fontSize = 14.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(expense.note.ifBlank { expense.category.label }, fontSize = 13.sp)
                        Text(expense.date.format(V07ShortDateFormat), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("−${v07Euro(expense.amount)}", fontSize = 12.sp)
                }
                if (index != expenseRows.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showExpense) V07AddExpenseSheet(vm, onDismiss = { showExpense = false })
    if (showIncome) V07AddIncomeSheet(vm, onDismiss = { showIncome = false })
    if (showPayment) V07AddPaymentSheet(vm, onDismiss = { showPayment = false })
    if (showAllocation) V07AllocateSheet(vm, defaultAmount = plan.projectedLeft.coerceAtLeast(0.0), onDismiss = { showAllocation = false })
    if (showTargets) V07SavingsTargetsSheet(vm, onDismiss = { showTargets = false })
}

@Composable
private fun V07EmptyMoney(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
