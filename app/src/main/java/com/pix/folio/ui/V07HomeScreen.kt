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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
internal fun V07HomeScreen(
    vm: V07ViewModel,
    onOpenMoney: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onSettings: () -> Unit,
) {
    val summary = vm.summary
    val month = YearMonth.now()
    val plan = summary.moneyPlan(month)
    val history = buildList {
        addAll(summary.balanceHistory.takeLast(180).map { it.value })
        if (lastOrNull() != summary.totalBalance) add(summary.totalBalance)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FOLIO", fontSize = 13.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your money, quietly.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(34.dp))
        V07SectionLabel("NET WORTH")
        Spacer(Modifier.height(5.dp))
        Text(v07Euro(summary.totalBalance), fontSize = 44.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(
            "Cash + savings + investments",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(22.dp))
        if (history.size >= 2) {
            V07LineChart(history, Modifier.fillMaxWidth().height(132.dp))
        } else {
            Text("Net-worth history begins as you use Folio.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(80.dp))
        }

        Spacer(Modifier.height(20.dp))
        V07TinyStats(
            listOf(
                "Cash" to v07Euro(summary.cashBalance),
                "Saved" to v07Euro(summary.totalSavings),
                "Invested" to v07Euro(summary.investmentTotal),
            )
        )

        Spacer(Modifier.height(34.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenMoney),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V07SectionLabel(month.atDay(1).format(V07MonthFormat), Modifier.weight(1f))
            Text("Money →", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        V07Metric("Expected income", v07Euro(plan.expectedIncome), "Includes income assigned to this budget month")
        V07Divider()
        V07Metric("Committed", v07Euro(plan.committed), "Bills + investments + expenses + savings")
        V07Divider()
        V07Metric(
            "Projected left",
            v07SignedEuro(plan.projectedLeft),
            if (plan.projectedLeft > 0.0) "Available to allocate or keep as cash" else "Plan exceeds expected income",
            onClick = onOpenMoney,
        )

        Spacer(Modifier.height(34.dp))
        V07SectionLabel("SAFETY NET")
        Spacer(Modifier.height(5.dp))
        V07Goal(
            label = "Emergency fund",
            current = summary.emergencyFundBalance,
            target = summary.emergencyFundTarget,
            detail = if (summary.averageMonthlyOutflow > 0.0) {
                "${String.format("%.1f", summary.emergencyFundMonths)} months of recent spending"
            } else {
                "Add spending history to calculate coverage"
            },
            onClick = onOpenMoney,
        )
        V07Goal(
            label = "Crash reserve",
            current = summary.crashReserveBalance,
            target = summary.crashReserveTarget,
            detail = "${(summary.crashReserveProgress * 100).roundToInt()}% ready for a market drawdown",
            onClick = onOpenMoney,
        )

        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                V07SectionLabel("PORTFOLIO")
                Text(v07Euro(summary.investmentTotal), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${v07SignedEuro(summary.portfolioGain)} vs cost basis",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenPortfolio) { Text("Open →") }
        }

        Spacer(Modifier.height(26.dp))
        if (vm.canUndo) {
            TextButton(onClick = vm::undoLastChange) {
                Text("Undo last change")
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}
