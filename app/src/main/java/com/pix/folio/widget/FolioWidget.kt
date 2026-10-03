package com.pix.folio.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
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
import com.pix.folio.R
import com.pix.folio.data.FolioStore
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.data.SecureScalableStore
import com.pix.folio.data.SecureSparkasseStore
import java.text.NumberFormat
import java.util.Locale

private val WidgetMoney = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}

private val WidgetBackground = ColorProvider(R.color.folio_widget_background)
private val WidgetForeground = ColorProvider(R.color.folio_widget_foreground)
private val WidgetMuted = ColorProvider(R.color.folio_widget_muted)

private data class WidgetValues(
    val netWorth: Double,
    val investments: Double,
    val cash: Double,
    val savings: Double,
)

private fun widgetValues(context: Context): WidgetValues {
    val summary = FolioStore(context).summary()
    val scalable = SecureScalableStore(context).load().getOrNull()
    val sparkasse = SecureSparkasseStore(context).load().getOrNull()
    val tracking = InvestmentTrackingStore(context)

    val localInvestments = summary.investments.sumOf { holding ->
        val brokerUnits = tracking.brokerOwnedUnits(holding.id)
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

    val investments = scalable?.brokerAccountValue ?: localInvestments

    val cash = sparkasse?.availableBalance ?: summary.cashBalance

    return WidgetValues(
        netWorth = cash + summary.totalSavings + investments,
        investments = investments,
        cash = cash,
        savings = summary.totalSavings,
    )
}

class FolioOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val values = widgetValues(context)
        provideContent {
            OverviewWidgetContent(values)
        }
    }
}

@Composable
private fun OverviewWidgetContent(values: WidgetValues) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.folio_widget_logo),
                contentDescription = "Folio",
                modifier = GlanceModifier.width(20.dp).height(20.dp),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                "Folio",
                style = TextStyle(
                    color = WidgetForeground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(GlanceModifier.height(5.dp))
        Text(
            "NET WORTH",
            style = TextStyle(
                color = WidgetMuted,
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            "€" + WidgetMoney.format(values.netWorth),
            style = TextStyle(
                color = WidgetForeground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(GlanceModifier.height(7.dp))
        Row {
            OverviewWidgetMetric(
                label = "SAVINGS",
                amount = values.savings,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            OverviewWidgetMetric(
                label = "CASH",
                amount = values.cash,
                modifier = GlanceModifier.defaultWeight(),
            )
        }

        Spacer(GlanceModifier.height(5.dp))
        OverviewWidgetMetric(
            label = "INVESTMENTS",
            amount = values.investments,
        )
    }
}

@Composable
private fun OverviewWidgetMetric(
    label: String,
    amount: Double,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = TextStyle(
                color = WidgetMuted,
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            "€" + WidgetMoney.format(amount),
            style = TextStyle(
                color = WidgetForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

class FolioOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FolioOverviewWidget()
}
