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
    val total = vm.v081PortfolioTotal
    val gain = vm.v081PortfolioGain
    val gainPct = vm.v081PortfolioGainPct
    val valueHistory = vm.v081PortfolioHistory
    var graphMode by remember { mutableStateOf(V08PortfolioGraphMode.VALUE) }
    var editHolding by remember { mutableStateOf<InvestmentHolding?>(null) }
    var showAdd by remember { mutableStateOf(false) }
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

    val valuations = summary.investments.associateWith(vm::v081Valuation)
    val hasEstimatedHolding = valuations.values.any { !it.exact }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("Portfolio", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) { Text("+ Add") }
        }
        Spacer(Modifier.height(8.dp))
        Text(v07Euro(total), fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(
            "${v07SignedEuro(gain)} · ${String.format(Locale.US, "%+.1f%%", gainPct)} since purchase",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasEstimatedHolding) {
            Text(
                "Some holdings are estimated. Tap one and enter the exact units from your brokerage for broker-style valuation when the market quote is in EUR.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

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
                    V08PortfolioGraphMode.VALUE -> "Market value across all holdings. Today's endpoint uses exact unit-based values where available."
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
            "Tap a holding to edit its purchase date, time, and exact owned units.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        if (summary.investments.isEmpty()) {
            V07Panel(onClick = { showAdd = true }) {
                Text("Add your first investment", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Text("Add exact units, purchase date and time, or resolve an ETF/stock by ISIN.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            summary.investments
                .sortedByDescending { holding -> valuations[holding]?.value ?: 0.0 }
                .forEachIndexed { index, holding ->
                    val valuation = valuations.getValue(holding)
                    val timestamp = vm.purchaseDateTimeFor(holding.id)
                    val allocation = if (total > 0.0) valuation.value / total * 100.0 else 0.0
                    V07Metric(
                        label = holding.name,
                        value = v07Euro(valuation.value),
                        detail = buildString {
                            append("${String.format(Locale.US, "%.1f", allocation)}% · ${holding.kind.label} · ${valuation.status}")
                            valuation.units?.let { append(" · ${v081Units(it)} units") }
                            if (timestamp != null) append(" · bought ${timestamp.format(V08BoughtFormat)}")
                            else append(" · add purchase date & time")
                        },
                        onClick = { editHolding = holding },
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

    if (showAdd) {
        V08AddInvestmentSheet(vm = vm, onDismiss = { showAdd = false })
    }

    editHolding?.let { holding ->
        V081HoldingDetailsSheet(
            holding = holding,
            initialTimestamp = vm.purchaseDateTimeFor(holding.id),
            initialUnits = vm.ownedUnitsFor(holding.id),
            onDismiss = { editHolding = null },
            onSave = { timestamp, units ->
                vm.setInvestmentPurchaseDateTime(holding.id, timestamp)
                vm.setInvestmentOwnedUnits(holding.id, units)
                editHolding = null
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
private fun V081HoldingDetailsSheet(
    holding: InvestmentHolding,
    initialTimestamp: LocalDateTime?,
    initialUnits: Double?,
    onDismiss: () -> Unit,
    onSave: (LocalDateTime, Double?) -> Unit,
) {
    val seed = initialTimestamp ?: LocalDateTime.now()
    var dateText by remember(holding.id, initialTimestamp) { mutableStateOf(seed.toLocalDate().toString()) }
    var timeText by remember(holding.id, initialTimestamp) { mutableStateOf(seed.toLocalTime().withSecond(0).withNano(0).toString()) }
    var unitsText by remember(holding.id, initialUnits) { mutableStateOf(initialUnits?.let(::v081Units).orEmpty()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val timestamp = if (date != null && time != null) LocalDateTime.of(date, time) else null
    val units = unitsText.v07Double().takeIf { it > 0.0 }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
            Text("Purchase details", fontSize = 28.sp, fontWeight = FontWeight.Medium)
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
            OutlinedTextField(
                value = unitsText,
                onValueChange = { unitsText = it },
                label = { Text("Current units owned") },
                supportingText = { Text("Use the exact fractional quantity shown by your brokerage.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Date and time stay as purchase metadata. Exact current valuation uses your owned units only when the resolved market quote is EUR; otherwise Folio labels the value as estimated instead of silently mixing currencies.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(timestamp ?: return@Button, units) },
                enabled = timestamp != null && !timestamp.isAfter(LocalDateTime.now()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save details") }
        }
    }
}

private fun v081Units(value: Double): String =
    String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
