package com.pix.folio.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.data.RecurringSavingsRule
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.RecurringIncome
import com.pix.folio.model.RecurringInvestment
import com.pix.folio.model.SavingsBucketType
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V08MoneyScreen(vm: V07ViewModel) {
    val summary = vm.summary
    val planMonth = vm.suggestedBudgetMonth()
    val currentMonth = YearMonth.now()
    val envelope = vm.budgetEnvelope(planMonth)

    var editSpendable by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showRecurringSaving by remember { mutableStateOf(false) }
    var showTargets by remember { mutableStateOf(false) }

    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var editingIncome by remember { mutableStateOf<RecurringIncome?>(null) }
    var editingPayment by remember { mutableStateOf<MonthlyPayment?>(null) }
    var editingRecurringSaving by remember { mutableStateOf<RecurringSavingsRule?>(null) }
    var editingRecurringInvestment by remember { mutableStateOf<RecurringInvestment?>(null) }
    var budgetCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var savingsBucket by remember { mutableStateOf<SavingsBucketType?>(null) }

    val expenses = summary.expenses
        .filter { YearMonth.from(it.date) == currentMonth }
        .sortedByDescending { it.date }
    val sparkasseExpenses = vm.sparkasseSnapshot
        ?.expensesForMonth(currentMonth)
        ?.sortedByDescending { it.bookingDate }
    val budgetRows = ExpenseCategory.selectableEntries.filter { summary.budgetFor(it) != null }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("Money", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
        Text(
            "Cash, monthly plan and recurring money in one place.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("AVAILABLE CASH", fontSize = 11.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v07Euro(vm.availableCash), fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            if (vm.sparkasseSnapshot == null) {
                TextButton(onClick = { editSpendable = true }) { Text("Edit") }
            }
        }
        Text(
            if (vm.sparkasseSnapshot != null) "Sparkasse balance · read-only snapshot"
            else "Money you can use right now.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.sparkasseSnapshot == null) {
                OutlinedButton(
                    onClick = { showExpense = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("+ Expense") }
            }
            OutlinedButton(
                onClick = { showIncome = true },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("+ Income") }
        }

        Spacer(Modifier.height(30.dp))
        Text("Plan for ${planMonth.atDay(1).format(V07MonthFormat)}", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        V07Panel {
            V07Metric("Expected income", v07Euro(envelope.expectedIncome))
            V07Divider()
            V07Metric("Bills", "−${v07Euro(envelope.fixedPayments)}")
            V07Divider()
            V07Metric("Investments", "−${v07Euro(envelope.plannedInvestments)}")
            V07Divider()
            V07Metric("Spending budgets", "−${v07Euro(envelope.plannedVariableSpending)}")
            V07Divider()
            V07Metric("Savings", "−${v07Euro(envelope.savingsCommitment)}")
        }

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recurring", fontSize = 27.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (vm.autoRecurringEnabled) "Automatic · on" else "Automatic · off in Settings",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = vm::runRecurringNow) { Text("Run now") }
        }
        vm.recurringStatusLabel?.let {
            Text(it, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(14.dp))
        V08RecurringHeader("Income", onAdd = { showIncome = true })
        if (summary.incomes.isEmpty()) {
            V08EmptyRow("No recurring income")
        } else {
            summary.incomes.forEachIndexed { index, income ->
                V07Metric(
                    income.name,
                    v07Euro(income.amount),
                    "${v08DueLabel(income.dueDateFor(currentMonth), income.lastReceivedMonth == currentMonth)} · " +
                        if (income.budgetMonthOffset == 1) "funds next month" else "funds this month",
                    onClick = { editingIncome = income },
                )
                if (index != summary.incomes.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(20.dp))
        V08RecurringHeader("Bills", onAdd = { showPayment = true })
        if (summary.payments.isEmpty()) {
            V08EmptyRow("No recurring bills")
        } else {
            summary.payments.forEachIndexed { index, payment ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    V07Metric(
                        payment.name,
                        v07Euro(payment.amount),
                        v08DueLabel(payment.dueDateFor(currentMonth), payment.lastPaidMonth == currentMonth),
                        modifier = Modifier.weight(1f),
                        onClick = { editingPayment = payment },
                    )
                    TextButton(onClick = { vm.togglePayment(payment.id) }) {
                        Text(if (payment.lastPaidMonth == currentMonth) "Undo" else "Mark")
                    }
                }
                if (index != summary.payments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(20.dp))
        V08RecurringHeader("Savings", onAdd = { showRecurringSaving = true })
        if (vm.recurringSavingsRules.isEmpty()) {
            V08EmptyRow("No recurring savings")
        } else {
            vm.recurringSavingsRules.forEachIndexed { index, rule ->
                V07Metric(
                    rule.bucket.label,
                    v07Euro(rule.amount),
                    v08DueLabel(rule.dueDateFor(currentMonth), rule.appliedFor(currentMonth)),
                    onClick = { editingRecurringSaving = rule },
                )
                if (index != vm.recurringSavingsRules.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(20.dp))
        V08RecurringHeader("Investments")
        if (summary.recurringInvestments.isEmpty()) {
            V08EmptyRow("Set a recurring contribution from a holding in Portfolio")
        } else {
            summary.recurringInvestments.forEachIndexed { index, recurring ->
                val holding: InvestmentHolding? = summary.investments.firstOrNull { it.id == recurring.holdingId }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    V07Metric(
                        holding?.name ?: "Investment",
                        v07Euro(recurring.amount),
                        v08DueLabel(recurring.dueDateFor(currentMonth), recurring.lastAppliedMonth == currentMonth),
                        modifier = Modifier.weight(1f),
                        onClick = { editingRecurringInvestment = recurring },
                    )
                    TextButton(onClick = { vm.toggleRecurringInvestment(recurring.id) }) {
                        Text(if (recurring.lastAppliedMonth == currentMonth) "Undo" else "Mark")
                    }
                }
                if (index != summary.recurringInvestments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Savings", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showTargets = true }) { Text("Goals") }
        }
        V07Panel {
            V07Goal(
                "Emergency fund",
                summary.emergencyFundBalance,
                summary.emergencyFundTarget,
                if (summary.averageMonthlyOutflow > 0.0) "${String.format("%.1f", summary.emergencyFundMonths)} months covered" else "Safety fund",
                onClick = { savingsBucket = SavingsBucketType.EMERGENCY },
            )
            V07Divider()
            V07Goal(
                "Crash reserve",
                summary.crashReserveBalance,
                summary.crashReserveTarget,
                "Market reserve",
                onClick = { savingsBucket = SavingsBucketType.CRASH_RESERVE },
            )
            V07Divider()
            V07Metric(
                "General savings",
                v07Euro(summary.generalSavingsBalance),
                "Tap to move money",
                onClick = { savingsBucket = SavingsBucketType.GENERAL },
            )
        }

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spending budgets", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showBudget = true }) { Text("+ Add") }
        }
        if (budgetRows.isEmpty()) {
            V08EmptyRow("No spending budgets")
        } else {
            budgetRows.forEachIndexed { index, category ->
                val budget = summary.budgetFor(category)?.monthlyLimit ?: 0.0
                val spent = sparkasseExpenses
                    ?.filter { it.category == category }
                    ?.sumOf { it.expenseAmount }
                    ?: summary.spentFor(category, currentMonth)
                V07Metric(
                    category.label,
                    "${v07Euro(spent)} / ${v07Euro(budget)}",
                    "Tap to edit",
                    onClick = { budgetCategory = category },
                )
                if (index != budgetRows.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sparkasseExpenses != null) "Bank spending" else "Expenses",
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (sparkasseExpenses == null) {
                TextButton(onClick = { showExpense = true }) { Text("+ Add") }
            }
        }
        if (sparkasseExpenses != null) {
            if (sparkasseExpenses.isEmpty()) {
                V08EmptyRow("No booked Sparkasse spending this month")
            } else {
                sparkasseExpenses.take(20).forEachIndexed { index, transaction ->
                    val title = transaction.merchant.ifBlank {
                        transaction.purpose.ifBlank { transaction.category.label }
                    }
                    V07Metric(
                        title,
                        "−${v07Euro(transaction.expenseAmount)}",
                        "${transaction.bookingDate.format(V07ShortDateFormat)} · ${transaction.category.label}",
                    )
                    if (index != sparkasseExpenses.take(20).lastIndex) V07Divider()
                }
            }
        } else if (expenses.isEmpty()) {
            V08EmptyRow("No expenses this month")
        } else {
            expenses.take(12).forEachIndexed { index, expense ->
                V07Metric(
                    expense.note.ifBlank { expense.category.label },
                    "−${v07Euro(expense.amount)}",
                    expense.date.format(V07ShortDateFormat),
                    onClick = { editingExpense = expense },
                )
                if (index != expenses.take(12).lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(90.dp))
    }

    if (editSpendable) {
        V08SpendableBalanceSheet(
            current = summary.cashBalance,
            onDismiss = { editSpendable = false },
            onSave = {
                vm.setCashBalance(it)
                editSpendable = false
            },
        )
    }
    if (showExpense) V07AddExpenseSheet(vm, onDismiss = { showExpense = false })
    editingExpense?.let { row -> V07AddExpenseSheet(vm, existing = row, onDismiss = { editingExpense = null }) }
    if (showIncome) V07AddIncomeSheet(vm, onDismiss = { showIncome = false })
    editingIncome?.let { row -> V07AddIncomeSheet(vm, existing = row, onDismiss = { editingIncome = null }) }
    if (showPayment) V07AddPaymentSheet(vm, onDismiss = { showPayment = false })
    editingPayment?.let { row -> V07AddPaymentSheet(vm, existing = row, onDismiss = { editingPayment = null }) }
    if (showBudget) V07BudgetSheet(vm, onDismiss = { showBudget = false })
    budgetCategory?.let { category -> V07BudgetSheet(vm, initialCategory = category, onDismiss = { budgetCategory = null }) }
    if (showRecurringSaving) V07RecurringSavingSheet(vm, onDismiss = { showRecurringSaving = false })
    editingRecurringSaving?.let { row ->
        V07RecurringSavingSheet(vm, existing = row, onDismiss = { editingRecurringSaving = null })
    }
    editingRecurringInvestment?.let { row ->
        V07RecurringInvestmentSheet(vm, existing = row, onDismiss = { editingRecurringInvestment = null })
    }
    if (showTargets) V07SavingsTargetsSheet(vm, onDismiss = { showTargets = false })
    savingsBucket?.let { bucket ->
        V07SavingsTransferSheet(vm, bucket, onDismiss = { savingsBucket = null })
    }
}

@Composable
private fun V08RecurringHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (onAdd != null) TextButton(onClick = onAdd) { Text("+ Add") }
    }
}

@Composable
private fun V08EmptyRow(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun v08DueLabel(dueDate: LocalDate, completed: Boolean): String {
    if (completed) return "Done this month"
    val today = LocalDate.now()
    return when {
        !dueDate.isAfter(today) -> "Due now"
        else -> "Due ${dueDate.format(V07ShortDateFormat)}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V08SpendableBalanceSheet(
    current: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var value by remember(current) { mutableStateOf(if (current == 0.0) "" else current.toString()) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
            Text("Available cash", fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text(
                "Set the cash Folio should treat as available right now.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(14) },
                label = { Text("Cash amount (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(parsed ?: return@Button) },
                enabled = parsed != null && parsed >= 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save") }
        }
    }
}
