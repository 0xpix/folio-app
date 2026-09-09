package com.pix.folio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.data.RecurringSavingsRule
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.RecurringIncome
import com.pix.folio.model.RecurringInvestment
import com.pix.folio.model.SavingsBucketType
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AddExpenseSheet(
    vm: V07ViewModel,
    existing: Expense? = null,
    onDismiss: () -> Unit,
) {
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: ExpenseCategory.OTHER) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount?.toString() ?: "") }
    var note by remember(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var date by remember(existing?.id) { mutableStateOf((existing?.date ?: LocalDate.now()).toString()) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody(if (existing == null) "Add expense" else "Edit expense") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries) { item ->
                    OutlinedButton(onClick = { category = item }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (category == item) "• ${item.label}" else item.label)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            V07NumberField(amount, "Amount (€)") { amount = it }
            Spacer(Modifier.height(10.dp))
            V07TextField(note, "Note") { note = it }
            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                V07TextField(date, "Date · YYYY-MM-DD") { date = it.take(10) }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (existing == null) vm.addExpense(category, amount.v07Double(), note)
                    else vm.updateExpense(existing.id, category, amount.v07Double(), parsedDate ?: existing.date, note)
                    onDismiss()
                },
                enabled = amount.v07Double() > 0.0 && (existing == null || parsedDate != null),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text(if (existing == null) "Save expense" else "Save changes") }

            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { vm.removeExpense(existing.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete expense", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AddIncomeSheet(
    vm: V07ViewModel,
    existing: RecurringIncome? = null,
    onDismiss: () -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "Salary") }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount?.toString() ?: "2404") }
    var day by remember(existing?.id) { mutableStateOf(existing?.dayOfMonth?.toString() ?: "30") }
    var nextMonth by remember(existing?.id) { mutableStateOf(existing?.budgetMonthOffset == 1 || existing == null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody(if (existing == null) "Monthly salary" else "Edit monthly salary") {
            Text(
                "Salary is a recurring funding rule. A late-month salary can belong to the following budget month.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            V07TextField(name, "Name") { name = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(amount, "Monthly amount (€)", Modifier.weight(1f)) { amount = it }
                V07NumberField(day, "Pay day", Modifier.weight(1f)) { day = it.filter(Char::isDigit).take(2) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Which budget month should it fund?", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nextMonth = false }, shape = RoundedCornerShape(20.dp)) {
                    Text(if (!nextMonth) "• Same month" else "Same month")
                }
                OutlinedButton(onClick = { nextMonth = true }, shape = RoundedCornerShape(20.dp)) {
                    Text(if (nextMonth) "• Next month" else "Next month")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (nextMonth) "Example: salary received September 28–30 funds October." else "Salary funds the month it is received.",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val value = amount.v07Double()
                    val due = day.toIntOrNull() ?: 30
                    if (existing == null) vm.addIncome(name, value, due, nextMonth)
                    else vm.updateIncome(existing.id, name, value, due, nextMonth)
                    onDismiss()
                },
                enabled = name.isNotBlank() && amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text(if (existing == null) "Save monthly salary" else "Save changes") }

            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { vm.removeIncome(existing.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete salary rule", color = MaterialTheme.colorScheme.error) }
                Text(
                    "Past salary entries remain in history.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AddPaymentSheet(
    vm: V07ViewModel,
    existing: MonthlyPayment? = null,
    onDismiss: () -> Unit,
) {
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: PaymentCategory.HOME) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount?.toString() ?: "") }
    var day by remember(existing?.id) { mutableStateOf(existing?.dayOfMonth?.toString() ?: "1") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody(if (existing == null) "Recurring payment" else "Edit recurring payment") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PaymentCategory.entries) { item ->
                    OutlinedButton(onClick = { category = item }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (category == item) "• ${item.label}" else item.label)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            V07TextField(name, "Name · rent, gym, ticket…") { name = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(amount, "Amount (€)", Modifier.weight(1f)) { amount = it }
                V07NumberField(day, "Due day", Modifier.weight(1f)) { day = it.filter(Char::isDigit).take(2) }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val value = amount.v07Double()
                    val due = day.toIntOrNull() ?: 1
                    if (existing == null) vm.addPayment(category, name, value, due)
                    else vm.updatePayment(existing.id, category, name, value, due)
                    onDismiss()
                },
                enabled = name.isNotBlank() && amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text(if (existing == null) "Save payment" else "Save changes") }

            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { vm.removePayment(existing.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete recurring bill", color = MaterialTheme.colorScheme.error) }
                Text(
                    "Past paid entries stay in history; only the recurring rule is removed.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07BudgetSheet(
    vm: V07ViewModel,
    initialCategory: ExpenseCategory? = null,
    onDismiss: () -> Unit,
) {
    var category by remember(initialCategory) { mutableStateOf(initialCategory ?: ExpenseCategory.FOOD) }
    var amount by remember(category) {
        mutableStateOf(vm.summary.budgetFor(category)?.monthlyLimit?.toString() ?: "")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Monthly spending budget") {
            Text(
                "Budgets reserve money inside the month without pretending it has already been spent.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries) { item ->
                    OutlinedButton(
                        onClick = {
                            category = item
                            amount = vm.summary.budgetFor(item)?.monthlyLimit?.toString() ?: ""
                        },
                        shape = RoundedCornerShape(20.dp),
                    ) { Text(if (category == item) "• ${item.label}" else item.label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            V07NumberField(amount, "Monthly budget (€)") { amount = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.setBudget(category, amount.v07Double()); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save budget") }
            if (vm.summary.budgetFor(category) != null) {
                TextButton(
                    onClick = { vm.setBudget(category, 0.0); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove budget", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07RecurringSavingSheet(
    vm: V07ViewModel,
    existing: RecurringSavingsRule? = null,
    onDismiss: () -> Unit,
) {
    var bucket by remember(existing?.id) { mutableStateOf(existing?.bucket ?: SavingsBucketType.GENERAL) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount?.toString() ?: "500") }
    var day by remember(existing?.id) { mutableStateOf(existing?.dayOfMonth?.toString() ?: "1") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody(if (existing == null) "Monthly savings" else "Edit monthly savings") {
            Text(
                "This is moved from the salary-funded monthly envelope into savings on its due day.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SavingsBucketType.entries) { item ->
                    OutlinedButton(onClick = { bucket = item }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (bucket == item) "• ${item.label}" else item.label)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(amount, "Amount (€)", Modifier.weight(1f)) { amount = it }
                V07NumberField(day, "Day", Modifier.weight(1f)) { day = it.filter(Char::isDigit).take(2) }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val value = amount.v07Double()
                    val due = day.toIntOrNull() ?: 1
                    if (existing == null) vm.addRecurringSaving(bucket, value, due)
                    else vm.updateRecurringSaving(existing.id, bucket, value, due)
                    onDismiss()
                },
                enabled = amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text(if (existing == null) "Save monthly savings" else "Save changes") }
            if (existing != null) {
                TextButton(
                    onClick = { vm.removeRecurringSaving(existing.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete monthly savings rule", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07RecurringInvestmentSheet(
    vm: V07ViewModel,
    existing: RecurringInvestment,
    onDismiss: () -> Unit,
) {
    var amount by remember(existing.id) { mutableStateOf(existing.amount.toString()) }
    var day by remember(existing.id) { mutableStateOf(existing.dayOfMonth.toString()) }
    val holding = vm.summary.investments.firstOrNull { it.id == existing.holdingId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Recurring investment") {
            Text(holding?.name ?: "Investment", fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(amount, "Amount (€)", Modifier.weight(1f)) { amount = it }
                V07NumberField(day, "Day", Modifier.weight(1f)) { day = it.filter(Char::isDigit).take(2) }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.updateRecurringInvestment(existing.id, amount.v07Double(), day.toIntOrNull() ?: 1)
                    onDismiss()
                },
                enabled = amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save changes") }
            TextButton(
                onClick = { vm.removeRecurringInvestment(existing.id); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete recurring investment", color = MaterialTheme.colorScheme.error) }
            Text(
                "Past investment transactions stay in history.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07SavingsTransferSheet(
    vm: V07ViewModel,
    bucket: SavingsBucketType,
    onDismiss: () -> Unit,
) {
    var amount by remember(bucket) { mutableStateOf("") }
    var existingBalance by remember(bucket) { mutableStateOf("") }
    val value = amount.v07Double()
    val current = when (bucket) {
        SavingsBucketType.EMERGENCY -> vm.summary.emergencyFundBalance
        SavingsBucketType.CRASH_RESERVE -> vm.summary.crashReserveBalance
        SavingsBucketType.GENERAL -> vm.summary.generalSavingsBalance
    }
    val title = when (bucket) {
        SavingsBucketType.EMERGENCY -> "Emergency fund"
        SavingsBucketType.CRASH_RESERVE -> "Crash reserve"
        SavingsBucketType.GENERAL -> "General savings"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody(title) {
            V07Panel {
                Text("Saved", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v07Euro(current), fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                V07Metric("Account cash", v07Euro(vm.summary.cashBalance))
            }

            if (bucket == SavingsBucketType.EMERGENCY) {
                Spacer(Modifier.height(22.dp))
                Text("Already saved before Folio?", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Enter the balance you already had. This updates the milestone without subtracting from this month's cash or counting it as new savings.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                V07NumberField(existingBalance, "Existing saved balance (€)") { existingBalance = it }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        vm.setExistingEmergencyFundBalance(existingBalance.v07Double())
                        onDismiss()
                    },
                    enabled = existingBalance.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(22.dp),
                ) { Text("Set existing balance") }
            }

            Spacer(Modifier.height(24.dp))
            Text("Move money now", fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Text(
                "These actions move real money between account cash and this savings bucket.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            V07NumberField(amount, "Amount (€)") { amount = it }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    vm.transferSavings(bucket, value, "Manual ${bucket.label.lowercase()} deposit")
                    onDismiss()
                },
                enabled = value > 0.0 && vm.summary.cashBalance > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Add from cash") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.transferSavings(bucket, -value, "Manual ${bucket.label.lowercase()} withdrawal")
                    onDismiss()
                },
                enabled = value > 0.0 && current > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Withdraw to cash") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AllocateSheet(vm: V07ViewModel, defaultAmount: Double, onDismiss: () -> Unit) {
    var amount by remember(defaultAmount) { mutableStateOf(if (defaultAmount > 0.0) String.format("%.2f", defaultAmount) else "") }
    val value = amount.v07Double().coerceAtMost(vm.summary.cashBalance)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Where should the money go?") {
            Text(
                "Nothing moves until you choose a destination. This allocation never becomes next month's available budget.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            V07NumberField(amount, "Amount (€)") { amount = it }
            Spacer(Modifier.height(16.dp))

            AllocationButton("Emergency fund", "Build the €${vm.summary.emergencyFundTarget.toInt()} safety target", value > 0.0) {
                vm.transferSavings(SavingsBucketType.EMERGENCY, value, "Leftover allocation")
                onDismiss()
            }
            AllocationButton("Crash reserve", "Keep cash ready for a market drawdown", value > 0.0) {
                vm.transferSavings(SavingsBucketType.CRASH_RESERVE, value, "Leftover allocation")
                onDismiss()
            }
            AllocationButton("General savings", "Flexible savings without a fixed purpose", value > 0.0) {
                vm.transferSavings(SavingsBucketType.GENERAL, value, "Leftover allocation")
                onDismiss()
            }

            if (vm.summary.investments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                V07SectionLabel("Invest it")
                Spacer(Modifier.height(5.dp))
                vm.summary.investments.take(6).forEach { holding ->
                    AllocationButton(holding.name, "Add to ${holding.kind.label}", value > 0.0) {
                        vm.addInvestmentContribution(holding.id, value)
                        onDismiss()
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Keep unassigned for now") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07SavingsTargetsSheet(vm: V07ViewModel, onDismiss: () -> Unit) {
    var emergency by remember { mutableStateOf(vm.summary.emergencyFundTarget.toString()) }
    var crash by remember { mutableStateOf(vm.summary.crashReserveTarget.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Savings targets") {
            V07NumberField(emergency, "Emergency target (€)") { emergency = it }
            Spacer(Modifier.height(10.dp))
            V07NumberField(crash, "Crash reserve target (€)") { crash = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.setSavingsTarget(SavingsBucketType.EMERGENCY, emergency.v07Double())
                    vm.setSavingsTarget(SavingsBucketType.CRASH_RESERVE, crash.v07Double())
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save targets") }
        }
    }
}

@Composable
private fun V07SheetBody(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 34.dp)
    ) {
        Text(title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun AllocationButton(title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 15.sp)
            Text(detail, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
