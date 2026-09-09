package com.pix.folio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentTag
import com.pix.folio.model.TimelineEntry
import com.pix.folio.model.TimelineEntryType
import com.pix.folio.ui.theme.FolioGain
import com.pix.folio.ui.theme.FolioLoss
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class InsightPage { OVERVIEW, TIMELINE, ANNUAL, PROJECTION, PORTFOLIO }

private val InsightMoney = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}
private val InsightMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val InsightDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val InsightMono = FontFamily.Monospace

@Composable
internal fun V06InsightsScreen(vm: FolioViewModel, onBack: () -> Unit) {
    var page by remember { mutableStateOf(InsightPage.OVERVIEW) }

    when (page) {
        InsightPage.OVERVIEW -> InsightOverview(vm, onBack, onOpen = { page = it })
        InsightPage.TIMELINE -> TimelineScreen(vm.summary, onBack = { page = InsightPage.OVERVIEW })
        InsightPage.ANNUAL -> AnnualReviewScreen(vm.summary, onBack = { page = InsightPage.OVERVIEW })
        InsightPage.PROJECTION -> ProjectionScreen(vm.summary, onBack = { page = InsightPage.OVERVIEW })
        InsightPage.PORTFOLIO -> PortfolioLabScreen(vm, onBack = { page = InsightPage.OVERVIEW })
    }
}

@Composable
private fun InsightOverview(
    vm: FolioViewModel,
    onBack: () -> Unit,
    onOpen: (InsightPage) -> Unit,
) {
    val summary = vm.summary
    val now = YearMonth.now()
    val current = summary.monthOverview(now)
    val previous = summary.monthOverview(now.minusMonths(1))
    var showEmergency by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        InsightBackHeader("Insights", onBack)
        Spacer(Modifier.height(26.dp))

        InsightLabel("THIS MONTH VS LAST MONTH")
        Spacer(Modifier.height(10.dp))
        CompareRow("Income", current.income, previous.income)
        CompareRow("Spent", current.expenses + current.payments, previous.expenses + previous.payments, lowerIsBetter = true)
        CompareRow("Invested", current.invested, previous.invested)
        CompareRow("Left", current.left, previous.left)

        Spacer(Modifier.height(28.dp))
        InsightLabel("RESILIENCE")
        Spacer(Modifier.height(12.dp))
        InsightMetricRow(
            title = "Emergency fund",
            value = insightEuro(summary.emergencyFundBalance),
            detail = if (summary.averageMonthlyOutflow > 0.0) {
                "${oneDecimal(summary.emergencyFundMonths)} months of recent outflow"
            } else {
                "Add spending history to calculate coverage"
            },
            onClick = { showEmergency = true },
        )
        InsightMetricRow(
            title = "Monthly outflow",
            value = insightEuro(summary.averageMonthlyOutflow),
            detail = "Average of up to 3 active months",
        )
        InsightMetricRow(
            title = "Contribution streak",
            value = "${summary.investmentContributionStreak} mo",
            detail = if (summary.investmentContributionStreak > 0) "Consecutive months with investment activity" else "No active streak yet",
        )

        Spacer(Modifier.height(28.dp))
        InsightLabel("MILESTONES")
        Spacer(Modifier.height(12.dp))
        val nextMilestone = summary.automaticMilestones.firstOrNull { !it.reached }
        if (nextMilestone == null) {
            InsightNote("All automatic milestones through €1,000,000 reached.")
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Next · ${insightEuro(nextMilestone.amount)}", fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(nextMilestone.progress * 100).roundToInt()}% complete",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Text(insightEuro((nextMilestone.amount - summary.totalBalance).coerceAtLeast(0.0)), fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            ProgressLine(nextMilestone.progress.toFloat())
            Spacer(Modifier.height(14.dp))
            summary.automaticMilestones.filter { it.reached }.takeLast(3).forEach { milestone ->
                InsightMetricRow("Reached", insightEuro(milestone.amount), "Net-worth milestone")
            }
        }

        Spacer(Modifier.height(28.dp))
        InsightLabel("EXPLORE")
        Spacer(Modifier.height(8.dp))
        InsightMenuRow("Transaction timeline", "All money movement") { onOpen(InsightPage.TIMELINE) }
        InsightMenuRow("Annual review", "Year in numbers") { onOpen(InsightPage.ANNUAL) }
        InsightMenuRow("Projected growth", "Scenario calculator") { onOpen(InsightPage.PROJECTION) }
        InsightMenuRow("Investment lab", "Prices, purchases, tags & notes") { onOpen(InsightPage.PORTFOLIO) }

        Spacer(Modifier.height(26.dp))
        if (vm.canUndo) {
            OutlinedButton(
                onClick = { vm.undoLastChange() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Undo last local change") }
            Spacer(Modifier.height(8.dp))
            InsightNote("Undo restores the financial data snapshot from immediately before your last change.")
        }
        Spacer(Modifier.height(22.dp))
    }

    if (showEmergency) {
        EmergencyFundSheet(
            current = summary.emergencyFundBalance,
            onDismiss = { showEmergency = false },
            onSave = {
                vm.setEmergencyFundBalance(it)
                showEmergency = false
            },
        )
    }
}

@Composable
private fun TimelineScreen(summary: FolioSummary, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val rows = summary.transactionTimeline(month)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        InsightBackHeader("Transaction timeline", onBack)
        Spacer(Modifier.height(20.dp))
        MonthPicker(month, onPrevious = { month = month.minusMonths(1) }, onNext = {
            if (month < YearMonth.now()) month = month.plusMonths(1)
        })
        Spacer(Modifier.height(22.dp))

        val overview = summary.monthOverview(month)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TinyMetric("IN", overview.income, Modifier.weight(1f))
            TinyMetric("OUT", overview.expenses + overview.payments, Modifier.weight(1f))
            TinyMetric("INVEST", overview.invested, Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))
        InsightLabel("ACTIVITY")
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            InsightEmpty("No activity in ${month.atDay(1).format(InsightMonth)}.")
        } else {
            rows.forEachIndexed { index, row ->
                TimelineRow(row)
                if (index != rows.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AnnualReviewScreen(summary: FolioSummary, onBack: () -> Unit) {
    val years = summary.availableReviewYears
    var year by remember(years) { mutableStateOf(years.firstOrNull() ?: Year.now().value) }
    val review = summary.annualReview(year)
    val months = (1..12).map { summary.monthOverview(YearMonth.of(year, it)) }
    val active = months.filter { it.income != 0.0 || it.outflow != 0.0 || it.invested != 0.0 }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        InsightBackHeader("Annual review", onBack)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { year -= 1 }) { Text("‹") }
            Spacer(Modifier.weight(1f))
            Text(year.toString(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (year < Year.now().value) year += 1 }, enabled = year < Year.now().value) { Text("›") }
        }

        Spacer(Modifier.height(20.dp))
        InsightLabel("YEAR IN NUMBERS")
        Spacer(Modifier.height(8.dp))
        InsightValueRow("Income", review.income)
        InsightValueRow("Spent", review.spent, negative = true)
        InsightValueRow("Invested", review.invested, negative = true)
        InsightValueRow("Left", review.left, signed = true)
        InsightValueRow("Net-worth change", review.netWorthChange, signed = true)

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CountMetric("ACTIVE MONTHS", review.activeMonths.toString(), Modifier.weight(1f))
            CountMetric("CONTRIBUTIONS", review.contributionCount.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))
        InsightLabel("MONTHS")
        Spacer(Modifier.height(8.dp))
        if (active.isEmpty()) {
            InsightEmpty("No recorded activity for $year.")
        } else {
            active.forEachIndexed { index, month ->
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(month.month.month.name.take(3), fontFamily = InsightMono, fontSize = 10.sp, modifier = Modifier.width(44.dp))
                    Text("In ${insightEuro(month.income)}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Out ${insightEuro(month.outflow + month.invested)}", fontSize = 12.sp)
                }
                if (index != active.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProjectionScreen(summary: FolioSummary, onBack: () -> Unit) {
    var monthly by remember { mutableStateOf(if (summary.monthlyInvested > 0) summary.monthlyInvested.roundToInt().toString() else "1000") }
    var rate by remember { mutableStateOf("7") }
    var years by remember { mutableStateOf("15") }
    val monthlyValue = monthly.toInsightDouble().coerceAtLeast(0.0)
    val rateValue = rate.toInsightDouble()
    val yearsValue = (years.toIntOrNull() ?: 15).coerceIn(1, 50)
    val projection = summary.projectedGrowth(monthlyValue, rateValue, yearsValue)
    val chart = normalizeDoubles(projection.map { it.value })
    val final = projection.lastOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        InsightBackHeader("Projected growth", onBack)
        Spacer(Modifier.height(24.dp))
        InsightLabel("SCENARIO")
        Spacer(Modifier.height(10.dp))
        InsightNumberField(monthly, "Monthly contribution (€)") { monthly = it }
        Spacer(Modifier.height(10.dp))
        InsightNumberField(rate, "Annual return (%)", allowMinus = true) { rate = it }
        Spacer(Modifier.height(10.dp))
        InsightNumberField(years, "Years") { years = it.filter(Char::isDigit).take(2) }

        Spacer(Modifier.height(28.dp))
        InsightLabel("PROJECTED VALUE")
        Text(insightEuro(final?.value ?: summary.investmentTotal), fontSize = 42.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(4.dp))
        Text(
            "Contributed ${insightEuro(final?.contributed ?: summary.portfolioCostBasis)} · assumed ${oneDecimal(rateValue)}%/yr",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(22.dp))
        InsightSparkline(chart, Modifier.fillMaxWidth().height(130.dp))
        Spacer(Modifier.height(24.dp))

        InsightLabel("CHECKPOINTS")
        Spacer(Modifier.height(8.dp))
        projection.drop(1).filter { point ->
            point.year == 1 || point.year == yearsValue || point.year % 5 == 0
        }.distinctBy { it.year }.forEachIndexed { index, point ->
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
                Text("Year ${point.year}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(insightEuro(point.value), fontSize = 13.sp)
            }
            if (index != projection.drop(1).filter { it.year == 1 || it.year == yearsValue || it.year % 5 == 0 }.distinctBy { it.year }.lastIndex) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        InsightNote("Projection is a scenario, not a forecast. It uses a constant annual return and monthly compounding.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PortfolioLabScreen(vm: FolioViewModel, onBack: () -> Unit) {
    val summary = vm.summary
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = summary.investments.firstOrNull { it.id == selectedId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        InsightBackHeader("Investment lab", onBack)
        Spacer(Modifier.height(24.dp))
        InsightLabel("PORTFOLIO")
        Spacer(Modifier.height(8.dp))
        if (summary.investments.isEmpty()) {
            InsightEmpty("Add an investment first. Detailed price and purchase history will appear here.")
        } else {
            summary.investments.forEachIndexed { index, holding ->
                val market = summary.marketValueFor(holding)
                val gain = summary.gainFor(holding)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = holding.id }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(holding.name, fontSize = 14.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            listOf(holding.kind.label, holding.symbol).filter { it.isNotBlank() }.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = InsightMono,
                            fontSize = 9.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(insightEuro(market), fontSize = 13.sp)
                        Text(
                            insightSignedEuro(gain),
                            fontSize = 10.sp,
                            color = if (gain >= 0) FolioGain else FolioLoss,
                        )
                    }
                }
                if (index != summary.investments.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (selected != null) {
        InvestmentLabSheet(vm, selected, onDismiss = { selectedId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentLabSheet(vm: FolioViewModel, holding: InvestmentHolding, onDismiss: () -> Unit) {
    val summary = vm.summary
    val transactions = summary.transactionsFor(holding.id)
    val prices = summary.priceHistoryFor(holding.id)
    val units = summary.unitsFor(holding.id)
    val latestPrice = summary.latestPriceFor(holding.id)
    val market = summary.marketValueFor(holding)
    val gain = summary.gainFor(holding)
    val gainPct = summary.gainPctFor(holding)
    var contribution by remember { mutableStateOf("") }
    var contributionUnits by remember { mutableStateOf("") }
    var contributionPrice by remember { mutableStateOf("") }
    var marketPrice by remember { mutableStateOf("") }
    var priceDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var tags by remember(holding.tags) { mutableStateOf(holding.tags) }
    var note by remember(holding.note) { mutableStateOf(holding.note) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 30.dp)
        ) {
            Text(holding.name, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(
                listOf(holding.symbol, holding.exchange, holding.isin).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = InsightMono,
                fontSize = 9.sp,
            )

            Spacer(Modifier.height(24.dp))
            InsightLabel("POSITION")
            Spacer(Modifier.height(8.dp))
            InsightMetricRow("Market value", insightEuro(market), if (latestPrice != null) "Latest price ${insightEuro(latestPrice)}" else "Using contributed value")
            InsightMetricRow("Cost basis", insightEuro(holding.amount), if (units > 0) "${fourDecimals(units)} units" else "Units not recorded")
            InsightMetricRow(
                "Unrealized",
                "${insightSignedEuro(gain)} · ${signedPercent(gainPct)}",
                if (prices.isNotEmpty()) "${prices.size} recorded price point${if (prices.size == 1) "" else "s"}" else "Add a market price to track performance",
            )

            Spacer(Modifier.height(22.dp))
            InsightLabel("PRICE HISTORY")
            Spacer(Modifier.height(10.dp))
            InsightSparkline(normalizeDoubles(prices.map { it.close }), Modifier.fillMaxWidth().height(110.dp))
            if (prices.isEmpty()) InsightNote("No price points yet.")
            prices.takeLast(5).reversed().forEach { price ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(price.date.format(InsightDate), fontFamily = InsightMono, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    Text("${insightEuro(price.close)} ${price.currency}", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(22.dp))
            InsightLabel("ADD MARKET PRICE")
            Spacer(Modifier.height(10.dp))
            InsightNumberField(marketPrice, "Close price") { marketPrice = it }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = priceDate,
                onValueChange = { priceDate = it.take(10) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                colors = insightFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val date = runCatching { LocalDate.parse(priceDate) }.getOrDefault(LocalDate.now())
                    vm.addInvestmentPrice(holding.id, marketPrice.toInsightDouble(), "EUR", holding.symbol, "Manual", date)
                    marketPrice = ""
                },
                enabled = marketPrice.toInsightDouble() > 0.0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Record price") }

            Spacer(Modifier.height(26.dp))
            InsightLabel("PURCHASE HISTORY")
            Spacer(Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                InsightNote("No purchases recorded.")
            } else {
                transactions.sortedByDescending { it.date }.forEachIndexed { index, tx ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(tx.date.format(InsightDate), fontFamily = InsightMono, fontSize = 9.sp, modifier = Modifier.weight(1f))
                            Text("+${insightEuro(tx.amount)}", fontSize = 12.sp)
                        }
                        if (tx.units > 0.0 || tx.unitPrice > 0.0) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                listOf(
                                    tx.units.takeIf { it > 0.0 }?.let { "${fourDecimals(it)} units" },
                                    tx.unitPrice.takeIf { it > 0.0 }?.let { "${insightEuro(it)} / unit" },
                                ).filterNotNull().joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (index != transactions.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Spacer(Modifier.height(24.dp))
            InsightLabel("ADD CONTRIBUTION")
            Spacer(Modifier.height(10.dp))
            InsightNumberField(contribution, "Amount (€)") { contribution = it }
            Spacer(Modifier.height(8.dp))
            InsightNumberField(contributionUnits, "Units / shares (optional)") { contributionUnits = it }
            Spacer(Modifier.height(8.dp))
            InsightNumberField(contributionPrice, "Purchase price / unit (optional)") { contributionPrice = it }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    vm.addInvestmentContribution(
                        holding.id,
                        contribution.toInsightDouble(),
                        contributionUnits.toInsightDouble(),
                        contributionPrice.toInsightDouble(),
                    )
                    contribution = ""
                    contributionUnits = ""
                    contributionPrice = ""
                },
                enabled = contribution.toInsightDouble() > 0.0,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Add purchase") }

            Spacer(Modifier.height(26.dp))
            InsightLabel("TAGS")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InvestmentTag.entries) { tag ->
                    val active = tag in tags
                    Text(
                        tag.label,
                        modifier = Modifier
                            .background(
                                if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(18.dp),
                            )
                            .clickable { tags = if (active) tags - tag else tags + tag }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = note,
                onValueChange = { note = it.take(500) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("Investment thesis / note") },
                colors = insightFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.updateInvestmentDetails(holding.id, tags, note) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save tags & note") }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyFundSheet(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf(if (current > 0.0) current.roundToInt().toString() else "") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text("Emergency fund", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            InsightNote("Track the liquid money you reserve for emergencies. Folio compares it with recent monthly outflow.")
            Spacer(Modifier.height(18.dp))
            InsightNumberField(amount, "Emergency fund (€)") { amount = it }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(amount.toInsightDouble()) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun CompareRow(label: String, current: Double, previous: Double, lowerIsBetter: Boolean = false) {
    val delta = current - previous
    val good = if (lowerIsBetter) delta <= 0.0 else delta >= 0.0
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(
                "Last month ${insightEuro(previous)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(insightEuro(current), fontSize = 14.sp)
            Text(
                comparisonDelta(delta, previous),
                color = if (good) FolioGain else FolioLoss,
                fontSize = 10.sp,
            )
        }
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun InsightMetricRow(title: String, value: String, detail: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Row(modifier.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(value, fontSize = 13.sp)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun InsightMenuRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TimelineRow(row: TimelineEntry) {
    val positive = row.amount >= 0.0
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)), fontFamily = InsightMono, fontSize = 9.sp, modifier = Modifier.width(58.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name, fontSize = 13.sp)
            Text(
                row.detail.ifBlank { row.type.name.lowercase().replaceFirstChar(Char::uppercase) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
        }
        Text(
            if (positive) "+${insightEuro(row.amount)}" else "−${insightEuro(abs(row.amount))}",
            fontSize = 12.sp,
            color = when (row.type) {
                TimelineEntryType.INCOME -> FolioGain
                TimelineEntryType.EXPENSE, TimelineEntryType.PAYMENT, TimelineEntryType.INVESTMENT -> MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

@Composable
private fun MonthPicker(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious) { Text("‹") }
        Spacer(Modifier.weight(1f))
        Text(month.atDay(1).format(InsightMonth), fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onNext, enabled = month < YearMonth.now()) { Text("›") }
    }
}

@Composable
private fun TinyMetric(label: String, value: Double, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(12.dp)) {
        InsightLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(insightEuro(value), fontSize = 13.sp)
    }
}

@Composable
private fun CountMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(14.dp)) {
        InsightLabel(label)
        Spacer(Modifier.height(7.dp))
        Text(value, fontSize = 26.sp)
    }
}

@Composable
private fun InsightValueRow(label: String, value: Double, negative: Boolean = false, signed: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            when {
                signed -> insightSignedEuro(value)
                negative -> "−${insightEuro(value)}"
                else -> insightEuro(value)
            },
            fontSize = 14.sp,
            color = if (signed && value < 0) FolioLoss else MaterialTheme.colorScheme.onBackground,
        )
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ProgressLine(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).height(5.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
    }
}

@Composable
private fun InsightBackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InsightLabel(text: String) {
    Text(text, fontFamily = InsightMono, fontSize = 10.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InsightNote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun InsightEmpty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 22.dp))
}

@Composable
private fun InsightNumberField(value: String, placeholder: String, allowMinus: Boolean = false, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { raw ->
            onChange(raw.filter { it.isDigit() || it == '.' || it == ',' || (allowMinus && it == '-') })
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = if (allowMinus) KeyboardType.Text else KeyboardType.Decimal),
        singleLine = true,
        colors = insightFieldColors(),
    )
}

@Composable
private fun insightFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@Composable
private fun InsightSparkline(values: List<Float>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.onBackground
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        drawLine(guide, Offset(0f, size.height * .86f), Offset(size.width, size.height * .86f), strokeWidth = 1f)
        if (values.isEmpty()) return@Canvas
        if (values.size == 1) {
            drawCircle(line, radius = 4f, center = Offset(size.width, size.height * .5f))
            return@Canvas
        }
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = size.height * (1f - value.coerceIn(.05f, .95f))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

private fun normalizeDoubles(values: List<Double>): List<Float> {
    if (values.isEmpty()) return emptyList()
    if (values.size == 1) return listOf(.5f)
    val min = values.minOrNull() ?: return emptyList()
    val max = values.maxOrNull() ?: return emptyList()
    if (max == min) return List(values.size) { .5f }
    return values.map { ((it - min) / (max - min)).toFloat().coerceIn(.08f, .92f) }
}

private fun comparisonDelta(delta: Double, base: Double): String {
    if (base == 0.0) return if (delta == 0.0) "No change" else insightSignedEuro(delta)
    val pct = delta / abs(base) * 100.0
    return "${if (pct >= 0) "+" else "−"}${oneDecimal(abs(pct))}%"
}

private fun String.toInsightDouble(): Double = replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
private fun insightEuro(value: Double): String = "€${InsightMoney.format(value)}"
private fun insightSignedEuro(value: Double): String = if (value >= 0.0) "+${insightEuro(value)}" else "−${insightEuro(abs(value))}"
private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun fourDecimals(value: Double): String = String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
private fun signedPercent(value: Double): String = "${if (value >= 0) "+" else "−"}${oneDecimal(abs(value))}%"
