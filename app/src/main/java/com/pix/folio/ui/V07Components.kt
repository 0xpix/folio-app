package com.pix.folio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal val V07MonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
internal val V07ShortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

private val V07Money = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
}

internal fun v07Euro(value: Double): String = "€${V07Money.format(value)}"
internal fun v07SignedEuro(value: Double): String = when {
    value > 0.005 -> "+${v07Euro(value)}"
    value < -0.005 -> "−${v07Euro(-value)}"
    else -> v07Euro(0.0)
}
internal fun String.v07Double(): Double = replace("€", "").replace(" ", "").replace(',', '.').toDoubleOrNull() ?: 0.0

@Composable
internal fun V07SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ENGLISH),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        letterSpacing = 1.6.sp,
    )
}

@Composable
internal fun V07Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp)
    ) { content() }
}

@Composable
internal fun V07DashboardCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 14.dp)
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        Text(value, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
internal fun V07Metric(
    label: String,
    value: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 14.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(detail, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun V07Progress(progress: Double, modifier: Modifier = Modifier) {
    val safe = progress.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .fillMaxWidth(safe)
                .height(5.dp)
                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(50))
        )
    }
}

@Composable
internal fun V07Goal(
    label: String,
    current: Double,
    target: Double,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val progress = if (target > 0.0) (current / target).coerceIn(0.0, 1.0) else 0.0
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text("${v07Euro(current)} / ${v07Euro(target)}", fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        V07Progress(progress)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${(progress * 100).roundToInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun V07Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val points = values.filter { it.isFinite() }
    val lineColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.minOrNull() ?: return@Canvas
        val max = points.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0.0001 } ?: 1.0
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / points.lastIndex.coerceAtLeast(1).toFloat()
            val normalized = ((value - min) / span).toFloat()
            val y = size.height - normalized * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun V07LineChart(values: List<Double>, modifier: Modifier = Modifier) {
    val points = values.filter { it.isFinite() }
    val lineColor = MaterialTheme.colorScheme.onSurface
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        drawLine(guideColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        val min = points.minOrNull() ?: return@Canvas
        val max = points.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0.0001 } ?: 1.0
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / points.lastIndex.toFloat()
            val y = size.height - (((value - min) / span).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun V07NumberField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
internal fun V07TextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
internal fun V07Divider() {
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun V07MonthPicker(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", modifier = Modifier.clickable(onClick = onPrevious).padding(12.dp), fontSize = 25.sp)
        Text(month.atDay(1).format(V07MonthFormat), modifier = Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Text("›", modifier = Modifier.clickable(onClick = onNext).padding(12.dp), fontSize = 25.sp)
    }
}

@Composable
internal fun V07TinyStats(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Column(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 13.dp, vertical = 13.dp)
            ) {
                V07SectionLabel(label)
                Spacer(Modifier.height(7.dp))
                Text(value, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
