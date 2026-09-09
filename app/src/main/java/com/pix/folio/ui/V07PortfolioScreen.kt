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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.data.OpenFigiService
import com.pix.folio.model.Cs2AssetType
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07PortfolioScreen(vm: V07ViewModel) {
    val summary = vm.summary
    var addHolding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InvestmentHolding?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                V07SectionLabel("Portfolio")
                Text(v07Euro(summary.investmentTotal), fontSize = 38.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${v07SignedEuro(summary.portfolioGain)} · ${String.format(Locale.US, "%.1f", summary.portfolioGainPct)}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { addHolding = true }) { Text("Add") }
        }

        Spacer(Modifier.height(20.dp))
        V07TinyStats(
            listOf(
                "Cost" to v07Euro(summary.portfolioCostBasis),
                "Streak" to "${summary.investmentContributionStreak} mo",
                "Assets" to summary.investments.size.toString(),
            )
        )

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V07SectionLabel("Holdings", Modifier.weight(1f))
            TextButton(onClick = { vm.refreshMarketPrices() }, enabled = !vm.marketRefreshing) {
                Text(if (vm.marketRefreshing) "Updating…" else "Refresh")
            }
        }
        vm.marketRefreshLabel?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }

        if (summary.investments.isEmpty()) {
            Text(
                "Add an ETF, stock, fund, or CS2 asset. ISIN lookup and automatic price tracking live here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            summary.investments.sortedByDescending(summary::marketValueFor).forEachIndexed { index, holding ->
                val market = summary.marketValueFor(holding)
                val gain = summary.gainFor(holding)
                V07Metric(
                    label = holding.name,
                    value = v07Euro(market),
                    detail = buildString {
                        append(holding.kind.label)
                        if (holding.isin.isNotBlank()) append(" · ${holding.isin}")
                        if (holding.kind == InvestmentKind.CS2) append(" · ${holding.cs2AssetType.label}")
                        if (gain != 0.0) append(" · ${v07SignedEuro(gain)}")
                    },
                    onClick = { selected = holding },
                )
                if (index != summary.investments.lastIndex) V07Divider()
            }
        }

        Spacer(Modifier.height(28.dp))
        V07SectionLabel("Allocation")
        Spacer(Modifier.height(8.dp))
        summary.allocation.forEachIndexed { index, group ->
            val pct = if (summary.investmentTotal > 0.0) group.amount / summary.investmentTotal * 100.0 else 0.0
            V07Metric(group.name, v07Euro(group.amount), "${String.format(Locale.US, "%.0f", pct)}% of portfolio")
            if (index != summary.allocation.lastIndex) V07Divider()
        }
        Spacer(Modifier.height(100.dp))
    }

    if (addHolding) {
        AddV07InvestmentSheet(
            onDismiss = { addHolding = false },
            onSave = { kind, name, symbol, amount, isin, figi, exchange, units, unitPrice, marketName, cs2Type ->
                vm.addInvestment(kind, name, symbol, amount, isin, figi, exchange, units, unitPrice, marketName, cs2Type)
                addHolding = false
            },
        )
    }

    selected?.let { holding ->
        val fresh = summary.investments.firstOrNull { it.id == holding.id }
        if (fresh != null) {
            V07HoldingSheet(vm, fresh, onDismiss = { selected = null })
        } else {
            selected = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddV07InvestmentSheet(
    onDismiss: () -> Unit,
    onSave: (InvestmentKind, String, String, Double, String, String, String, Double, Double, String, Cs2AssetType) -> Unit,
) {
    var kind by remember { mutableStateOf(InvestmentKind.ETF) }
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isin by remember { mutableStateOf("") }
    var figi by remember { mutableStateOf("") }
    var exchange by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf("") }
    var marketName by remember { mutableStateOf("") }
    var cs2Type by remember { mutableStateOf(Cs2AssetType.OTHER) }
    var lookupText by remember { mutableStateOf<String?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            Text("Add investment", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InvestmentKind.entries) { item ->
                    OutlinedButton(onClick = { kind = item }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (kind == item) "• ${item.label}" else item.label)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (kind != InvestmentKind.CS2) {
                V07TextField(isin, "ISIN (optional)") { isin = it.uppercase().take(12) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            lookingUp = true
                            lookupText = null
                            OpenFigiService.lookupIsin(isin).fold(
                                onSuccess = { rows ->
                                    rows.firstOrNull()?.let { found ->
                                        name = found.name
                                        symbol = found.ticker
                                        figi = found.figi
                                        exchange = found.exchange
                                        kind = found.kind
                                        lookupText = "Found ${found.name}"
                                    } ?: run { lookupText = "No match found" }
                                },
                                onFailure = { lookupText = it.message ?: "Lookup failed" },
                            )
                            lookingUp = false
                        }
                    },
                    enabled = !lookingUp && OpenFigiService.isValidIsin(isin),
                ) { Text(if (lookingUp) "Looking up…" else "Lookup ISIN") }
                lookupText?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(10.dp))
            }

            V07TextField(name, if (kind == InvestmentKind.CS2) "Asset name" else "Name") { name = it }
            Spacer(Modifier.height(10.dp))
            if (kind == InvestmentKind.CS2) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Cs2AssetType.entries) { type ->
                        OutlinedButton(onClick = { cs2Type = type }, shape = RoundedCornerShape(20.dp)) {
                            Text(if (cs2Type == type) "• ${type.label}" else type.label)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                V07TextField(marketName, "Steam Market hash name") { marketName = it }
            } else {
                V07TextField(symbol, "Ticker / price symbol") { symbol = it.uppercase() }
            }
            Spacer(Modifier.height(10.dp))
            V07NumberField(amount, "Amount paid (€)") { amount = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(units, "Units", Modifier.weight(1f)) { units = it }
                V07NumberField(unitPrice, "Unit price", Modifier.weight(1f)) { unitPrice = it }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onSave(
                        kind, name.trim(), symbol.trim(), amount.v07Double(), isin.trim(), figi.trim(), exchange.trim(),
                        units.v07Double(), unitPrice.v07Double(), marketName.trim(), cs2Type,
                    )
                },
                enabled = name.isNotBlank() && amount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Add to portfolio") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V07HoldingSheet(vm: V07ViewModel, holding: InvestmentHolding, onDismiss: () -> Unit) {
    val summary = vm.summary
    val history = summary.priceHistoryFor(holding.id)
    var contribution by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var recurringAmount by remember { mutableStateOf("") }
    var recurringDay by remember { mutableStateOf("1") }
    var manualPrice by remember { mutableStateOf("") }
    var priceSymbol by remember(holding.id) { mutableStateOf(holding.priceSymbol.ifBlank { holding.symbol }) }
    var marketName by remember(holding.id) { mutableStateOf(holding.marketHashName) }
    var note by remember(holding.id) { mutableStateOf(holding.note) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            V07SectionLabel(holding.kind.label)
            Text(holding.name, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text(
                "${v07Euro(summary.marketValueFor(holding))} · cost ${v07Euro(holding.amount)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(22.dp))

            if (history.size >= 2) {
                V07LineChart(history.map { it.close }, Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(8.dp))
            }
            V07TinyStats(
                listOf(
                    "Units" to String.format(Locale.US, "%.4f", summary.unitsFor(holding.id)),
                    "Price" to (summary.latestPriceFor(holding.id)?.let(::v07Euro) ?: "—"),
                    "Gain" to v07SignedEuro(summary.gainFor(holding)),
                )
            )

            Spacer(Modifier.height(24.dp))
            V07SectionLabel("Automatic price")
            Spacer(Modifier.height(8.dp))
            if (holding.kind == InvestmentKind.CS2) {
                V07TextField(marketName, "Steam Market hash name") { marketName = it }
            } else {
                V07TextField(priceSymbol, "Ticker / Yahoo symbol") { priceSymbol = it }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                vm.updateInvestmentTracking(holding.id, priceSymbol, marketName, if (holding.kind == InvestmentKind.CS2) holding.cs2AssetType else null)
                vm.refreshMarketPrices()
            }) { Text("Save tracking & refresh") }

            Spacer(Modifier.height(22.dp))
            V07SectionLabel("Add contribution")
            Spacer(Modifier.height(8.dp))
            V07NumberField(contribution, "Amount (€)") { contribution = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(units, "Units", Modifier.weight(1f)) { units = it }
                V07NumberField(purchasePrice, "Price", Modifier.weight(1f)) { purchasePrice = it }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.addInvestmentContribution(holding.id, contribution.v07Double(), units.v07Double(), purchasePrice.v07Double())
                    contribution = ""; units = ""; purchasePrice = ""
                },
                enabled = contribution.v07Double() > 0.0,
            ) { Text("Record purchase") }

            Spacer(Modifier.height(22.dp))
            V07SectionLabel("Recurring purchase")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(recurringAmount, "Amount (€)", Modifier.weight(1f)) { recurringAmount = it }
                V07NumberField(recurringDay, "Day", Modifier.weight(1f)) { recurringDay = it.filter(Char::isDigit).take(2) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.addRecurringInvestment(holding.id, recurringAmount.v07Double(), recurringDay.toIntOrNull() ?: 1)
                    recurringAmount = ""
                },
                enabled = recurringAmount.v07Double() > 0.0,
            ) { Text("Add monthly investment") }

            Spacer(Modifier.height(22.dp))
            V07SectionLabel("Manual price")
            Spacer(Modifier.height(8.dp))
            V07NumberField(manualPrice, "Market price (€)") { manualPrice = it }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.addInvestmentPrice(holding.id, manualPrice.v07Double(), symbol = priceSymbol); manualPrice = "" },
                enabled = manualPrice.v07Double() > 0.0,
            ) { Text("Save today's price") }

            Spacer(Modifier.height(22.dp))
            V07TextField(note, "Note") { note = it }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.updateInvestmentDetails(holding.id, holding.tags, note) }) { Text("Save note") }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { vm.removeInvestment(holding.id); onDismiss() }) {
                Text("Remove investment", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
