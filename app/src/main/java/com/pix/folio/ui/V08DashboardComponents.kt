package com.pix.folio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.ValueSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal enum class V08HistoryRange(val label: String, val months: Long?) {
    ONE_MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    ONE_YEAR("1Y", 12),
    ALL("ALL", null),
}

private val V08InspectDate = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH)

@Composable
internal fun V08NetWorthHistory(
    history: List<ValueSnapshot>,
    currentValue: Double,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(V08HistoryRange.ALL) }
    val now = System.currentTimeMillis()
    val cutoff = range.months?.let { months ->
        Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .minusMonths(months)
            .toInstant()
            .toEpochMilli()
    }
    val filtered = remember(history, currentValue, range) {
        buildList {
            addAll(history.filter { cutoff == null || it.atMillis >= cutoff }.sortedBy { it.atMillis })
            if (lastOrNull()?.value != currentValue) add(ValueSnapshot(now, currentValue))
        }.let { rows ->
            if (rows.size >= 2) rows else buildList {
                history.sortedBy { it.atMillis }.takeLast(2).forEach(::add)
                if (lastOrNull()?.value != currentValue) add(ValueSnapshot(now, currentValue))
            }
        }
    }
    val rangeChange = filtered.lastOrNull()?.value?.minus(filtered.firstOrNull()?.value ?: currentValue) ?: 0.0

    Column(modifier) {
        V08InteractiveValueChart(
            points = filtered.map { it.atMillis to it.value },
            labelFor = { millis ->
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(V08InspectDate)
            },
            semanticTrend = rangeChange,
            modifier = Modifier.fillMaxWidth().height(154.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            V08HistoryRange.entries.forEach { option ->
                TextButton(onClick = { range = option }, modifier = Modifier.weight(1f)) {
                    Text(
                        if (range == option) "• ${option.label}" else option.label,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun V08PortfolioHistory(
    history: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
    semanticTrend: Double? = null,
    showZeroLine: Boolean = false,
    signedValues: Boolean = false,
) {
    if (history.size < 2) return
    val zone = ZoneId.systemDefault()
    V08InteractiveValueChart(
        points = history.map { (date, value) -> date.atStartOfDay(zone).toInstant().toEpochMilli() to value },
        labelFor = { millis -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(V07ShortDateFormat) },
        semanticTrend = semanticTrend,
        showZeroLine = showZeroLine,
        signedValues = signedValues,
        modifier = modifier,
    )
}

@Composable
private fun V08InteractiveValueChart(
    points: List<Pair<Long, Double>>,
    labelFor: (Long) -> String,
    modifier: Modifier = Modifier,
    semanticTrend: Double? = null,
    showZeroLine: Boolean = false,
    signedValues: Boolean = false,
) {
    if (points.size < 2) return
    val lineColor = semanticTrend?.let { folioChangeColor(it) } ?: MaterialTheme.colorScheme.onBackground
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    var selectedIndex by remember(points) { mutableIntStateOf(points.lastIndex) }
    var measuredWidth by remember { mutableIntStateOf(1) }

    fun selectAt(x: Float) {
        val fraction = (x / measuredWidth.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        selectedIndex = (fraction * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
    }

    val selected = points[selectedIndex]
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                labelFor(selected.first),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (signedValues) v07SignedEuro(selected.second) else v07Euro(selected.second),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (semanticTrend != null) lineColor else MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier
                .onSizeChanged { measuredWidth = it.width.coerceAtLeast(1) }
                .pointerInput(points, measuredWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        selectAt(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            selectAt(change.position.x)
                            if (!change.pressed) break
                            change.consume()
                        }
                    }
                }
        ) {
            val values = points.map { it.second }
            val rawMin = values.minOrNull() ?: 0.0
            val rawMax = values.maxOrNull() ?: rawMin
            val min = if (showZeroLine) minOf(rawMin, 0.0) else rawMin
            val max = if (showZeroLine) maxOf(rawMax, 0.0) else rawMax
            val span = (max - min).takeIf { it > 0.00001 } ?: 1.0
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = size.width * index / points.lastIndex.toFloat()
                val y = size.height - ((point.second - min) / span).toFloat() * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val zeroY = size.height - ((0.0 - min) / span).toFloat() * size.height
            if (showZeroLine) {
                drawLine(guideColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            }

            val fillBaseY = if (showZeroLine) zeroY else size.height
            val area = Path().apply {
                points.forEachIndexed { index, point ->
                    val x = size.width * index / points.lastIndex.toFloat()
                    val y = size.height - ((point.second - min) / span).toFloat() * size.height
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                lineTo(size.width, fillBaseY)
                lineTo(0f, fillBaseY)
                close()
            }
            drawPath(area, lineColor.copy(alpha = 0.08f))
            drawPath(path, lineColor, style = Stroke(width = 3f))

            val x = size.width * selectedIndex / points.lastIndex.toFloat()
            val selectedY = size.height - ((selected.second - min) / span).toFloat() * size.height
            drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            drawCircle(lineColor, radius = 5f, center = Offset(x, selectedY))
        }
    }
}

@Composable
internal fun V08SpendingBreakdown(summary: FolioSummary, month: java.time.YearMonth) {
    val rows = ExpenseCategory.entries.mapNotNull { category ->
        val spent = summary.spentFor(category, month)
        if (spent > 0.0) category to spent else null
    }.sortedByDescending { it.second }
    val total = rows.sumOf { it.second }
    val previousTotal = ExpenseCategory.entries.sumOf { summary.spentFor(it, month.minusMonths(1)) }
    val difference = total - previousTotal
    val visibleRows = rows.take(6)

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("Where did my money go?", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(
            if (previousTotal > 0.0) "${v07SignedEuro(difference)} vs last month" else "Your spending by category",
            fontSize = 11.sp,
            color = if (previousTotal > 0.0) folioChangeColor(difference, positiveIsGood = false)
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (visibleRows.isEmpty()) {
            Text("No spending recorded for this month.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            visibleRows.forEachIndexed { index, (category, amount) ->
                val share = if (total > 0.0) amount / total * 100.0 else 0.0
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 5.dp)) {
                    Text(category.label.uppercase(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${v07Euro(amount)} · ${String.format(Locale.US, "%.0f", share)}%", fontSize = 12.sp)
                }
                V07Progress(share / 100.0)
                if (index != visibleRows.lastIndex) Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
internal fun V08PortfolioIntelligence(vm: V07ViewModel) {
    val summary = vm.summary
    val valuations = summary.investments.associateWith(vm::v081Valuation)
    val holdings = summary.investments.sortedByDescending { valuations.getValue(it).value }
    val total = vm.v081PortfolioTotal
    if (holdings.isEmpty() || total <= 0.0) return

    fun returnPct(index: Int): Double {
        val holding = holdings[index]
        if (holding.amount <= 0.0) return 0.0
        return (valuations.getValue(holding).value - holding.amount) / holding.amount * 100.0
    }

    val largest = holdings.first()
    val largestValue = valuations.getValue(largest).value
    val largestShare = largestValue / total * 100.0
    val bestIndex = holdings.indices.maxByOrNull(::returnPct)
    val worstIndex = holdings.indices.minByOrNull(::returnPct)
    val marketGrowth = total - summary.portfolioCostBasis

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("Portfolio intelligence", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        V07Metric("Contributed", v07Euro(summary.portfolioCostBasis))
        V07Divider()
        V07Metric(
            "Market growth",
            v07SignedEuro(marketGrowth),
            valueColor = folioChangeColor(marketGrowth),
        )
        V07Divider()
        V07Metric("Largest position", "${String.format(Locale.US, "%.0f", largestShare)}%", largest.name)
        if (largestShare >= 25.0) {
            Text(
                "${largest.name} is a large share of the portfolio. This is a concentration signal, not a buy/sell recommendation.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (bestIndex != null && worstIndex != null && holdings.size > 1) {
            val bestReturn = returnPct(bestIndex)
            val worstReturn = returnPct(worstIndex)
            V07Divider()
            V07Metric(
                "Best return",
                "${String.format(Locale.US, "%+.1f%%", bestReturn)}",
                holdings[bestIndex].name,
                valueColor = folioChangeColor(bestReturn),
            )
            V07Divider()
            V07Metric(
                "Lowest return",
                "${String.format(Locale.US, "%+.1f%%", worstReturn)}",
                holdings[worstIndex].name,
                valueColor = folioChangeColor(worstReturn),
            )
        }
    }
}
