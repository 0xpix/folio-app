package com.pix.folio.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.model.InvestmentHolding
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val V08BoughtFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH)
private enum class V08PortfolioGraphMode(val label: String) { VALUE("VALUE"), RETURN("RETURN"), CONTRIBUTIONS("CONTRIBUTIONS") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V08PortfolioScreen(vm: V07ViewModel) {
    val summary = vm.summary
    val total = vm.trackedPortfolioTotal
    val gain = vm.trackedPortfolioGain
    val gainPct = vm.trackedPortfolioGainPct
    val valueHistory = vm.trackedPortfolioHistory
    var graphMode by remember { mutableStateOf(V08PortfolioGraphMode.VALUE) }
    var editTimestamp by remember { mutableStateOf<InvestmentHolding?>(null) }
    var showFullManager by remember { mutableStateOf(false) }

    val graphSeries = remember(valueHistory, summary.investmentTransactions, graphMode) {
        when (graphMode) {
            V08PortfolioGraphMode.VALUE -> valueHistory
            V08PortfolioGraphMode.CONTRIBUTIONS -> valueHistory.map { (date, _) ->
                date to summary.investmentTransactions
                    .filter { !it.date.isAfter(date) }
                    .sumOf { it.amount }
            }
            V08PortfolioGraphMode.RETURN -> valueHistory.map { (date, value) ->
                val contributed = summary.investmentTransactions
                    .filter { !it.date.isAfter(date) }
                    .sumOf { it.amount }
                date to (value - contributed)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("Portfolio", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(v07Euro(total), fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(
            "${v07SignedEuro(gain)} · ${String.format(Locale.US, "%+.1f%%", gainPct)} since purchase",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            V08PortfolioGraphMode.entries.forEach { option ->
                TextButton(onClick = { graphMode = option }, modifier = Modifier.weight(1f)) {
                    Text(if (graphMode == option) "• ${option.label}" else option.label, fontSize = 10.sp)
                }
            }
        }
        if (graphSeries.size >= 2) {
            V08PortfolioHistory(
                history = graphSeries,
                modifier = Modifier.fillMaxWidth().height(170.dp),
            )
            Text(
                when (graphMode) {
                    V08PortfolioGraphMode.VALUE -> "Market value across all holdings."
                    V08PortfolioGraphMode.RETURN -> "Market value minus contributions recorded by that date."
                    V08PortfolioGraphMode.CONTRIBUTIONS -> "How much you contributed over time."
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            V07Panel {
                Text("Portfolio history", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Add a purchase date to each tracked investment and Folio will reconstruct the combined curve.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        V08PortfolioIntelligence(vm)

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Holdings", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    vm.refreshMarketPrices()
                    vm.refreshTrackedInvestments()
                },
                enabled = !vm.marketRefreshing && !vm.trackingRefreshing,
            ) { Text(if (vm.marketRefreshing || vm.trackingRefreshing) "Updating…" else "Refresh") }
        }
        Text(
            "Purchase time is stored locally together with the purchase date. Tap a holding to correct it.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        if (summary.investments.isEmpty()) {
            V07Panel(onClick = { showFullManager = true }) {
                Text("Add your first investment", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Text("The full portfolio manager handles ISIN lookup, tracking and recurring contributions.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            summary.investments.sortedByDescending(vm::trackedMarketValue).forEachIndexed { index, holding ->
                val market = vm.trackedMarketValue(holding)
                val timestamp = vm.purchaseDateTimeFor(holding.id)
                val allocation = if (total > 0.0) market / total * 100.0 else 0.0
                V07Metric(
                    label = holding.name,
                    value = v07Euro(market),
                    detail = buildString {
                        append("${String.format(Locale.US, "%.1f", allocation)}% · ${holding.kind.label}")
                        if (timestamp != null) append(" · bought ${timestamp.format(V08BoughtFormat)}")
                        else append(" · add purchase date & time")
                    },
                    onClick = { editTimestamp = holding },
                )
                if (index != summary.investments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(30.dp))
        OutlinedButton(
            onClick = { showFullManager = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(22.dp),
        ) { Text("Open full portfolio manager") }
        Spacer(Modifier.height(80.dp))
    }

    editTimestamp?.let { holding ->
        V08PurchaseTimestampSheet(
            holding = holding,
            initial = vm.purchaseDateTimeFor(holding.id),
            onDismiss = { editTimestamp = null },
            onSave = {
                vm.setInvestmentPurchaseDateTime(holding.id, it)
                editTimestamp = null
            },
        )
    }

    if (showFullManager) {
        ModalBottomSheet(onDismissRequest = { showFullManager = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 760.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Full portfolio manager", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showFullManager = false }) { Text("Done") }
                }
                V07PortfolioScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V08PurchaseTimestampSheet(
    holding: InvestmentHolding,
    initial: LocalDateTime?,
    onDismiss: () -> Unit,
    onSave: (LocalDateTime) -> Unit,
) {
    val seed = initial ?: LocalDateTime.now()
    var dateText by remember(holding.id, initial) { mutableStateOf(seed.toLocalDate().toString()) }
    var timeText by remember(holding.id, initial) { mutableStateOf(seed.toLocalTime().withSecond(0).withNano(0).toString()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val timestamp = if (date != null && time != null) LocalDateTime.of(date, time) else null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
            Text("Purchase date & time", fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text(holding.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it.take(10) },
                label = { Text("Date · YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it.take(5) },
                label = { Text("Time · HH:mm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The clock time is stored as purchase metadata. Daily market graphs still use historical closes, because Folio does not pretend to have execution-grade intraday prices.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(timestamp ?: return@Button) },
                enabled = timestamp != null && !timestamp.isAfter(LocalDateTime.now()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save purchase time") }
        }
    }
}
