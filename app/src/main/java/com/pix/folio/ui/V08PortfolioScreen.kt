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
import com.pix.folio.model.InvestmentEntrySource
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val V08BoughtFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH)
private val V08InvestmentMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private enum class V08PortfolioGraphMode(val label: String) { VALUE("VALUE"), RETURN("RETURN"), CONTRIBUTIONS("CONTRIBUTIONS") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V08PortfolioScreen(vm: V07ViewModel) {
    val summary = vm.summary
    val scalable = vm.scalableSnapshot
    val valuations = summary.investments.associateWith(vm::v081Valuation)
    val total = scalable?.investmentValue ?: valuations.values.sumOf { it.value }
    val invested = summary.portfolioCostBasis
    val localGain = v081AbsoluteReturn(total, invested)
    val brokerReturn = scalable?.primaryAbsoluteReturn
    val gain = brokerReturn?.absoluteReturn ?: localGain
    val valueHistory = vm.v081PortfolioHistory(total)
    val gainPct = if (scalable == null) {
        v081TimeWeightedReturnPct(valueHistory, summary.investmentTransactions)
    } else {
        0.0
    }
    var graphMode by remember { mutableStateOf(V08PortfolioGraphMode.VALUE) }
    var editHolding by remember { mutableStateOf<InvestmentHolding?>(null) }
    var editTransaction by remember { mutableStateOf<InvestmentTransaction?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val graphSeries = remember(valueHistory, summary.investmentTransactions, graphMode) {
        when (graphMode) {
            V08PortfolioGraphMode.VALUE -> valueHistory
            V08PortfolioGraphMode.CONTRIBUTIONS ->
                v081CumulativeMonthlyContributions(summary.investmentTransactions)
            V08PortfolioGraphMode.RETURN ->
                v081AbsoluteReturnSeries(valueHistory, summary.investmentTransactions)
        }
    }

    val hasEstimatedHolding = scalable == null && valuations.values.any { !it.exact }
    val graphSemanticTrend = when (graphMode) {
        V08PortfolioGraphMode.VALUE -> gain
        V08PortfolioGraphMode.RETURN -> graphSeries.lastOrNull()?.second
        V08PortfolioGraphMode.CONTRIBUTIONS -> null
    }

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
            if (scalable != null) {
                brokerReturn?.let {
                    "Scalable ${it.timeframe} return · ${v07SignedEuro(it.absoluteReturn)}"
                } ?: "Exact broker value from Scalable Capital"
            } else {
                "${v07SignedEuro(gain)} total return · ${String.format(Locale.US, "%+.1f%%", gainPct)} time-weighted"
            },
            fontSize = 13.sp,
            color = if (scalable != null && brokerReturn == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                folioChangeColor(gain)
            },
        )
        if (scalable != null) {
            Text(
                "Source: Scalable Capital · encrypted read-only snapshot · ${scalable.createdAtUtc}",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
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
                semanticTrend = graphSemanticTrend,
                showZeroLine = graphMode == V08PortfolioGraphMode.RETURN,
                signedValues = graphMode == V08PortfolioGraphMode.RETURN,
            )
            Text(
                when (graphMode) {
                    V08PortfolioGraphMode.VALUE -> if (scalable != null) {
                        "Historical curve uses Folio purchase history; today's endpoint is the exact Scalable broker valuation."
                    } else {
                        "Market value across all holdings. Today's endpoint uses exact unit-based values where available."
                    }
                    V08PortfolioGraphMode.RETURN -> if (scalable != null) {
                        "Historical return uses Folio purchase history; today's portfolio value comes from Scalable Capital."
                    } else {
                        "Absolute return in euros: portfolio value minus invested capital. Deposits do not count as performance."
                    }
                    V08PortfolioGraphMode.CONTRIBUTIONS -> "Running contributed total by month. September stays visible, then October adds on top."
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
        if (scalable != null) {
            V07Panel {
                Text("Scalable Capital", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Broker-reported values are authoritative while this snapshot is active.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                V07Metric("Investments", v07Euro(scalable.investmentValue), "Securities + crypto reported by Scalable")
                V07Divider()
                V07Metric("Broker total", v07Euro(scalable.totalValue), "May include broker cash / credit")
                V07Divider()
                V07Metric("Securities", v07Euro(scalable.securitiesValue))
                if (scalable.cryptoValue > 0.0) {
                    V07Divider()
                    V07Metric("Crypto", v07Euro(scalable.cryptoValue))
                }
                brokerReturn?.let {
                    V07Divider()
                    V07Metric(
                        "Scalable return · ${it.timeframe}",
                        v07SignedEuro(it.absoluteReturn),
                        valueColor = folioChangeColor(it.absoluteReturn),
                    )
                }
            }
        } else {
            V08PortfolioIntelligence(
                vm = vm,
                valuations = valuations,
                total = total,
                invested = invested,
                absoluteReturn = gain,
                timeWeightedReturnPct = gainPct,
            )
        }

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Holdings", fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (scalable == null) {
                TextButton(
                    onClick = {
                        vm.refreshMarketPrices()
                        vm.refreshTrackedInvestments()
                    },
                    enabled = !vm.marketRefreshing && !vm.trackingRefreshing,
                ) { Text(if (vm.marketRefreshing || vm.trackingRefreshing) "Updating…" else "Refresh") }
            } else {
                Text("Scalable snapshot", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (scalable != null) {
                "Exact broker holdings from the imported snapshot. Import a newer snapshot in Settings → Connections to refresh them."
            } else {
                "Tap a holding to edit its purchase date, time, and exact owned units."
            },
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        if (scalable != null) {
            if (scalable.holdings.isEmpty()) {
                V07Panel {
                    Text("No Scalable holdings", fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "The imported broker snapshot contains no security holdings.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                scalable.holdings
                    .sortedByDescending { it.valuation }
                    .forEachIndexed { index, brokerHolding ->
                        val localHolding = summary.investments.firstOrNull {
                            it.isin.isNotBlank() && it.isin.equals(brokerHolding.isin, ignoreCase = true)
                        }
                        val allocation = if (total > 0.0) brokerHolding.valuation / total * 100.0 else 0.0
                        V07Metric(
                            label = brokerHolding.name,
                            value = v07Euro(brokerHolding.valuation),
                            detail = buildString {
                                append("${String.format(Locale.US, "%.1f", allocation)}% · Scalable Capital · exact")
                                brokerHolding.quantity?.let { append(" · ${v081Units(it)} units") }
                                if (brokerHolding.quoteOutdated) append(" · quote flagged outdated")
                                append(" · ${brokerHolding.isin}")
                            },
                            onClick = localHolding?.let { holding -> ({ editHolding = holding }) },
                        )
                        if (index != scalable.holdings.lastIndex) V07Divider()
                    }
            }
        } else if (summary.investments.isEmpty()) {
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
                    val holdingGain = valuation.value - holding.amount
                    val holdingReturnPct = if (holding.amount > 0.0) holdingGain / holding.amount * 100.0 else null
                    V07Metric(
                        label = holding.name,
                        value = v07Euro(valuation.value),
                        detail = buildString {
                            append("${String.format(Locale.US, "%.1f", allocation)}% · ${holding.kind.label} · ${valuation.status}")
                            holdingReturnPct?.let { append(" · ${String.format(Locale.US, "%+.1f%%", it)} return") }
                            valuation.units?.let { append(" · ${v081Units(it)} units") }
                            if (timestamp != null) append(" · bought ${timestamp.format(V08BoughtFormat)}")
                            else append(" · add purchase date & time")
                        },
                        onClick = { editHolding = holding },
                        valueColor = holdingReturnPct?.let { folioChangeColor(holdingGain) }
                            ?: MaterialTheme.colorScheme.onBackground,
                    )
                    if (index != summary.investments.lastIndex) V07Divider()
                }
        }

        Spacer(Modifier.height(36.dp))
        Text("Investment activity", fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Text(
            "Every saved purchase appears in its real month. Tap any entry to correct its amount, units, date, or exact time — recurring purchases included.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        val holdingNames = summary.investments.associate { it.id to it.name }
        val activityByMonth = summary.investmentTransactions
            .sortedByDescending { it.purchasedAt }
            .groupBy { YearMonth.from(it.date) }
            .toList()
            .sortedByDescending { it.first }
        val cumulativeByMonth = buildMap<YearMonth, Double> {
            var running = 0.0
            summary.investmentTransactions
                .groupBy { YearMonth.from(it.date) }
                .toSortedMap()
                .forEach { (month, rows) ->
                    running += rows.sumOf { it.amount }
                    put(month, running)
                }
        }

        if (activityByMonth.isEmpty()) {
            V07Panel {
                Text("No purchases recorded yet", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Add an investment or contribution and it will appear here.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            activityByMonth.forEach { (month, rows) ->
                V07Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            month.atDay(1).format(V08InvestmentMonthFormat),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "MONTH TOTAL",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                v07Euro(rows.sumOf { it.amount }),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Cumulative ${v07Euro(cumulativeByMonth[month] ?: 0.0)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    rows.forEachIndexed { index, transaction ->
                        V07Metric(
                            label = holdingNames[transaction.holdingId] ?: "Investment",
                            value = v07Euro(transaction.amount),
                            detail = buildString {
                                append(v08InvestmentSourceLabel(transaction.source))
                                append(" · ")
                                append(transaction.purchasedAt.format(V08BoughtFormat))
                                if (transaction.units > 0.0) {
                                    append(" · ")
                                    append(v081Units(transaction.units))
                                    append(" units")
                                }
                            },
                            onClick = { editTransaction = transaction },
                        )
                        if (index != rows.lastIndex) V07Divider()
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showAdd) {
        V08AddInvestmentSheet(vm = vm, onDismiss = { showAdd = false })
    }

    editHolding?.let { holding ->
        V081HoldingDetailsSheet(
            vm = vm,
            holding = holding,
            initialTimestamp = vm.purchaseDateTimeFor(holding.id),
            initialUnits = vm.ownedUnitsFor(holding.id),
            onDismiss = { editHolding = null },
        )
    }

    editTransaction?.let { transaction ->
        V08InvestmentTransactionSheet(
            vm = vm,
            transaction = transaction,
            holdingName = summary.investments.firstOrNull { it.id == transaction.holdingId }?.name ?: "Investment",
            onDismiss = { editTransaction = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V081HoldingDetailsSheet(
    vm: V07ViewModel,
    holding: InvestmentHolding,
    initialTimestamp: LocalDateTime?,
    initialUnits: Double?,
    onDismiss: () -> Unit,
) {
    val seed = initialTimestamp ?: LocalDateTime.now()
    var dateText by remember(holding.id, initialTimestamp) { mutableStateOf(seed.toLocalDate().toString()) }
    var timeText by remember(holding.id, initialTimestamp) { mutableStateOf(seed.toLocalTime().withSecond(0).withNano(0).toString()) }
    var unitsText by remember(holding.id, initialUnits) { mutableStateOf(initialUnits?.let(::v081Units).orEmpty()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val timestamp = if (date != null && time != null) LocalDateTime.of(date, time) else null
    val units = unitsText.v07Double().takeIf { it > 0.0 }
    val recurring = vm.summary.recurringInvestments.firstOrNull { it.holdingId == holding.id }
    var contributionAmount by remember(holding.id) { mutableStateOf("") }
    var contributionUnits by remember(holding.id) { mutableStateOf("") }
    var contributionMessage by remember(holding.id) { mutableStateOf<String?>(null) }
    val contributionValue = contributionAmount.v07Double()
    val contributionUnitsValue = contributionUnits.v07Double()
    val exactUnitsAlreadyTracked = initialUnits != null && initialUnits > 0.0

    var recurringAmount by remember(holding.id, recurring?.id) {
        mutableStateOf(recurring?.amount?.toString().orEmpty())
    }
    var recurringDay by remember(holding.id, recurring?.id) {
        mutableStateOf(recurring?.dayOfMonth?.toString() ?: "1")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
            Text("First purchase", fontSize = 28.sp, fontWeight = FontWeight.Medium)
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
                label = { Text("Current broker units") },
                supportingText = { Text("Enter the total quantity currently shown by your brokerage. Only this confirmed total is treated as exact.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This edits the earliest saved purchase for this holding. New purchases invalidate the confirmed broker-unit snapshot so Folio cannot silently keep using stale units.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val value = timestamp ?: return@Button
                    vm.setInvestmentPurchaseDateTime(holding.id, value)
                    vm.setInvestmentOwnedUnits(holding.id, units)
                    onDismiss()
                },
                enabled = timestamp != null && !timestamp.isAfter(LocalDateTime.now()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save details") }

            Spacer(Modifier.height(28.dp))
            Text("Add contribution now", fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Text(
                "Record money you already invested in this holding. This immediately increases Invested and reduces Available cash.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(contributionAmount, "Amount (€)", Modifier.weight(1f)) { contributionAmount = it }
                V07NumberField(contributionUnits, "Units bought", Modifier.weight(1f)) { contributionUnits = it }
            }
            Text(
                when {
                    contributionValue > vm.summary.cashBalance + 0.005 ->
                        "Not enough available cash. You currently have ${v07Euro(vm.summary.cashBalance)}."
                    exactUnitsAlreadyTracked && contributionUnitsValue <= 0.0 ->
                        "Enter the units bought so the ETF market value stays in sync with your exact owned units."
                    else ->
                        "Recurring investment below only creates a schedule; it does not record a contribution until it runs."
                },
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = if (contributionValue > vm.summary.cashBalance + 0.005) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val amount = contributionValue
                    vm.addInvestmentContribution(
                        holdingId = holding.id,
                        amount = amount,
                        units = contributionUnitsValue,
                    )
                    contributionMessage = "Added ${v07Euro(amount)} to ${holding.name}"
                    contributionAmount = ""
                    contributionUnits = ""
                },
                enabled = contributionValue > 0.0 &&
                    contributionValue <= vm.summary.cashBalance + 0.005 &&
                    (!exactUnitsAlreadyTracked || contributionUnitsValue > 0.0),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Add contribution") }
            contributionMessage?.let {
                Text(
                    it,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Recurring investment", fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Text(
                if (recurring == null) "Optional monthly contribution." else "Runs automatically on its due day when cash is available.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(recurringAmount, "Amount (€)", Modifier.weight(1f)) { recurringAmount = it }
                V07NumberField(recurringDay, "Day", Modifier.weight(1f)) {
                    recurringDay = it.filter(Char::isDigit).take(2)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val amount = recurringAmount.v07Double()
                    val day = recurringDay.toIntOrNull() ?: 1
                    if (recurring == null) vm.addRecurringInvestment(holding.id, amount, day)
                    else vm.updateRecurringInvestment(recurring.id, amount, day)
                },
                enabled = recurringAmount.v07Double() > 0.0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text(if (recurring == null) "Add recurring investment" else "Save recurring investment") }
            if (recurring != null) {
                TextButton(
                    onClick = { vm.removeRecurringInvestment(recurring.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove recurring investment", color = MaterialTheme.colorScheme.error) }
            }

            Spacer(Modifier.height(22.dp))
            TextButton(
                onClick = {
                    vm.removeInvestment(holding.id)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove investment", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V08InvestmentTransactionSheet(
    vm: V07ViewModel,
    transaction: InvestmentTransaction,
    holdingName: String,
    onDismiss: () -> Unit,
) {
    var amountText by remember(transaction.id) { mutableStateOf(transaction.amount.toString()) }
    var unitsText by remember(transaction.id) {
        mutableStateOf(if (transaction.units > 0.0) v081Units(transaction.units) else "")
    }
    var dateText by remember(transaction.id) { mutableStateOf(transaction.date.toString()) }
    var timeText by remember(transaction.id) {
        mutableStateOf(transaction.time.withSecond(0).withNano(0).toString())
    }

    val amount = amountText.v07Double()
    val units = unitsText.v07Double()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull()
    val purchasedAt = if (date != null && time != null) LocalDateTime.of(date, time) else null
    val valid = amount > 0.0 && units >= 0.0 && purchasedAt != null && !purchasedAt.isAfter(LocalDateTime.now())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp)) {
            Text("Edit purchase", fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text(
                "${holdingName} · ${v08InvestmentSourceLabel(transaction.source)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V07NumberField(amountText, "Amount (€)", Modifier.weight(1f)) { amountText = it }
                V07NumberField(unitsText, "Units bought", Modifier.weight(1f)) { unitsText = it }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it.take(10) },
                label = { Text("Purchase date · YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it.take(5) },
                label = { Text("Purchase time · HH:mm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text(
                if (transaction.source == InvestmentEntrySource.RECURRING) {
                    "This edits this month's recorded recurring purchase only. The recurring amount/day for future months stays unchanged."
                } else {
                    "This corrects the saved purchase history and cost basis. It does not move Available cash again."
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    vm.updateInvestmentTransaction(
                        id = transaction.id,
                        amount = amount,
                        units = units,
                        purchasedAt = purchasedAt ?: return@Button,
                    )
                    onDismiss()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save purchase") }
        }
    }
}

private fun v08InvestmentSourceLabel(source: InvestmentEntrySource): String = when (source) {
    InvestmentEntrySource.INITIAL -> "Initial purchase"
    InvestmentEntrySource.MANUAL -> "Manual purchase"
    InvestmentEntrySource.RECURRING -> "Recurring purchase"
}

private fun v081Units(value: Double): String =
    String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
