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
import com.pix.folio.data.FolioStartMonth
import com.pix.folio.data.RecurringSavingsRule
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.RecurringIncome
import com.pix.folio.model.RecurringInvestment
import com.pix.folio.model.SavingsBucketType
import java.time.YearMonth

@Composable
internal fun V07MoneyScreen(vm: V07ViewModel) {
    val summary = vm.summary
    var month by remember { mutableStateOf(vm.suggestedBudgetMonth()) }
    var showExpense by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showAllocation by remember { mutableStateOf(false) }
    var showTargets by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showRecurringSaving by remember { mutableStateOf(false) }
    var savingsBucket by remember { mutableStateOf<SavingsBucketType?>(null) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var editingIncome by remember { mutableStateOf<RecurringIncome?>(null) }
    var editingPayment by remember { mutableStateOf<MonthlyPayment?>(null) }
    var editingRecurringInvestment by remember { mutableStateOf<RecurringInvestment?>(null) }
    var editingRecurringSaving by remember { mutableStateOf<RecurringSavingsRule?>(null) }
    var budgetCategory by remember { mutableStateOf<ExpenseCategory?>(null) }

    val envelope = vm.budgetEnvelope(month)
    val expenseRows = summary.expenses.filter { YearMonth.from(it.date) == month }.sortedByDescending { it.date }
    val monthIsCurrent = month == YearMonth.now()
    val salary = summary.incomes.firstOrNull {
        it.name.contains("salary", true) || it.name.contains("stipend", true) || it.name.contains("wage", true)
    } ?: summary.incomes.firstOrNull()
    val allocationDefault = minOf(envelope.availableCash.coerceAtLeast(0.0), summary.cashBalance)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("Money", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
        Text(
            "One salary-funded envelope per month. Previous leftovers never roll in automatically.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        V07MonthPicker(
            month = month,
            onPrevious = { if (month > FolioStartMonth) month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
            canPrevious = month > FolioStartMonth,
        )

        Spacer(Modifier.height(20.dp))
        Text("Available this month", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            v07Euro(envelope.availableCash),
            fontSize = 58.sp,
            lineHeight = 62.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${v07Euro(envelope.expectedIncome)} salary/income − ${v07Euro(envelope.committed)} planned = ${v07Euro(envelope.availableCash)} free",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Received for this envelope ${v07Euro(envelope.receivedIncome)} · account cash ${v07Euro(summary.cashBalance)} · prior-month cash excluded",
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (monthIsCurrent && allocationDefault > 0.0) {
            TextButton(onClick = { showAllocation = true }) {
                Text("Allocate available money →")
            }
        }

        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly salary", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showIncome = true }) { Text(if (salary == null) "+ Add" else "+ Another") }
        }
        if (salary == null) {
            V07Panel(onClick = { showIncome = true }) {
                Text("Add your monthly salary", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "For a salary received around the 28th–30th, choose next month so the September salary funds October.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            V07Panel(onClick = { editingIncome = salary }) {
                Text(salary.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(v07Euro(salary.amount), fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Expected day ${salary.dayOfMonth} · ${if (salary.budgetMonthOffset == 1) "funds next month" else "funds same month"} · tap to edit",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.toggleIncome(salary.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (salary.received) "Undo salary received this month" else "Mark salary received now")
            }
            summary.incomes.filterNot { it.id == salary.id }.forEach { income ->
                V07Metric(
                    income.name,
                    v07Euro(income.amount),
                    "Day ${income.dayOfMonth} · tap to edit",
                    onClick = { editingIncome = income },
                )
            }
        }

        if (monthIsCurrent) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showExpense = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("+ Expense", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { showPayment = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("+ Bill", fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(38.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Savings", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showTargets = true }) { Text("Targets") }
        }
        Text(
            "Savings balances stay manual. Monthly savings rules simply move the amount you choose from that month's salary envelope.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        V07Panel {
            V07Goal(
                "Emergency fund",
                summary.emergencyFundBalance,
                summary.emergencyFundTarget,
                if (summary.averageMonthlyOutflow > 0.0) "${String.format("%.1f", summary.emergencyFundMonths)} months covered" else "Tap to add your existing balance",
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

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Monthly savings", fontSize = 23.sp, fontWeight = FontWeight.Medium)
                Text("Example: €500 on day 1", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showRecurringSaving = true }) { Text("+ Add") }
        }
        if (vm.recurringSavingsRules.isEmpty()) {
            V07EmptyMoney("No recurring savings allocation yet.")
        } else {
            vm.recurringSavingsRules.forEachIndexed { index, rule ->
                V07Metric(
                    rule.bucket.label,
                    v07Euro(rule.amount),
                    "Day ${rule.dayOfMonth} · ${if (rule.appliedFor(month)) "saved" else "scheduled"} · tap to edit",
                    onClick = { editingRecurringSaving = rule },
                )
                if (index != vm.recurringSavingsRules.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(38.dp))
        Text("Month plan", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        V07Panel {
            V07Metric("Expected income", v07Euro(envelope.expectedIncome))
            V07Divider()
            V07Metric("Recurring bills", "−${v07Euro(envelope.fixedPayments)}")
            V07Divider()
            V07Metric("Investments", "−${v07Euro(envelope.plannedInvestments)}")
            V07Divider()
            V07Metric("Spending budgets", "−${v07Euro(envelope.plannedVariableSpending)}")
            V07Divider()
            V07Metric("Savings", "−${v07Euro(envelope.savingsCommitment)}")
            V07Divider()
            V07Metric("Available", v07Euro(envelope.availableCash), "Does not include previous-month leftover cash")
        }

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spending budgets", fontSize = 27.sp, fontWeight = FontWeight.Medium)
                Text("Reserve money without marking it spent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showBudget = true }) { Text("+ Add") }
        }
        val budgetRows = ExpenseCategory.entries.filter { summary.budgetFor(it) != null }
        if (budgetRows.isEmpty()) {
            V07Panel(onClick = { showBudget = true }) {
                Text("Add a spending budget", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text("For your plan, Food can be €300/month.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            budgetRows.forEachIndexed { index, category ->
                val budget = summary.budgetFor(category)?.monthlyLimit ?: 0.0
                val spent = summary.spentFor(category, month)
                V07Metric(
                    category.label,
                    v07Euro(budget),
                    "${v07Euro(spent)} spent · tap to edit",
                    onClick = { budgetCategory = category },
                )
                if (index != budgetRows.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(38.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bills", fontSize = 27.sp, fontWeight = FontWeight.Medium)
                Text("Recurring payments", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (monthIsCurrent) TextButton(onClick = vm::runRecurringNow) { Text("Run now") }
        }
        if (summary.payments.isEmpty()) {
            V07EmptyMoney("No recurring bills yet.")
        } else {
            summary.payments.forEachIndexed { index, payment ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clickable { editingPayment = payment }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(payment.category.glyph, fontSize = 18.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(payment.name, fontSize = 17.sp)
                            Text("Day ${payment.dayOfMonth} · tap to edit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(v07Euro(payment.amount), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                    if (monthIsCurrent) {
                        TextButton(onClick = { vm.togglePayment(payment.id) }) {
                            Text(if (payment.paid) "Paid" else "Mark")
                        }
                    }
                }
                if (index != summary.payments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Recurring investments", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        if (summary.recurringInvestments.isEmpty()) {
            V07EmptyMoney("Create a monthly investment from Portfolio.")
        } else {
            summary.recurringInvestments.forEachIndexed { index, recurring ->
                val holding: InvestmentHolding? = summary.investments.firstOrNull { it.id == recurring.holdingId }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    V07Metric(
                        holding?.name ?: "Investment",
                        v07Euro(recurring.amount),
                        "Day ${recurring.dayOfMonth} · tap to edit",
                        modifier = Modifier.weight(1f),
                        onClick = { editingRecurringInvestment = recurring },
                    )
                    if (monthIsCurrent) {
                        TextButton(onClick = { vm.toggleRecurringInvestment(recurring.id) }) {
                            Text(if (recurring.applied) "Done" else "Mark")
                        }
                    }
                }
                if (index != summary.recurringInvestments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(38.dp))
        Text("Expenses", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Text(
            "${v07Euro(envelope.actualExpenses)} recorded this month · tap any row to edit or delete",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (expenseRows.isEmpty()) {
            V07EmptyMoney("No expenses recorded for this month.")
        } else {
            expenseRows.forEachIndexed { index, expense ->
                Row(
                    Modifier.fillMaxWidth().clickable { editingExpense = expense }.padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(expense.category.glyph, fontSize = 18.sp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(expense.note.ifBlank { expense.category.label }, fontSize = 17.sp)
                        Text("${expense.date.format(V07ShortDateFormat)} · tap to edit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("−${v07Euro(expense.amount)}", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
                if (index != expenseRows.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showExpense) V07AddExpenseSheet(vm, onDismiss = { showExpense = false })
    editingExpense?.let { row -> V07AddExpenseSheet(vm, existing = row, onDismiss = { editingExpense = null }) }
    if (showIncome) V07AddIncomeSheet(vm, onDismiss = { showIncome = false })
    editingIncome?.let { row -> V07AddIncomeSheet(vm, existing = row, onDismiss = { editingIncome = null }) }
    if (showPayment) V07AddPaymentSheet(vm, onDismiss = { showPayment = false })
    editingPayment?.let { row -> V07AddPaymentSheet(vm, existing = row, onDismiss = { editingPayment = null }) }
    if (showAllocation) V07AllocateSheet(vm, defaultAmount = allocationDefault, onDismiss = { showAllocation = false })
    if (showTargets) V07SavingsTargetsSheet(vm, onDismiss = { showTargets = false })
    if (showBudget) V07BudgetSheet(vm, onDismiss = { showBudget = false })
    budgetCategory?.let { category -> V07BudgetSheet(vm, initialCategory = category, onDismiss = { budgetCategory = null }) }
    if (showRecurringSaving) V07RecurringSavingSheet(vm, onDismiss = { showRecurringSaving = false })
    editingRecurringSaving?.let { row ->
        V07RecurringSavingSheet(vm, existing = row, onDismiss = { editingRecurringSaving = null })
    }
    editingRecurringInvestment?.let { row ->
        V07RecurringInvestmentSheet(vm, existing = row, onDismiss = { editingRecurringInvestment = null })
    }
    savingsBucket?.let { bucket ->
        V07SavingsTransferSheet(vm, bucket, onDismiss = { savingsBucket = null })
    }
}

@Composable
private fun V07EmptyMoney(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
