package com.pix.folio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.data.ScalableSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val V141BrokerTime = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH)

@Composable
internal fun V141ScalablePortfolioScreen(snapshot: ScalableSnapshot) {
    val brokerHoldingsValue = snapshot.holdingsValue
    val brokerAccountValue = snapshot.brokerAccountValue
    val brokerReturn = snapshot.primaryAbsoluteReturn
    val importedAt = snapshot.createdAtUtc
        .atZone(ZoneId.systemDefault())
        .format(V141BrokerTime)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            "Portfolio",
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "SCALABLE CAPITAL",
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))

        V07Panel {
            Text(
                "BROKER ACCOUNT VALUE",
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                v07Euro(brokerAccountValue),
                fontSize = 52.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            brokerReturn?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    v07SignedEuro(it.absoluteReturn) + " · " + it.timeframe + " broker return",
                    fontSize = 12.sp,
                    color = folioChangeColor(it.absoluteReturn),
                )
            }

            Spacer(Modifier.height(16.dp))
            V07Divider()
            Spacer(Modifier.height(10.dp))

            V07Metric(
                "Holdings",
                v07Euro(brokerHoldingsValue),
                snapshot.holdings.size.toString() + " positions returned by Scalable",
            )
            V07Divider()
            V07Metric(
                "Broker cash",
                v07Euro(snapshot.cashBalance),
                "Scalable cash-breakdown",
            )
            if (kotlin.math.abs(snapshot.portfolioValue - brokerHoldingsValue) >= 0.005) {
                V07Divider()
                V07Metric(
                    "Overview valuation",
                    v07Euro(snapshot.portfolioValue),
                    "Scalable overview value; not used for the headline",
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Broker holdings",
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                snapshot.holdings.size.toString(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Only positions returned by Scalable CLI are shown here. Folio does not recalculate their values.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        if (snapshot.holdings.isEmpty()) {
            V07Panel {
                Text("No broker holdings returned", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            snapshot.holdings
                .sortedByDescending { it.valuation }
                .forEachIndexed { index, holding ->
                    V07Metric(
                        label = holding.name,
                        value = v07Euro(holding.valuation),
                        detail = buildString {
                            if (brokerHoldingsValue > 0.0) {
                                append(String.format(Locale.US, "%.1f%%", holding.valuation / brokerHoldingsValue * 100.0))
                                append(" · ")
                            }
                            append("Scalable")
                            holding.quantity?.let { append(" · " + v081Units(it) + " units") }
                            if (holding.securityType.isNotBlank()) append(" · " + holding.securityType)
                            append(" · " + holding.isin)
                        },
                    )
                    if (index != snapshot.holdings.lastIndex) V07Divider()
                }
        }

        Spacer(Modifier.height(28.dp))
        V07Panel {
            Text("Snapshot", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            V07Metric("Imported", importedAt)
            V07Divider()
            V07Metric("CLI", snapshot.cliVersion ?: "Scalable CLI")
            snapshot.valuationTimestampUtc?.let {
                V07Divider()
                V07Metric("Broker timestamp", it)
            }
        }

        Spacer(Modifier.height(30.dp))
        Text(
            "To refresh Portfolio, generate and import a new Scalable snapshot. Folio does not use Yahoo prices, local holdings, local cost basis, or Folio purchase history while this broker snapshot is connected.",
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
