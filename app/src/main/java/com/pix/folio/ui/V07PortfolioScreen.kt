package com.pix.folio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun V07PortfolioScreen(vm: V07ViewModel) {
    val summary = vm.summary
    var addHolding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InvestmentHolding?>(null) }
    val total = vm.v081PortfolioTotal
    val gain = vm.v081PortfolioGain
    val gainPct = vm.v081PortfolioGainPct
    val portfolioHistory = vm.v081PortfolioHistory
    val valuations = summary.investments.associateWith(vm::v081Valuation)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Portfolio", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text(v07Euro(total), fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    "${v07SignedEuro(gain)}  ·  ${if (gainPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", gainPct)}% since purchase",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { addHolding = true }) { Text("+ Add") }
        }

        Spacer(Modifier.height(28.dp))
        if (portfolioHistory.size >= 2) {
            Text("Combined portfolio", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                "Every investment added together from its own purchase date; today's endpoint uses unit-based values when available.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            V07LineChart(portfolioHistory.map { it.second }, Modifier.fillMaxWidth().height(170.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    portfolioHistory.first().first.format(V07ShortDateFormat),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    portfolioHistory.last().first.format(V07ShortDateFormat),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (summary.investments.isNotEmpty()) {
            V07Panel {
                Text("Portfolio graph is getting ready", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Add purchase dates and refresh market history. Folio will combine every holding into one curve.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        V07TinyStats(
            listOf(
                "Cost" to v07Euro(summary.portfolioCostBasis),
                "Assets" to summary.investments.size.toString(),
                "Tracked" to summary.investments.count { vm.purchaseDateFor(it.id) != null }.toString(),
            )
        )

        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Investments", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    vm.refreshMarketPrices()
                    vm.refreshTrackedInvestments()
                },
                enabled = !vm.marketRefreshing && !vm.trackingRefreshing,
            ) { Text(if (vm.marketRefreshing || vm.trackingRefreshing) "Updating…" else "Refresh") }
        }
        Text(
            "Folio prefers owned units × the freshest EUR quote. Holdings without complete units remain clearly estimated.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        vm.marketRefreshLabel?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        if (summary.investments.isEmpty()) {
            V07Panel(onClick = { addHolding = true }) {
                Text("Add your first investment", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Enter what you paid, the exact units, and when you bought it.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            summary.investments
                .sortedByDescending { valuations.getValue(it).value }
                .forEachIndexed { index, holding ->
                    val valuation = valuations.getValue(holding)
                    val rowGain = valuation.value - holding.amount
                    val date = vm.purchaseDateFor(holding.id)
                    V07Metric(
                        label = holding.name,
                        value = v07Euro(valuation.value),
                        detail = buildString {
                            append("${holding.kind.label} · ${valuation.status}")
                            if (date != null) append(" · bought ${date.format(V07ShortDateFormat)}")
                            else if (holding.kind != InvestmentKind.CS2) append(" · add purchase date")
                            if (rowGain != 0.0) append(" · ${v07SignedEuro(rowGain)}")
                        },
                        onClick = { selected = holding },
                    )
                    if (index != summary.investments.lastIndex) V07Divider()
                }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (addHolding) {
        V08AddInvestmentSheet(vm = vm, onDismiss = { addHolding = false })
    }

    selected?.let { holding ->
        val fresh = summary.investments.firstOrNull { it.id == holding.id }
        if (fresh != null) V07HoldingSheet(vm, fresh, onDismiss = { selected = null })
        else selected = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V07HoldingSheet(vm: V07ViewModel, holding: InvestmentHolding, onDismiss: () -> Unit) {
    val valuation = vm.v081Valuation(holding)
    val value = valuation.value
    val gain = value - holding.amount
    val gainPct = if (holding.amount > 0.0) gain / holding.amount * 100.0 else 0.0
    val valueHistory = vm.trackedValueHistory(holding)
    val history = vm.trackedHistories[holding.id]
    val storedDate = vm.purchaseDateFor(holding.id)
    val storedUnits = vm.ownedUnitsFor(holding.id)
    var purchaseDate by remember(holding.id, storedDate) { mutableStateOf((storedDate ?: LocalDate.now()).toString()) }
    var unitsText by remember(holding.id, storedUnits) { mutableStateOf(storedUnits?.let(::formatUnitsV07).orEmpty()) }
    var priceSymbol by remember(holding.id) { mutableStateOf(holding.priceSymbol.ifBlank { holding.symbol }) }
    var marketName by remember(holding.id) { mutableStateOf(holding.marketHashName) }
    var recurringAmount by remember { mutableStateOf("") }
    var recurringDay by remember { mutableStateOf("1") }
    val parsedDate = runCatching { LocalDate.parse(purchaseDate) }.getOrNull()
    val parsedUnits = unitsText.v07Double().takeIf { it > 0.0 }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            V07SectionLabel(holding.kind.label)
            Text(holding.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(v07Euro(value), fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${v07SignedEuro(gain)}  ·  ${if (gainPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", gainPct)}% · ${valuation.status}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            if (valueHistory.size >= 2) {
                V07LineChart(valueHistory.map { it.second }, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(valueHistory.first().first.format(V07ShortDateFormat), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(valueHistory.last().first.format(V07ShortDateFormat), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    history?.let { "${it.symbol} · ${it.source}" } ?: "Market history",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (holding.kind != InvestmentKind.CS2) {
                V07Panel {
                    Text("Graph unavailable", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        vm.trackingErrors[holding.id] ?: "Add the purchase date, then refresh market history.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("Purchase", fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            V07Metric("Cost", v07Euro(holding.amount))
            V07TextField(purchaseDate, "Purchase date · YYYY-MM-DD") { purchaseDate = it.take(10) }
            if (holding.kind != InvestmentKind.CS2) {
                Spacer(Modifier.height(8.dp))
                V07NumberField(unitsText, "Current units owned") { unitsText = it }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    parsedDate?.let { vm.setInvestmentPurchaseDate(holding.id, it) }
                    if (holding.kind != InvestmentKind.CS2) vm.setInvestmentOwnedUnits(holding.id, parsedUnits)
                },
                enabled = parsedDate != null && !parsedDate.isAfter(LocalDate.now()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save purchase details") }

            Spacer(Modifier.height(26.dp))
            Text("Market tracking", fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Text(
                if (holding.kind == InvestmentKind.CS2) "Steam Community Market indicative price" else "Key-free Yahoo Finance data; exchange delay is shown when reported.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (holding.kind == InvestmentKind.CS2) {
                V07TextField(marketName, "Steam Market hash name") { marketName = it }
            } else {
                V07TextField(priceSymbol, "Ticker / Yahoo symbol") { priceSymbol = it.uppercase() }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.updateInvestmentTracking(holding.id, priceSymbol, marketName, if (holding.kind == InvestmentKind.CS2) holding.cs2AssetType else null)
                    vm.refreshMarketPrices()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save tracking & refresh") }

            Spacer(Modifier.height(26.dp))
            Text("Monthly investment", fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add recurring investment") }

            Spacer(Modifier.height(28.dp))
            TextButton(
                onClick = { vm.removeInvestment(holding.id); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove investment", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun formatUnitsV07(value: Double): String =
    String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
