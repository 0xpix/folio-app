package com.pix.folio.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pix.folio.BuildConfig
import com.pix.folio.data.BetaRelease
import com.pix.folio.data.UpdateStatus
import com.pix.folio.data.UpdateUiState
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.PaymentCategory
import com.pix.folio.ui.components.NotoEmojiRenderer
import com.pix.folio.ui.theme.FolioGain
import com.pix.folio.ui.theme.FolioLoss
import com.pix.folio.updates.BetaUpdater
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class RootTab { HOME, EXPENSES, INVESTMENTS, PAYMENTS, MORE }
private val MoneyNumber = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DateLabel = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val Mono = FontFamily.Monospace

@Composable
fun FolioApp(vm: FolioViewModel = viewModel()) {
    var tab by remember { mutableStateOf(RootTab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FolioBottomBar(tab) { tab = it } },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                RootTab.HOME -> HomeScreen(vm.summary) { tab = RootTab.MORE }
                RootTab.EXPENSES -> ExpensesScreen(vm)
                RootTab.INVESTMENTS -> InvestmentsScreen(vm.summary)
                RootTab.PAYMENTS -> PaymentsScreen(vm)
                RootTab.MORE -> MoreScreen(vm)
            }
        }
    }
}

@Composable
private fun HomeScreen(summary: FolioSummary, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FolioMiniMark()
                Spacer(Modifier.width(10.dp))
                Text("Folio", fontSize = 25.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(28.dp))
        QuietLabel("TOTAL BALANCE")
        Text(
            euro(summary.totalBalance),
            fontSize = 46.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            signedEuro(summary.monthlyChange) + " this month",
            color = if (summary.monthlyChange >= 0) FolioGain else FolioLoss,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(30.dp))
        Sparkline(
            listOf(.24f, .29f, .31f, .36f, .34f, .42f, .47f, .45f, .53f, .58f, .62f, .69f),
            Modifier.fillMaxWidth().height(118.dp)
        )
        RangeRow()

        Spacer(Modifier.height(28.dp))
        SummaryRow("Income", euro(summary.monthlyIncome), "↗")
        SummaryRow("Expenses", "−${euro(summary.monthlyExpenses)}", "↘")
        SummaryRow("Investments", euro(summary.investmentTotal), "↗")
        SummaryRow("Payments", "−${euro(summary.payments.sumOf { it.amount })}", "□")
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExpensesScreen(vm: FolioViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    val summary = vm.summary
    val grouped = summary.expenses
        .groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Expenses", onAdd = { showAdd = true })
        Spacer(Modifier.height(30.dp))
        Text(euro(summary.monthlyExpenses), fontSize = 42.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp)
        Spacer(Modifier.height(3.dp))
        Text(YearMonth.now().atDay(1).format(MonthLabel), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))

        grouped.forEachIndexed { index, (category, amount) ->
            MoneyRow(
                glyph = category.glyph,
                title = category.label,
                trailing = euro(amount),
            )
            if (index != grouped.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "A quiet view of where the month went.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }

    if (showAdd) {
        AddExpenseSheet(
            onDismiss = { showAdd = false },
            onSave = { category, amount, note ->
                vm.addExpense(category, amount, note)
                showAdd = false
            }
        )
    }
}

@Composable
private fun InvestmentsScreen(summary: FolioSummary) {
    val total = summary.investmentTotal.coerceAtLeast(1.0)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Investments")
        Spacer(Modifier.height(30.dp))
        Text(euro(summary.investmentTotal), fontSize = 42.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp)
        Spacer(Modifier.height(4.dp))
        Text("+ ${euro(summary.monthlyInvestmentContribution)} this month", color = FolioGain, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        Sparkline(
            listOf(.20f, .26f, .29f, .35f, .39f, .44f, .42f, .50f, .55f, .61f, .66f, .76f),
            Modifier.fillMaxWidth().height(118.dp)
        )
        RangeRow()

        Spacer(Modifier.height(34.dp))
        QuietLabel("ALLOCATION")
        Spacer(Modifier.height(14.dp))
        summary.allocation.forEach { group ->
            AllocationRow(group.name, (group.amount / total).toFloat())
            Spacer(Modifier.height(18.dp))
        }

        Spacer(Modifier.height(16.dp))
        QuietLabel("PORTFOLIO")
        Spacer(Modifier.height(8.dp))
        summary.investments.forEachIndexed { index, holding ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(holding.name, fontSize = 15.sp)
                    Text(holding.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontSize = 10.sp)
                }
                Text(euro(holding.amount), fontSize = 14.sp)
            }
            if (index != summary.investments.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PaymentsScreen(vm: FolioViewModel) {
    var recent by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    val rows = vm.summary.payments
        .filter { if (recent) it.paid else !it.paid }
        .sortedBy { it.dueDate }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Payments", onAdd = { showAdd = true })
        Spacer(Modifier.height(24.dp))
        SegmentSwitch(
            left = "Upcoming",
            right = "Recent",
            rightSelected = recent,
            onChange = { recent = it },
        )
        Spacer(Modifier.height(26.dp))
        QuietLabel(if (recent) "COMPLETED" else "THIS MONTH")
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Spacer(Modifier.height(34.dp))
            Text(
                if (recent) "Nothing paid yet." else "No upcoming payments.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        } else {
            rows.forEachIndexed { index, payment ->
                PaymentRow(payment) { vm.togglePayment(payment.id) }
                if (index != rows.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (recent) "Tap a payment to move it back to upcoming." else "Tap a payment when it is paid.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }

    if (showAdd) {
        AddPaymentSheet(
            onDismiss = { showAdd = false },
            onSave = { category, name, amount, date ->
                vm.addPayment(category, name, amount, date)
                showAdd = false
            }
        )
    }
}

@Composable
private fun MoreScreen(vm: FolioViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf(UpdateUiState()) }
    var downloaded by remember { mutableStateOf<File?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Folio")
        Spacer(Modifier.height(30.dp))
        MenuRow("Overview", "Your money, simply.")
        MenuRow("Appearance", "Follows system")
        MenuRow("Widget", "Balance · 3×1")
        Spacer(Modifier.height(28.dp))
        QuietLabel("BETA")
        Spacer(Modifier.height(8.dp))
        MenuRow("Version", BuildConfig.VERSION_NAME)
        MenuRow("Channel", BuildConfig.UPDATE_CHANNEL)

        if (BuildConfig.GITHUB_BETA_UPDATES) {
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    updateState = updateState.copy(status = UpdateStatus.CHECKING, error = null)
                    scope.launch { updateState = BetaUpdater.check() }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (updateState.status == UpdateStatus.CHECKING) "Checking…" else "Check for beta")
            }
            Spacer(Modifier.height(12.dp))
            UpdateResult(context, updateState, downloaded) { release ->
                updateState = updateState.copy(status = UpdateStatus.DOWNLOADING)
                scope.launch {
                    BetaUpdater.download(context, release)
                        .onSuccess { file ->
                            downloaded = file
                            updateState = updateState.copy(status = UpdateStatus.READY)
                        }
                        .onFailure { error ->
                            updateState = updateState.copy(status = UpdateStatus.ERROR, error = error.message)
                        }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        QuietLabel("DATA")
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.resetDemo() }) {
            Text("Reset demo data", color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_COMMIT}",
            fontFamily = Mono,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateResult(
    context: Context,
    state: UpdateUiState,
    downloaded: File?,
    onDownload: (BetaRelease) -> Unit,
) {
    when (state.status) {
        UpdateStatus.UP_TO_DATE -> SmallNote("You're on the newest Folio beta.")
        UpdateStatus.ERROR -> SmallNote(state.error ?: "Couldn't check for updates.")
        UpdateStatus.AVAILABLE, UpdateStatus.DOWNLOADING, UpdateStatus.READY -> {
            val release = state.release ?: return
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text(release.tagName, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Text(release.title, fontSize = 17.sp)
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(release.notes, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                if (state.status == UpdateStatus.READY && downloaded != null) {
                    Button(
                        onClick = { BetaUpdater.install(context, downloaded) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("Install update") }
                } else {
                    Button(
                        onClick = { onDownload(release) },
                        enabled = state.status != UpdateStatus.DOWNLOADING && release.apkUrl != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text(if (state.status == UpdateStatus.DOWNLOADING) "Downloading…" else "Download update") }
                }
                TextButton(onClick = { BetaUpdater.openRelease(context, release) }) { Text("View release") }
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSave: (ExpenseCategory, Double, String) -> Unit,
) {
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Add expense", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(26.dp))
            TextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("€0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal),
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(18.dp))
            QuietLabel("CATEGORY")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries) { item ->
                    CategoryChip(
                        glyph = item.glyph,
                        label = item.label,
                        selected = category == item,
                        onClick = { category = item }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            TextField(
                value = note,
                onValueChange = { note = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Note (optional)") },
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(category, amount.toAmount(), note) },
                enabled = amount.toAmount() > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentSheet(
    onDismiss: () -> Unit,
    onSave: (PaymentCategory, String, Double, LocalDate) -> Unit,
) {
    var category by remember { mutableStateOf(PaymentCategory.HOME) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Add payment", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(22.dp))
            TextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name") },
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("€0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = day,
                onValueChange = { day = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Day of month") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(18.dp))
            QuietLabel("TYPE")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PaymentCategory.entries) { item ->
                    CategoryChip(item.glyph, item.label, category == item) { category = item }
                }
            }
            Spacer(Modifier.height(24.dp))
            val month = YearMonth.now()
            val safeDay = (day.toIntOrNull() ?: 1).coerceIn(1, month.lengthOfMonth())
            Button(
                onClick = { onSave(category, name, amount.toAmount(), month.atDay(safeDay)) },
                enabled = name.isNotBlank() && amount.toAmount() > 0.0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun CategoryChip(glyph: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonochromeEmoji(
            glyph,
            18,
            tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PaymentRow(payment: MonthlyPayment, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonochromeEmoji(payment.category.glyph, 24)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(payment.name, fontSize = 15.sp)
            Text(payment.dueDate.format(DateLabel), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Text(euro(payment.amount), fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (payment.paid) FolioGain else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                )
        )
    }
}

@Composable
private fun MoneyRow(glyph: String, title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        MonochromeEmoji(glyph, 24)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(trailing, fontSize = 14.sp)
    }
}

@Composable
private fun MonochromeEmoji(glyph: String, sizeDp: Int, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground) {
    val context = LocalContext.current
    val bitmap = remember(context, glyph, sizeDp) { NotoEmojiRenderer.render(context, glyph, sizeDp) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(sizeDp.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun PageHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "Add") }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, symbol: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, fontFamily = Mono, fontSize = 18.sp, modifier = Modifier.width(38.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AllocationRow(label: String, share: Float) {
    val normalized = share.coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${(normalized * 100).roundToInt()}%", fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Box(
                Modifier
                    .fillMaxWidth(normalized)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.onBackground, CircleShape)
            )
        }
    }
}

@Composable
private fun SegmentSwitch(
    left: String,
    right: String,
    rightSelected: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .padding(3.dp)
    ) {
        Segment(left, !rightSelected, Modifier.weight(1f)) { onChange(false) }
        Segment(right, rightSelected, Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(
                if (selected) MaterialTheme.colorScheme.background else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(19.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun MenuRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun QuietLabel(text: String) {
    Text(
        text,
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SmallNote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun FolioMiniMark() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.width(18.dp).height(5.dp).background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(4.dp)))
        Box(Modifier.width(13.dp).height(5.dp).background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(4.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(13.dp).background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onBackground
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        drawLine(guide, Offset(0f, size.height * .85f), Offset(size.width, size.height * .85f), strokeWidth = 1f)
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = size.height * (1f - value.coerceIn(0f, 1f))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

@Composable
private fun RangeRow() {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("1W", "1M", "3M", "1Y", "ALL").forEachIndexed { index, value ->
            Text(
                value,
                fontSize = 10.sp,
                fontWeight = if (index == 1) FontWeight.SemiBold else FontWeight.Normal,
                color = if (index == 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (index == 1) Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 5.dp) else Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun FolioBottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        BottomItem(RootTab.HOME, selected, Icons.Outlined.Home, "Home", onSelect)
        BottomItem(RootTab.EXPENSES, selected, Icons.Outlined.ReceiptLong, "Expenses", onSelect)
        BottomItem(RootTab.INVESTMENTS, selected, Icons.Outlined.TrendingUp, "Investments", onSelect)
        BottomItem(RootTab.PAYMENTS, selected, Icons.Outlined.Event, "Payments", onSelect)
        BottomItem(RootTab.MORE, selected, Icons.Outlined.MoreHoriz, "More", onSelect)
    }
}

@Composable
private fun BottomItem(tab: RootTab, selected: RootTab, icon: ImageVector, label: String, onSelect: (RootTab) -> Unit) {
    Column(
        Modifier.clickable { onSelect(tab) }.padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = if (tab == selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tab == selected) {
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 9.sp)
        } else {
            Spacer(Modifier.height(15.dp))
        }
    }
}

@Composable
private fun cleanFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
)

private fun String.toAmount(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun euro(value: Double): String = "€${MoneyNumber.format(value)}"
private fun signedEuro(value: Double): String = if (value >= 0) "+ ${euro(value)}" else "−${euro(-value)}"
