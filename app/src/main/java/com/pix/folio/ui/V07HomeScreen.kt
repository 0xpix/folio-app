package com.pix.folio.ui

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
import com.pix.folio.model.TimelineEntryType
import java.time.YearMonth
import java.util.Locale

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
        addAll(summary.balanceHistory.takeLast(120).map { it.value })
        if (lastOrNull() != summary.totalBalance) add(summary.totalBalance)
    }
    val historyStart = history.firstOrNull() ?: summary.totalBalance
    val historyChange = summary.totalBalance - historyStart
    val historyPct = if (historyStart > 0.0) historyChange / historyStart * 100.0 else 0.0
    val recent = summary.transactionTimeline().take(5)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("FOLIO", fontSize = 13.sp, letterSpacing = 2.2.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Net Worth", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(v07Euro(summary.totalBalance), fontSize = 43.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "${v07SignedEuro(historyChange)}  ·  ${if (historyPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", historyPct)}%",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        if (history.size >= 2) {
            V07LineChart(history, Modifier.fillMaxWidth().height(92.dp))
        } else {
            Spacer(Modifier.height(92.dp))
        }

        Spacer(Modifier.height(24.dp))
        V07TinyStats(
            listOf(
                "Cash" to v07Euro(summary.cashBalance),
                "Savings" to v07Euro(summary.totalSavings),
                "Portfolio" to v07Euro(summary.investmentTotal),
            )
        )

        Spacer(Modifier.height(28.dp))
        V07Panel(onClick = onOpenMoney) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(month.atDay(1).format(V07MonthFormat), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${v07Euro(plan.committed)} committed of ${v07Euro(plan.expectedIncome)} expected",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(v07SignedEuro(plan.projectedLeft), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    Text("projected left", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Savings", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenMoney) { Text("Manage") }
        }
        V07Panel(onClick = onOpenMoney) {
            V07Goal(
                label = "Emergency fund",
                current = summary.emergencyFundBalance,
                target = summary.emergencyFundTarget,
                detail = if (summary.averageMonthlyOutflow > 0.0) {
                    "${String.format(Locale.US, "%.1f", summary.emergencyFundMonths)} months covered"
                } else {
                    "Tap to add money manually"
                },
            )
            V07Divider()
            V07Goal(
                label = "Crash reserve",
                current = summary.crashReserveBalance,
                target = summary.crashReserveTarget,
                detail = "Tap Money to manage savings",
            )
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (recent.isNotEmpty()) TextButton(onClick = onOpenMoney) { Text("View all") }
        }
        if (recent.isEmpty()) {
            Text(
                "Your latest income, expenses, bills and investments will appear here.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            recent.forEachIndexed { index, row ->
                val prefix = when (row.type) {
                    TimelineEntryType.INCOME -> "+"
                    TimelineEntryType.EXPENSE, TimelineEntryType.PAYMENT, TimelineEntryType.INVESTMENT -> "−"
                }
                V07Metric(
                    row.name,
                    "$prefix${v07Euro(kotlin.math.abs(row.amount))}",
                    "${row.detail} · ${row.date.format(V07ShortDateFormat)}",
                )
                if (index != recent.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onOpenPortfolio, modifier = Modifier.fillMaxWidth()) {
            Text("Open portfolio →")
        }

        Spacer(Modifier.height(90.dp))
    }
}
