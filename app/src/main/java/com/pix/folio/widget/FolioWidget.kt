package com.pix.folio.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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
import com.pix.folio.data.FolioStore
import com.pix.folio.data.InvestmentTrackingStore
import java.text.NumberFormat
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

private enum class WidgetMetric(val label: String) {
    NET_WORTH("NET WORTH"),
    INVESTMENTS("INVESTMENTS"),
    CASH_LEFT("CASH LEFT");

    fun next(): WidgetMetric = entries[(ordinal + 1) % entries.size]
    fun previous(): WidgetMetric = entries[(ordinal - 1 + entries.size) % entries.size]
}

private object WidgetMetricStore {
    private const val PREFS = "folio_widget_metric_v2"
    private const val KEY = "metric"

    fun get(context: Context): WidgetMetric {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, WidgetMetric.NET_WORTH.name)
        return runCatching { WidgetMetric.valueOf(raw ?: WidgetMetric.NET_WORTH.name) }
            .getOrDefault(WidgetMetric.NET_WORTH)
    }

    fun set(context: Context, metric: WidgetMetric) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, metric.name)
            .apply()
    }
}

private data class WidgetValues(
    val netWorth: Double,
    val investments: Double,
    val cashLeft: Double,
)

private fun widgetValues(context: Context): WidgetValues {
    val summary = FolioStore(context).summary()
    val tracking = InvestmentTrackingStore(context)

    val investments = summary.investments.sumOf { holding ->
        val brokerUnits = tracking.ownedUnits(holding.id)
        val latest = summary.priceHistoryFor(holding.id).lastOrNull()
        val exactEurValue = if (
            brokerUnits != null &&
            brokerUnits > 0.0 &&
            latest != null &&
            latest.close > 0.0 &&
            latest.currency.trim().equals("EUR", ignoreCase = true)
        ) {
            brokerUnits * latest.close
        } else {
            null
        }

        exactEurValue ?: summary.marketValueFor(holding)
    }

    return WidgetValues(
        netWorth = summary.cashBalance + summary.totalSavings + investments,
        investments = investments,
        cashLeft = summary.cashBalance,
    )
}

class FolioBalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val metric = WidgetMetricStore.get(context)
        val values = widgetValues(context)
        val amount = when (metric) {
            WidgetMetric.NET_WORTH -> values.netWorth
            WidgetMetric.INVESTMENTS -> values.investments
            WidgetMetric.CASH_LEFT -> values.cashLeft
        }

        provideContent {
            BalanceWidgetContent(
                label = metric.label,
                amount = "€${WidgetMoney.format(amount)}",
            )
        }
    }
}

@Composable
private fun BalanceWidgetContent(
    label: String,
    amount: String,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetBackground)
            .cornerRadius(26.dp)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Folio", style = TextStyle(color = WidgetMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
            Text(label, style = TextStyle(color = WidgetMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(3.dp))
            Text(amount, style = TextStyle(color = WidgetForeground, fontSize = 30.sp, fontWeight = FontWeight.Medium))
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                "↑",
                modifier = GlanceModifier
                    .clickable(actionRunCallback<PreviousWidgetMetricAction>())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(color = WidgetForeground, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                "↓",
                modifier = GlanceModifier
                    .clickable(actionRunCallback<NextWidgetMetricAction>())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

class PreviousWidgetMetricAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetMetricStore.set(context, WidgetMetricStore.get(context).previous())
        FolioBalanceWidget().update(context, glanceId)
    }
}

class NextWidgetMetricAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetMetricStore.set(context, WidgetMetricStore.get(context).next())
        FolioBalanceWidget().update(context, glanceId)
    }
}

class FolioBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FolioBalanceWidget()
}
