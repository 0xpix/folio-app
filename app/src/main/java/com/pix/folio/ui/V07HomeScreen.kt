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
import com.pix.folio.data.FolioStartMonth
import com.pix.folio.model.TimelineEntryType
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun V07HomeScreen(
    vm: V07ViewModel,
    onOpenMoney: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onSettings: () -> Unit,
) {
    val summary = vm.summary
    val netWorth = vm.trackedNetWorth
    val portfolio = vm.trackedPortfolioTotal
    val month = vm.suggestedBudgetMonth()
    val envelope = vm.budgetEnvelope(month)
    val startDate = FolioStartMonth.atDay(1)
    val historyStartMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val history = buildList {
        addAll(summary.balanceHistory.filter { it.atMillis >= historyStartMillis }.takeLast(120).map { it.value })
        if (lastOrNull() != netWorth) add(netWorth)
    }
    val historyStart = history.firstOrNull() ?: netWorth
    val historyChange = netWorth - historyStart
    val historyPct = if (historyStart > 0.0) historyChange / historyStart * 100.0 else 0.0
    val recent = summary.transactionTimeline().filter { !it.date.isBefore(startDate) }.take(5)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("FOLIO", fontSize = 15.sp, letterSpacing = 2.6.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(34.dp))
        Text("Net Worth", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(v07Euro(netWorth), fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Text(
            "${v07SignedEuro(historyChange)}  ·  ${if (historyPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", historyPct)}% · since Sep 2026",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (history.size >= 2) {
            V07LineChart(history, Modifier.fillMaxWidth().height(124.dp))
        } else {
            Spacer(Modifier.height(124.dp))
        }

        Spacer(Modifier.height(28.dp))
        V07TinyStats(
            listOf(
                "Cash" to v07Euro(summary.cashBalance),
                "Savings" to v07Euro(summary.totalSavings),
                "Portfolio" to v07Euro(portfolio),
            )
        )

        Spacer(Modifier.height(34.dp))
        V07Panel(onClick = onOpenMoney) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(month.atDay(1).format(V07MonthFormat), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${v07Euro(envelope.committed)} planned from ${v07Euro(envelope.expectedIncome)} salary/income",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Previous-month cash is excluded",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(v07Euro(envelope.availableCash), fontSize = 27.sp, fontWeight = FontWeight.Medium)
                    Text("available", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Savings", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
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
                    "Tap to add your existing savings"
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

        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (recent.isNotEmpty()) TextButton(onClick = onOpenMoney) { Text("View all") }
        }
        if (recent.isEmpty()) {
            Text(
                "Your latest income, expenses, bills and investments from September 2026 onward will appear here.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
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

        Spacer(Modifier.height(22.dp))
        TextButton(onClick = onOpenPortfolio, modifier = Modifier.fillMaxWidth()) {
            Text("Open portfolio →", fontSize = 16.sp)
        }

        Spacer(Modifier.height(90.dp))
    }
}
