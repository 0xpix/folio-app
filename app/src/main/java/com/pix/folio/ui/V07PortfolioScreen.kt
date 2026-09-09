package com.pix.folio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun V07PortfolioScreen(vm: V07ViewModel) {
    val summary = vm.summary
    var addHolding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InvestmentHolding?>(null) }
    val total = vm.trackedPortfolioTotal
    val gain = vm.trackedPortfolioGain
    val gainPct = vm.trackedPortfolioGainPct
    val portfolioHistory = vm.trackedPortfolioHistory

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
                "Every investment added together from its own purchase date.",
                fontSize = 11.sp,
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
            "For stocks and ETFs, Folio rebuilds value from your purchase date using key-free market history.",
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
                    "Enter what you paid and when you bought it. Folio handles the price history and current value.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            summary.investments.sortedByDescending(vm::trackedMarketValue).forEachIndexed { index, holding ->
                val market = vm.trackedMarketValue(holding)
                val rowGain = vm.trackedGain(holding)
                val date = vm.purchaseDateFor(holding.id)
                V07Metric(
                    label = holding.name,
                    value = v07Euro(market),
                    detail = buildString {
                        append(holding.kind.label)
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
        AddV07InvestmentSheet(
            onDismiss = { addHolding = false },
            onSave = { kind, name, symbol, amount, purchaseDate, isin, figi, exchange, marketName, cs2Type ->
                vm.addInvestment(
                    kind = kind,
                    name = name,
                    symbol = symbol,
                    amount = amount,
                    isin = isin,
                    figi = figi,
                    exchange = exchange,
                    marketHashName = marketName,
                    cs2AssetType = cs2Type,
                    purchaseDate = purchaseDate,
                )
                vm.refreshMarketPrices()
                addHolding = false
            },
        )
    }

    selected?.let { holding ->
        val fresh = summary.investments.firstOrNull { it.id == holding.id }
        if (fresh != null) V07HoldingSheet(vm, fresh, onDismiss = { selected = null })
        else selected = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddV07InvestmentSheet(
    onDismiss: () -> Unit,
    onSave: (InvestmentKind, String, String, Double, LocalDate, String, String, String, String, Cs2AssetType) -> Unit,
) {
    var kind by remember { mutableStateOf(InvestmentKind.ETF) }
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var isin by remember { mutableStateOf("") }
    var figi by remember { mutableStateOf("") }
    var exchange by remember { mutableStateOf("") }
    var marketName by remember { mutableStateOf("") }
    var cs2Type by remember { mutableStateOf(Cs2AssetType.OTHER) }
    var lookupText by remember { mutableStateOf<String?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val parsedDate = runCatching { LocalDate.parse(purchaseDate) }.getOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            Text("Add investment", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
            Text(
                "Tell Folio what you bought, what you paid, and the purchase date.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
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
            V07TextField(purchaseDate, "Purchase date · YYYY-MM-DD") { purchaseDate = it.take(10) }
            Spacer(Modifier.height(8.dp))
            Text(
                "You do not need to enter the old share price. Folio uses the market close around this date as the starting point.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val date = parsedDate ?: return@Button
                    onSave(
                        kind, name.trim(), symbol.trim(), amount.v07Double(), date,
                        isin.trim(), figi.trim(), exchange.trim(), marketName.trim(), cs2Type,
                    )
                },
                enabled = name.isNotBlank() && amount.v07Double() > 0.0 && parsedDate != null && !parsedDate.isAfter(LocalDate.now()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Track investment") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V07HoldingSheet(vm: V07ViewModel, holding: InvestmentHolding, onDismiss: () -> Unit) {
    val value = vm.trackedMarketValue(holding)
    val gain = vm.trackedGain(holding)
    val gainPct = vm.trackedGainPct(holding)
    val valueHistory = vm.trackedValueHistory(holding)
    val history = vm.trackedHistories[holding.id]
    val storedDate = vm.purchaseDateFor(holding.id)
    var purchaseDate by remember(holding.id, storedDate) { mutableStateOf((storedDate ?: LocalDate.now()).toString()) }
    var priceSymbol by remember(holding.id) { mutableStateOf(holding.priceSymbol.ifBlank { holding.symbol }) }
    var marketName by remember(holding.id) { mutableStateOf(holding.marketHashName) }
    var recurringAmount by remember { mutableStateOf("") }
    var recurringDay by remember { mutableStateOf("1") }
    val parsedDate = runCatching { LocalDate.parse(purchaseDate) }.getOrNull()

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
                "${v07SignedEuro(gain)}  ·  ${if (gainPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", gainPct)}% since purchase",
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { parsedDate?.let { vm.setInvestmentPurchaseDate(holding.id, it) } },
                enabled = parsedDate != null && !parsedDate.isAfter(LocalDate.now()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save date & rebuild graph") }

            Spacer(Modifier.height(26.dp))
            Text("Market tracking", fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Text(
                if (holding.kind == InvestmentKind.CS2) "Steam Community Market indicative price" else "Key-free Yahoo Finance chart data; exchange delay is shown when reported.",
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
