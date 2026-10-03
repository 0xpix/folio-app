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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.pix.folio.data.ScalablePerformanceSnapshot
import com.pix.folio.data.ScalableSnapshot

@Composable
internal fun V141ScalablePortfolioScreen(snapshot: ScalableSnapshot) {
    val frames = snapshot.performance.sortedBy { scalableFrameOrder(it.timeframe) }
    val initialFrame = snapshot.primaryAbsoluteReturn ?: frames.lastOrNull()
    var selectedFrame by remember(snapshot.createdAtUtc) { mutableStateOf(initialFrame) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            "Portfolio",
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(26.dp))

        Text(
            v07Euro(snapshot.brokerAccountValue),
            fontSize = 52.sp,
            lineHeight = 58.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )

        selectedFrame?.let { frame ->
            Spacer(Modifier.height(4.dp))
            Text(
                v07SignedEuro(frame.absoluteReturn),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = folioChangeColor(frame.absoluteReturn),
            )
        }

        if (frames.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                frames.forEach { frame ->
                    TextButton(
                        onClick = { selectedFrame = frame },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 4.dp,
                            vertical = 2.dp,
                        ),
                    ) {
                        Text(
                            text = scalableFrameLabel(frame.timeframe),
                            fontSize = 11.sp,
                            fontWeight = if (selectedFrame?.timeframe == frame.timeframe) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (selectedFrame?.timeframe == frame.timeframe) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        if (snapshot.cashBalance > 0.005) {
            Spacer(Modifier.height(18.dp))
            V07Divider()
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cash",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    v07Euro(snapshot.cashBalance),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(34.dp))
        Text(
            "Portfolio",
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))

        if (snapshot.holdings.isEmpty()) {
            Text(
                "No positions returned by Scalable in this snapshot.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        } else {
            snapshot.holdings
                .sortedByDescending { it.valuation }
                .forEachIndexed { index, holding ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                holding.name,
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                            )
                            if (holding.securityType.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    holding.securityType,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            v07Euro(holding.valuation),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (index != snapshot.holdings.lastIndex) V07Divider()
                }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun scalableFrameOrder(value: String): Int = when (value.uppercase()) {
    "ONE_DAY", "1D", "DAY" -> 0
    "ONE_WEEK", "1W", "WEEK" -> 1
    "ONE_MONTH", "1M", "MONTH" -> 2
    "THREE_MONTHS", "3M" -> 3
    "SIX_MONTHS", "6M" -> 4
    "YEAR_TO_DATE", "YTD" -> 5
    "ONE_YEAR", "1Y", "YEAR" -> 6
    "MAX", "ALL", "ALL_TIME", "SINCE_INCEPTION", "SINCE_BUY", "SINCE_BUYING" -> 7
    else -> 8
}

private fun scalableFrameLabel(value: String): String = when (value.uppercase()) {
    "ONE_DAY", "1D", "DAY" -> "1D"
    "ONE_WEEK", "1W", "WEEK" -> "1W"
    "ONE_MONTH", "1M", "MONTH" -> "1M"
    "THREE_MONTHS", "3M" -> "3M"
    "SIX_MONTHS", "6M" -> "6M"
    "YEAR_TO_DATE", "YTD" -> "YTD"
    "ONE_YEAR", "1Y", "YEAR" -> "1Y"
    "MAX", "ALL", "ALL_TIME", "SINCE_INCEPTION", "SINCE_BUY", "SINCE_BUYING" -> "MAX"
    else -> value
}
