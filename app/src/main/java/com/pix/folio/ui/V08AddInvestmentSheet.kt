package com.pix.folio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.data.OpenFigiService
import com.pix.folio.model.Cs2AssetType
import com.pix.folio.model.InvestmentKind
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V08AddInvestmentSheet(
    vm: V07ViewModel,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(InvestmentKind.ETF) }
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var purchaseTime by remember {
        mutableStateOf(LocalTime.now().withSecond(0).withNano(0).toString())
    }
    var isin by remember { mutableStateOf("") }
    var figi by remember { mutableStateOf("") }
    var exchange by remember { mutableStateOf("") }
    var marketName by remember { mutableStateOf("") }
    var cs2Type by remember { mutableStateOf(Cs2AssetType.OTHER) }
    var lookupText by remember { mutableStateOf<String?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val parsedDate = runCatching { LocalDate.parse(purchaseDate) }.getOrNull()
    val parsedTime = runCatching { LocalTime.parse(purchaseTime) }.getOrNull()
    val parsedTimestamp = if (parsedDate != null && parsedTime != null) {
        LocalDateTime.of(parsedDate, parsedTime)
    } else null
    val parsedUnits = units.v07Double().takeIf { it > 0.0 }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            Text("Add investment", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
            Text(
                "Record what you bought, what you paid, and the exact local date and time of the purchase.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InvestmentKind.entries) { item ->
                    OutlinedButton(onClick = { kind = item }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (kind == item) "• ${item.label}" else item.label)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (kind != InvestmentKind.CS2) {
                V07TextField(isin, "ISIN (optional)") { isin = it.uppercase().take(12) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            lookingUp = true
                            lookupText = null
                            OpenFigiService.lookupIsin(isin).fold(
                                onSuccess = { rows ->
                                    rows.firstOrNull()?.let { found ->
                                        name = found.name
                                        symbol = found.ticker
                                        figi = found.figi
                                        exchange = found.exchange
                                        kind = found.kind
                                        lookupText = "Found ${found.name}"
                                    } ?: run { lookupText = "No match found" }
                                },
                                onFailure = { lookupText = it.message ?: "Lookup failed" },
                            )
                            lookingUp = false
                        }
                    },
                    enabled = !lookingUp && OpenFigiService.isValidIsin(isin),
                ) { Text(if (lookingUp) "Looking up…" else "Lookup ISIN") }
                lookupText?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
            }

            V07TextField(name, if (kind == InvestmentKind.CS2) "Asset name" else "Name") { name = it }
            Spacer(Modifier.height(10.dp))
            if (kind == InvestmentKind.CS2) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Cs2AssetType.entries) { type ->
                        OutlinedButton(onClick = { cs2Type = type }, shape = RoundedCornerShape(20.dp)) {
                            Text(if (cs2Type == type) "• ${type.label}" else type.label)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                V07TextField(marketName, "Steam Market hash name") { marketName = it }
            } else {
                V07TextField(symbol, "Ticker / price symbol") { symbol = it.uppercase() }
            }
            Spacer(Modifier.height(10.dp))
            V07NumberField(amount, "Amount paid (€)") { amount = it }

            if (kind != InvestmentKind.CS2) {
                Spacer(Modifier.height(10.dp))
                V07NumberField(units, "Units owned (recommended)") { units = it }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter the exact fractional units shown by your brokerage. With a EUR market quote, Folio will use units × latest price instead of estimating from the purchase amount.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            V07TextField(purchaseDate, "Purchase date · YYYY-MM-DD") { purchaseDate = it.take(10) }
            Spacer(Modifier.height(10.dp))
            V07TextField(purchaseTime, "Purchase time · HH:mm") { purchaseTime = it.take(5) }
            Spacer(Modifier.height(8.dp))
            Text(
                "The time is kept as your purchase metadata. Market history remains daily-close data unless a future data source provides trustworthy intraday prices.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val timestamp = parsedTimestamp ?: return@Button
                    vm.addInvestmentV08(
                        kind = kind,
                        name = name.trim(),
                        symbol = symbol.trim(),
                        amount = amount.v07Double(),
                        purchaseDateTime = timestamp,
                        isin = isin.trim(),
                        figi = figi.trim(),
                        exchange = exchange.trim(),
                        units = parsedUnits ?: 0.0,
                        marketHashName = marketName.trim(),
                        cs2AssetType = cs2Type,
                    )
                    vm.refreshMarketPrices()
                    onDismiss()
                },
                enabled = name.isNotBlank() &&
                    amount.v07Double() > 0.0 &&
                    parsedTimestamp != null &&
                    !parsedTimestamp.isAfter(LocalDateTime.now()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Track investment") }
        }
    }
}
