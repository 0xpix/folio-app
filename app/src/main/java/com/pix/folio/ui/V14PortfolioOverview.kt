package com.pix.folio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val V14SnapshotTime = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.ENGLISH)

@Composable
internal fun V14PortfolioHero(
    vm: V07ViewModel,
    total: Double,
    gain: Double,
    gainPct: Double,
    onAdd: () -> Unit,
) {
    val scalable = vm.scalableSnapshot
    val summary = vm.summary
    val brokerReturn = scalable?.primaryAbsoluteReturn
    val sourceLabel = if (scalable != null) "SCALABLE CAPITAL" else "FOLIO TRACKING"
    val sourceDetail = if (scalable != null) {
        val imported = scalable.createdAtUtc.atZone(ZoneId.systemDefault()).format(V14SnapshotTime)
        "IMPORTED SNAPSHOT · " + imported
    } else {
        "LOCAL PORTFOLIO"
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Portfolio",
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                sourceLabel,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) { Text("+ Add") }
    }

    Spacer(Modifier.height(14.dp))

    V07Panel {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (scalable != null) "BROKER TOTAL" else "PORTFOLIO VALUE",
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                sourceDetail,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(7.dp))
        Text(
            v07Euro(total),
            fontSize = 52.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            when {
                scalable != null && brokerReturn != null ->
                    v07SignedEuro(brokerReturn.absoluteReturn) + " · " + brokerReturn.timeframe + " broker return"
                scalable != null ->
                    "Exact value from the imported Scalable snapshot"
                else ->
                    v07SignedEuro(gain) + " total return · " +
                        String.format(Locale.US, "%+.1f%%", gainPct) + " time-weighted"
            },
            fontSize = 12.sp,
            color = when {
                scalable != null && brokerReturn == null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> folioChangeColor(gain)
            },
        )

        Spacer(Modifier.height(18.dp))
        V07Divider()
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scalable != null) {
                V14PortfolioMiniMetric(
                    label = "INVESTED",
                    value = scalable.brokerInvestedCapital?.let(::v07Euro) ?: "—",
                    detail = if (scalable.brokerInvestedCapital != null) "broker-derived" else "not reported",
                    modifier = Modifier.weight(1f),
                )
                V14PortfolioMiniMetric(
                    label = "HOLDINGS",
                    value = v07Euro(scalable.holdingsValue),
                    detail = scalable.holdings.size.toString() + " positions",
                    modifier = Modifier.weight(1f),
                )
                V14PortfolioMiniMetric(
                    label = "CASH / CREDIT",
                    value = v07SignedEuro(scalable.brokerCashOrCreditValue),
                    detail = "broker residual",
                    modifier = Modifier.weight(1f),
                )
            } else {
                V14PortfolioMiniMetric(
                    label = "INVESTED",
                    value = v07Euro(summary.portfolioCostBasis),
                    detail = "recorded contributions",
                    modifier = Modifier.weight(1f),
                )
                V14PortfolioMiniMetric(
                    label = "RETURN",
                    value = v07SignedEuro(gain),
                    detail = "absolute",
                    modifier = Modifier.weight(1f),
                )
                V14PortfolioMiniMetric(
                    label = "HOLDINGS",
                    value = summary.investments.size.toString(),
                    detail = "tracked locally",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (scalable != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Scalable mode does not mix Folio's manual cost basis into the broker headline. " +
                    "Local purchase history remains below only as your Folio activity record.",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun V14PortfolioMiniMetric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            detail,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
