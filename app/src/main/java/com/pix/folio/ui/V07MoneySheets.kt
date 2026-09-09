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
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.SavingsBucketType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AddExpenseSheet(vm: V07ViewModel, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf(ExpenseCategory.OTHER) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Add expense") {
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
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.addExpense(category, amount.v07Double(), note); onDismiss() },
                enabled = amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save expense") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AddIncomeSheet(vm: V07ViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Salary") }
    var amount by remember { mutableStateOf("2404") }
    var day by remember { mutableStateOf("29") }
    var nextMonth by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Recurring income") {
            V07TextField(name, "Name") { name = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(amount, "Amount (€)", Modifier.weight(1f)) { amount = it }
                V07NumberField(day, "Pay day", Modifier.weight(1f)) { day = it.filter(Char::isDigit).take(2) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Budget month", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
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
                if (nextMonth) "Example: salary received September 29 funds October." else "Income funds the month it is received.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.addIncome(name, amount.v07Double(), day.toIntOrNull() ?: 1, nextMonth); onDismiss() },
                enabled = name.isNotBlank() && amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save income") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07AddPaymentSheet(vm: V07ViewModel, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf(PaymentCategory.HOME) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("1") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        V07SheetBody("Recurring payment") {
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
                onClick = { vm.addPayment(category, name, amount.v07Double(), day.toIntOrNull() ?: 1); onDismiss() },
                enabled = name.isNotBlank() && amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save payment") }
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
                "Move what is left without changing your monthly history.",
                fontSize = 10.sp,
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
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Keep as cash")
            }
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
                modifier = Modifier.fillMaxWidth().height(50.dp),
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
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
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
            Text(title, fontSize = 13.sp)
            Text(detail, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
