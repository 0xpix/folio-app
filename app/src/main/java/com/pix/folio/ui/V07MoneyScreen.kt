package com.pix.folio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.pix.folio.model.SavingsBucketType
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
    var savingsBucket by remember { mutableStateOf<SavingsBucketType?>(null) }

    val plan = summary.moneyPlan(month)
    val overview = summary.monthOverview(month)
    val expenseRows = summary.expenses.filter { YearMonth.from(it.date) == month }.sortedByDescending { it.date }
    val monthIsCurrent = month == YearMonth.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("Money", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(
            "See what came in, what went out, and what is still available.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        V07MonthPicker(month, onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })

        Spacer(Modifier.height(16.dp))
        V07Panel {
            V07SectionLabel("Available cash")
            Spacer(Modifier.height(4.dp))
            Text(v07Euro(summary.cashBalance), fontSize = 32.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            V07Divider()
            V07Metric(
                "Projected left",
                v07SignedEuro(plan.projectedLeft),
                "After planned bills, investments, expenses, and savings",
                onClick = if (monthIsCurrent && plan.projectedLeft > 0.0) ({ showAllocation = true }) else null,
            )
        }

        if (monthIsCurrent) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showExpense = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("+ Expense", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { showIncome = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("+ Income", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { showPayment = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("+ Bill", fontSize = 11.sp) }
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Savings", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showTargets = true }) { Text("Targets") }
        }
        Text(
            "Savings are manual. Tap a bucket to add or withdraw money.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        V07Panel {
            V07Goal(
                "Emergency fund",
                summary.emergencyFundBalance,
                summary.emergencyFundTarget,
                if (summary.averageMonthlyOutflow > 0.0) "${String.format("%.1f", summary.emergencyFundMonths)} months covered" else "Coverage needs spending history",
                onClick = { savingsBucket = SavingsBucketType.EMERGENCY },
            )
            V07Divider()
            V07Goal(
                "Crash reserve",
                summary.crashReserveBalance,
                summary.crashReserveTarget,
                "Reserve for market drawdowns",
                onClick = { savingsBucket = SavingsBucketType.CRASH_RESERVE },
            )
            V07Divider()
            V07Metric(
                "General savings",
                v07Euro(summary.generalSavingsBalance),
                "Flexible savings without a target",
                onClick = { savingsBucket = SavingsBucketType.GENERAL },
            )
        }

        Spacer(Modifier.height(30.dp))
        Text("Month plan", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        V07Panel {
            V07Metric("Expected income", v07Euro(plan.expectedIncome))
            V07Divider()
            V07Metric("Fixed payments", "−${v07Euro(plan.fixedPayments)}")
            V07Divider()
            V07Metric("Investments", "−${v07Euro(plan.plannedInvestments)}")
            V07Divider()
            V07Metric("Expenses", "−${v07Euro(plan.expenses)}")
            V07Divider()
            V07Metric("Savings allocated", "−${v07Euro(plan.savingsAllocated)}")
        }
        if (monthIsCurrent && plan.projectedLeft > 0.0) {
            TextButton(onClick = { showAllocation = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Allocate leftover manually →")
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recurring", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (vm.autoRecurringEnabled) "Salary, bills and recurring investments can post automatically." else "Recurring automation is off.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (monthIsCurrent) TextButton(onClick = vm::runRecurringNow) { Text("Run now") }
        }

        Spacer(Modifier.height(12.dp))
        V07SectionLabel("Income")
        if (summary.incomes.isEmpty()) {
            V07EmptyMoney("No recurring income yet.")
        } else {
            summary.incomes.forEachIndexed { index, income ->
                val fundedMonth = income.budgetMonthFor(month)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (monthIsCurrent) Modifier.clickable { vm.toggleIncome(income.id) } else Modifier)
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(income.name, fontSize = 14.sp)
                        Text(
                            "Day ${income.dayOfMonth} · ${if (income.budgetMonthOffset == 1) "funds next month" else "funds same month"}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(v07Euro(income.amount), fontSize = 13.sp)
                        Text(
                            if (monthIsCurrent) {
                                if (income.received) "received" else "scheduled"
                            } else {
                                "→ ${fundedMonth.month.name.take(3)}"
                            },
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != summary.incomes.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(20.dp))
        V07SectionLabel("Bills")
        if (summary.payments.isEmpty()) {
            V07EmptyMoney("No recurring bills yet.")
        } else {
            summary.payments.forEachIndexed { index, payment ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (monthIsCurrent) Modifier.clickable { vm.togglePayment(payment.id) } else Modifier)
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(payment.category.glyph, fontSize = 15.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(payment.name, fontSize = 14.sp)
                        Text("Day ${payment.dayOfMonth}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        Spacer(Modifier.height(20.dp))
        V07SectionLabel("Investments")
        if (summary.recurringInvestments.isEmpty()) {
            V07EmptyMoney("Recurring portfolio contributions appear here after you create one from Portfolio.")
        } else {
            summary.recurringInvestments.forEachIndexed { index, recurring ->
                val holding: InvestmentHolding? = summary.investments.firstOrNull { it.id == recurring.holdingId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (monthIsCurrent) Modifier.clickable { vm.toggleRecurringInvestment(recurring.id) } else Modifier)
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(holding?.name ?: "Investment", fontSize = 14.sp)
                        Text("Day ${recurring.dayOfMonth} · monthly", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Expenses", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(
            "${v07Euro(overview.expenses)} this month",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
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
    savingsBucket?.let { bucket ->
        V07SavingsTransferSheet(vm, bucket, onDismiss = { savingsBucket = null })
    }
}

@Composable
private fun V07EmptyMoney(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
