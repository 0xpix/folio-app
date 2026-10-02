package com.pix.folio.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pix.folio.MainActivity
import com.pix.folio.data.FolioStartMonth
import com.pix.folio.data.FolioStore
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.model.ValueSnapshot
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Locale

private val WidgetMoney = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}

private val WidgetBackground = ColorProvider(
    day = Color(0xFFF5F3EE),
    night = Color(0xFF171717),
)
private val WidgetForeground = ColorProvider(
    day = Color(0xFF111111),
    night = Color(0xFFF5F3EE),
)
private val WidgetMuted = ColorProvider(
    day = Color(0xFF77746E),
    night = Color(0xFFAAA69F),
)

class FolioBalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val summary = FolioStore(context).summary()
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val tracking = InvestmentTrackingStore(context)
        val portfolioValue = summary.investments.sumOf { holding ->
            val brokerUnits = tracking.ownedUnits(holding.id)
            val latestPoint = summary.priceHistoryFor(holding.id).lastOrNull()
            val canUseExactUnits =
                brokerUnits != null &&
                    latestPoint != null &&
                    latestPoint.close > 0.0 &&
                    latestPoint.currency.trim().equals("EUR", ignoreCase = true)
            if (canUseExactUnits) brokerUnits!! * latestPoint!!.close else summary.marketValueFor(holding)
        }
        val totalBalance = summary.cashBalance + summary.totalSavings + portfolioValue
        val startMillis = FolioStartMonth.atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val chart = SparklineBitmap.render(context, dark, summary.balanceHistory.filter { it.atMillis >= startMillis })

        provideContent {
            BalanceWidgetContent(
                total = "€${WidgetMoney.format(totalBalance)}",
                change = (if (summary.monthlyChange >= 0) "+ €" else "− €") + WidgetMoney.format(kotlin.math.abs(summary.monthlyChange)) + " this month",
                chart = chart,
            )
        }
    }
}

@Composable
private fun BalanceWidgetContent(
    total: String,
    change: String,
    chart: Bitmap,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
.background(WidgetBackground)
            .cornerRadius(26.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.width(125.dp)) {
            Text("Folio", style = TextStyle(color = WidgetForeground, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(6.dp))
            Text(total, style = TextStyle(color = WidgetForeground, fontSize = 28.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(3.dp))
            Text(change, style = TextStyle(color = WidgetMuted, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.width(10.dp))
        Box(
            modifier = GlanceModifier.width(90.dp).height(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(chart),
                contentDescription = "Balance trend",
                modifier = GlanceModifier.width(90.dp).height(54.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

class FolioBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FolioBalanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            FolioWidgetUpdater.request(context)
        }
    }
}

private object SparklineBitmap {
    fun render(context: Context, dark: Boolean, history: List<ValueSnapshot>): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (110 * density).toInt().coerceAtLeast(1)
        val height = (54 * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (history.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFFF5F3EE.toInt() else 0xFF111111.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val source = history.takeLast(40).map { it.value }
        val min = source.minOrNull() ?: return bitmap
        val max = source.maxOrNull() ?: return bitmap
        val values = if (max == min) {
            List(source.size) { .5f }
        } else {
            source.map { (((it - min) / (max - min)).toFloat()).coerceIn(.1f, .9f) }
        }

        if (values.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width * .82f, height * (1f - values.first()), 2.5f * density, paint)
            return bitmap
        }

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = width * index.toFloat() / (values.size - 1)
            val y = height * (1f - value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
        return bitmap
    }
}
