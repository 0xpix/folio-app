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
import kotlin.math.abs

@Composable
internal fun V08HomeScreen(
    vm: V07ViewModel,
    onOpenMoney: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onSettings: () -> Unit,
) {
    val summary = vm.summary
    val month = vm.suggestedBudgetMonth()
    val envelope = vm.budgetEnvelope(month)
    val netWorth = vm.trackedNetWorth
    val startDate = FolioStartMonth.atDay(1)
    val historyStartMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val history = summary.balanceHistory.filter { it.atMillis >= historyStartMillis }
    val recent = summary.transactionTimeline().filter { !it.date.isBefore(startDate) }.take(4)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("F.", fontSize = 22.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }

        Spacer(Modifier.height(34.dp))
        Text("NET WORTH", fontSize = 11.sp, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v07Euro(netWorth), fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(18.dp))
        V08NetWorthHistory(history = history, currentValue = netWorth)

        Spacer(Modifier.height(30.dp))
        V07TinyStats(
            listOf(
                "BANK" to v07Euro(summary.cashBalance),
                "SAVINGS" to v07Euro(summary.totalSavings),
                "INVESTED" to v07Euro(vm.trackedPortfolioTotal),
            )
        )

        Spacer(Modifier.height(34.dp))
        V07Panel(onClick = onOpenMoney) {
            Text("FREE TO PLAN THIS MONTH", fontSize = 11.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(v07Euro(envelope.availableCash), fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "This is a planning number: ${v07Euro(envelope.expectedIncome)} expected income minus bills, investments, spending budgets and savings. Your actual bank balance is ${v07Euro(summary.cashBalance)}.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(30.dp))
        V08SpendingBreakdown(summary, month)

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (recent.isNotEmpty()) TextButton(onClick = onOpenMoney) { Text("All") }
        }
        if (recent.isEmpty()) {
            Text(
                "Income, spending, bills and investments will appear here as you use Folio.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recent.forEachIndexed { index, row ->
                val sign = when (row.type) {
                    TimelineEntryType.INCOME -> "+"
                    else -> "−"
                }
                V07Metric(
                    row.name,
                    "$sign${v07Euro(abs(row.amount))}",
                    "${row.detail} · ${row.date.format(V07ShortDateFormat)}",
                )
                if (index != recent.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(22.dp))
        TextButton(onClick = onOpenPortfolio, modifier = Modifier.fillMaxWidth()) {
            Text("Open portfolio →", fontSize = 16.sp)
        }
        Spacer(Modifier.height(80.dp))
    }
}
