package com.pix.folio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V08MoneyScreen(vm: V07ViewModel) {
    val summary = vm.summary
    val month = vm.suggestedBudgetMonth()
    val envelope = vm.budgetEnvelope(month)
    var editSpendable by remember { mutableStateOf(false) }
    var showFullManager by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("Money", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
        Text(
            "Two numbers, two jobs. Spendable now is what you can use today. Unassigned this month is what your monthly plan has not given a job yet.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        Text("SPENDABLE NOW", fontSize = 11.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v07Euro(summary.cashBalance), fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(
            "This changes when you record real spending, receive income, invest, or move money into/out of Folio savings. It is not reduced just because you made a future budget.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { editSpendable = true }) { Text("Set spendable balance") }

        Spacer(Modifier.height(26.dp))
        V07Panel {
            Text("UNASSIGNED THIS MONTH", fontSize = 11.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(v07Euro(envelope.availableCash), fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "${v07Euro(envelope.expectedIncome)} expected income − ${v07Euro(envelope.committed)} planned = ${v07Euro(envelope.availableCash)} unassigned",
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Planning only. This number can be higher or lower than Spendable now because it ignores old leftovers and includes future commitments for ${month.atDay(1).format(V07MonthFormat)}.",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(34.dp))
        Text("This month", fontSize = 27.sp, fontWeight = FontWeight.Medium)
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
            V07Metric("Savings plan", "−${v07Euro(envelope.savingsCommitment)}")
            V07Divider()
            V07Metric("Unassigned", v07Euro(envelope.availableCash))
        }

        Spacer(Modifier.height(30.dp))
        V08SpendingBreakdown(summary, month)

        Spacer(Modifier.height(34.dp))
        Text("Savings", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Text(
            "Savings are excluded from Spendable now but still count toward net worth.",
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
                if (summary.averageMonthlyOutflow > 0.0) "${String.format("%.1f", summary.emergencyFundMonths)} months covered" else "No spending history yet",
            )
            V07Divider()
            V07Goal("Crash reserve", summary.crashReserveBalance, summary.crashReserveTarget, "Reserved for drawdowns")
            V07Divider()
            V07Metric("General savings", v07Euro(summary.generalSavingsBalance))
        }

        Spacer(Modifier.height(30.dp))
        OutlinedButton(
            onClick = { showFullManager = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(22.dp),
        ) {
            Text("Manage salary, bills, budgets, savings & expenses")
        }
        Text(
            "The detailed manager keeps every existing Folio control; this page is the simpler money overview.",
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(80.dp))
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

    if (showFullManager) {
        ModalBottomSheet(onDismissRequest = { showFullManager = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 760.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Detailed money manager", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showFullManager = false }) { Text("Done") }
                }
                V07MoneyScreen(vm)
            }
        }
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
            Text("Spendable now", fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text(
                "Set the liquid amount Folio should treat as usable today. Savings and portfolio value are tracked separately.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(14) },
                label = { Text("Spendable amount (€)") },
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
