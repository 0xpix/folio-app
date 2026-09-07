package com.pix.folio.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pix.folio.BuildConfig
import com.pix.folio.data.BetaRelease
import com.pix.folio.data.OpenFigiService
import com.pix.folio.data.UpdateStatus
import com.pix.folio.data.UpdateUiState
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.InvestmentKind
import com.pix.folio.model.InvestmentTransaction
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.PaymentCategory
import com.pix.folio.model.RecurringIncome
import com.pix.folio.model.RecurringInvestment
import com.pix.folio.model.ValueSnapshot
import com.pix.folio.ui.components.NotoEmojiRenderer
import com.pix.folio.ui.theme.FolioGain
import com.pix.folio.ui.theme.FolioLoss
import com.pix.folio.updates.BetaUpdater
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class RootTab { HOME, EXPENSES, INVESTMENTS, RECURRING, MORE }
private enum class DetailPage { MONTH, NET_WORTH }
private enum class RecurringTab { PAYMENTS, INCOME, INVESTMENTS }

private enum class ChartRange(val label: String, val days: Long?) {
    WEEK("1W", 7),
    MONTH("1M", 31),
    THREE_MONTHS("3M", 93),
    YEAR("1Y", 366),
    ALL("ALL", null),
}

private val MoneyNumber = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DateLabel = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val DateTimeLabel = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.ENGLISH)
private val Mono = FontFamily.Monospace

@Composable
fun FolioApp(vm: FolioViewModel = viewModel()) {
    AppLockGate(vm) {
        FolioShell(vm)
    }
}

@Composable
private fun AppLockGate(vm: FolioViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by rememberSaveable(vm.appLockEnabled) { mutableStateOf(!vm.appLockEnabled) }

    val prompt = remember(activity) {
        if (activity == null) null else BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            },
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Folio")
            .setSubtitle("Use your device security to open your finances")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    fun authenticate() {
        prompt?.authenticate(promptInfo)
    }

    LaunchedEffect(vm.appLockEnabled, activity) {
        if (vm.appLockEnabled && !unlocked && activity != null) authenticate()
    }

    DisposableEffect(lifecycleOwner, vm.appLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && vm.appLockEnabled) unlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!vm.appLockEnabled || unlocked) {
        content()
    } else {
        LockScreen(onUnlock = ::authenticate)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolioMiniMark()
        Spacer(Modifier.height(22.dp))
        Text("Folio", fontSize = 31.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Locked", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono, fontSize = 11.sp)
        Spacer(Modifier.height(26.dp))
        OutlinedButton(onClick = onUnlock, shape = RoundedCornerShape(24.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Unlock Folio")
        }
    }
}

@Composable
private fun FolioShell(vm: FolioViewModel) {
    var tab by remember { mutableStateOf(RootTab.HOME) }
    var detail by remember { mutableStateOf<DetailPage?>(null) }

    if (detail != null) {
        when (detail) {
            DetailPage.MONTH -> MonthlyOverviewScreen(vm.summary) { detail = null }
            DetailPage.NET_WORTH -> NetWorthHistoryScreen(vm.summary) { detail = null }
            null -> Unit
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FolioBottomBar(tab) { tab = it } },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                RootTab.HOME -> HomeScreen(
                    summary = vm.summary,
                    onSettings = { tab = RootTab.MORE },
                    onMonth = { detail = DetailPage.MONTH },
                    onNetWorthHistory = { detail = DetailPage.NET_WORTH },
                )
                RootTab.EXPENSES -> ExpensesScreen(vm)
                RootTab.INVESTMENTS -> InvestmentsScreen(vm)
                RootTab.RECURRING -> RecurringScreen(vm)
                RootTab.MORE -> MoreScreen(vm)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    summary: FolioSummary,
    onSettings: () -> Unit,
    onMonth: () -> Unit,
    onNetWorthHistory: () -> Unit,
) {
    var range by remember { mutableStateOf(ChartRange.MONTH) }
    val chart = historySeries(summary.balanceHistory, range)
    val upcoming = remember(summary) { upcomingItems(summary) }

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
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }

        Spacer(Modifier.height(28.dp))
        QuietLabel("NET WORTH")
        Text(
            euro(summary.totalBalance),
            fontSize = 46.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            signedEuro(summary.monthlyChange) + " monthly cash flow",
            color = if (summary.monthlyChange >= 0) FolioGain else FolioLoss,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuietLabel("HISTORY")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNetWorthHistory) { Text("View", fontSize = 12.sp) }
        }
        HistoryChart(chart, "Your net-worth history starts as you use Folio.")
        RangeRow(range) { range = it }

        Spacer(Modifier.height(26.dp))
        ClickableSectionHeader("THIS MONTH", "Overview", onMonth)
        val month = summary.monthOverview(YearMonth.now())
        SummaryRow("Income", euro(month.income), "↗")
        SummaryRow("Spent", "−${euro(month.expenses + month.payments)}", "↘")
        SummaryRow("Invested", "−${euro(month.invested)}", "↑")
        SummaryRow("Left", signedEuro(month.left), "=")

        Spacer(Modifier.height(26.dp))
        QuietLabel("UPCOMING")
        Spacer(Modifier.height(8.dp))
        if (upcoming.isEmpty()) {
            SmallNote("Nothing upcoming this month.")
        } else {
            upcoming.take(4).forEachIndexed { index, item ->
                UpcomingRow(item)
                if (index != upcoming.take(4).lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (summary.totalBalance == 0.0 && summary.expenses.isEmpty() && summary.incomes.isEmpty()) {
            Spacer(Modifier.height(26.dp))
            SmallNote("Start clean. Set your cash balance, add an investment by ISIN, or add recurring salary.")
            TextButton(onClick = onSettings) { Text("Set starting balance") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ExpensesScreen(vm: FolioViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    val summary = vm.summary
    val grouped = summary.currentMonthExpenses
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
        ClickableSectionHeader("BUDGETS", if (summary.budgets.isEmpty()) "Set budget" else "Edit", { showBudget = true })
        if (summary.budgets.isEmpty()) {
            SmallNote("Optional. Add only the categories you want to keep an eye on.")
        } else {
            Spacer(Modifier.height(10.dp))
            summary.budgets.forEach { budget ->
                val spent = summary.spentFor(budget.category)
                BudgetRow(budget.category, spent, budget.monthlyLimit)
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        QuietLabel("SPENDING")
        Spacer(Modifier.height(8.dp))
        if (grouped.isEmpty()) {
            EmptyState("No expenses this month.", "Press + when you spend something.")
        } else {
            grouped.forEachIndexed { index, (category, amount) ->
                MoneyRow(category.glyph, category.label, euro(amount))
                if (index != grouped.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showAdd) {
        AddExpenseSheet(
            onDismiss = { showAdd = false },
            onSave = { category, amount, note ->
                vm.addExpense(category, amount, note)
                showAdd = false
            },
        )
    }

    if (showBudget) {
        BudgetSheet(
            summary = summary,
            onDismiss = { showBudget = false },
            onSave = { category, amount ->
                vm.setBudget(category, amount)
                showBudget = false
            },
        )
    }
}

@Composable
private fun InvestmentsScreen(vm: FolioViewModel) {
    val summary = vm.summary
    val total = summary.investmentTotal
    var range by remember { mutableStateOf(ChartRange.MONTH) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedHoldingId by remember { mutableStateOf<String?>(null) }
    val chart = historySeries(summary.investmentHistory, range)
    val selectedHolding = summary.investments.firstOrNull { it.id == selectedHoldingId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Investments", onAdd = { showAdd = true })
        Spacer(Modifier.height(30.dp))
        Text(euro(total), fontSize = 42.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (summary.investments.isEmpty()) "No holdings yet" else "${summary.investments.size} tracked holding${if (summary.investments.size == 1) "" else "s"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(28.dp))
        HistoryChart(chart, "Portfolio history starts when you add investments.")
        RangeRow(range) { range = it }

        Spacer(Modifier.height(32.dp))
        if (summary.investments.isEmpty()) {
            EmptyState("No investments yet.", "Press + and enter an ISIN to find the instrument with OpenFIGI.")
        } else {
            QuietLabel("ALLOCATION")
            Spacer(Modifier.height(14.dp))
            summary.allocation.forEach { group ->
                AllocationRow(group.name, if (total > 0.0) (group.amount / total).toFloat() else 0f)
                Spacer(Modifier.height(18.dp))
            }

            Spacer(Modifier.height(14.dp))
            QuietLabel("PORTFOLIO")
            Spacer(Modifier.height(8.dp))
            summary.investments.forEachIndexed { index, holding ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedHoldingId = holding.id }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(holding.name, fontSize = 15.sp)
                        Text(
                            listOf(holding.kind.label, holding.symbol, holding.isin)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = Mono,
                            fontSize = 10.sp,
                        )
                    }
                    Text(euro(holding.amount), fontSize = 14.sp)
                }
                if (index != summary.investments.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showAdd) {
        AddInvestmentSheet(
            onDismiss = { showAdd = false },
            onSave = { kind, name, symbol, amount, isin, figi, exchange ->
                vm.addInvestment(kind, name, symbol, amount, isin, figi, exchange)
                showAdd = false
            },
        )
    }

    if (selectedHolding != null) {
        InvestmentHistorySheet(
            holding = selectedHolding,
            transactions = summary.investmentTransactions.filter { it.holdingId == selectedHolding.id },
            onDismiss = { selectedHoldingId = null },
            onAddContribution = { amount -> vm.addInvestmentContribution(selectedHolding.id, amount) },
            onRemove = {
                vm.removeInvestment(selectedHolding.id)
                selectedHoldingId = null
            },
        )
    }
}

@Composable
private fun RecurringScreen(vm: FolioViewModel) {
    var selectedTab by remember { mutableStateOf(RecurringTab.PAYMENTS) }
    var showAddPayment by remember { mutableStateOf(false) }
    var showAddIncome by remember { mutableStateOf(false) }
    var showAddInvestment by remember { mutableStateOf(false) }
    val summary = vm.summary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Recurring", onAdd = {
            when (selectedTab) {
                RecurringTab.PAYMENTS -> showAddPayment = true
                RecurringTab.INCOME -> showAddIncome = true
                RecurringTab.INVESTMENTS -> showAddInvestment = true
            }
        })
        Spacer(Modifier.height(24.dp))
        ThreeSegmentSwitch(selectedTab) { selectedTab = it }
        Spacer(Modifier.height(26.dp))

        when (selectedTab) {
            RecurringTab.PAYMENTS -> {
                QuietLabel("MONTHLY PAYMENTS")
                Spacer(Modifier.height(8.dp))
                val payments = summary.payments.sortedBy { it.dayOfMonth }
                if (payments.isEmpty()) {
                    EmptyState("No recurring payments.", "Press + to add rent, phone, gym, or another bill.")
                } else {
                    payments.forEachIndexed { index, payment ->
                        PaymentRow(payment) { vm.togglePayment(payment.id) }
                        if (index != payments.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Spacer(Modifier.height(18.dp))
                    SmallNote("Tap a payment when it is paid. It resets automatically next month.")
                }
            }
            RecurringTab.INCOME -> {
                QuietLabel("MONTHLY INCOME")
                Spacer(Modifier.height(8.dp))
                val incomes = summary.incomes.sortedBy { it.dayOfMonth }
                if (incomes.isEmpty()) {
                    EmptyState("No recurring income.", "Press + to add salary or another monthly income.")
                } else {
                    incomes.forEachIndexed { index, income ->
                        IncomeRow(income) { vm.toggleIncomeReceived(income.id) }
                        if (index != incomes.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Spacer(Modifier.height(18.dp))
                    SmallNote("Tap salary when it arrives. Folio credits your cash balance for that month.")
                }
            }
            RecurringTab.INVESTMENTS -> {
                QuietLabel("MONTHLY INVESTMENTS")
                Spacer(Modifier.height(8.dp))
                val rows = summary.recurringInvestments.sortedBy { it.dayOfMonth }
                if (rows.isEmpty()) {
                    EmptyState("No recurring investments.", "Press + to link a monthly contribution to one of your holdings.")
                } else {
                    rows.forEachIndexed { index, row ->
                        val holding = summary.investments.firstOrNull { it.id == row.holdingId }
                        RecurringInvestmentRow(row, holding) { vm.toggleRecurringInvestment(row.id) }
                        if (index != rows.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Spacer(Modifier.height(18.dp))
                    SmallNote("Tap when the contribution executes. Folio moves the amount from cash into the holding.")
                }
            }
        }
    }

    if (showAddPayment) {
        AddPaymentSheet(
            onDismiss = { showAddPayment = false },
            onSave = { category, name, amount, day ->
                vm.addPayment(category, name, amount, day)
                showAddPayment = false
            },
        )
    }

    if (showAddIncome) {
        AddIncomeSheet(
            onDismiss = { showAddIncome = false },
            onSave = { name, amount, day ->
                vm.addIncome(name, amount, day)
                showAddIncome = false
            },
        )
    }

    if (showAddInvestment) {
        AddRecurringInvestmentSheet(
            holdings = summary.investments,
            onDismiss = { showAddInvestment = false },
            onSave = { holdingId, amount, day ->
                vm.addRecurringInvestment(holdingId, amount, day)
                showAddInvestment = false
            },
        )
    }
}

@Composable
private fun MoreScreen(vm: FolioViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf(UpdateUiState()) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var showBalance by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val lockAvailable = BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        PageHeader("Folio")
        Spacer(Modifier.height(30.dp))
        MenuRow("Cash balance", euro(vm.summary.cashBalance), onClick = { showBalance = true })
        MenuRow("Appearance", "Follows system")
        MenuRow("Widget", "Balance · 3×1")
        MenuRow(
            "App lock",
            when {
                !lockAvailable -> "Unavailable"
                vm.appLockEnabled -> "On"
                else -> "Off"
            },
            onClick = if (lockAvailable) ({ vm.setAppLockEnabled(!vm.appLockEnabled) }) else null,
        )

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
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { showClear = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(22.dp),
        ) { Text("Clear all Folio data") }
        Spacer(Modifier.height(24.dp))
        Text(
            "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.GIT_COMMIT}",
            fontFamily = Mono,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showBalance) {
        SetBalanceSheet(
            current = vm.summary.cashBalance,
            onDismiss = { showBalance = false },
            onSave = {
                vm.setCashBalance(it)
                showBalance = false
            },
        )
    }

    if (showClear) {
        ConfirmClearSheet(
            onDismiss = { showClear = false },
            onConfirm = {
                vm.clearAll()
                showClear = false
            },
        )
    }
}

@Composable
private fun MonthlyOverviewScreen(summary: FolioSummary, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val overview = summary.monthOverview(month)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        BackHeader("Monthly overview", onBack)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Spacer(Modifier.weight(1f))
            Text(month.atDay(1).format(MonthLabel), fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (month < YearMonth.now()) month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Text("›") }
        }
        Spacer(Modifier.height(24.dp))
        QuietLabel("LEFT")
        Text(
            signedEuro(overview.left),
            fontSize = 44.sp,
            color = if (overview.left >= 0) MaterialTheme.colorScheme.onBackground else FolioLoss,
        )
        Spacer(Modifier.height(28.dp))
        SummaryRow("Income", euro(overview.income), "↗")
        SummaryRow("Expenses", "−${euro(overview.expenses)}", "↘")
        SummaryRow("Payments", "−${euro(overview.payments)}", "□")
        SummaryRow("Invested", "−${euro(overview.invested)}", "↑")

        Spacer(Modifier.height(28.dp))
        QuietLabel("ACTIVITY")
        Spacer(Modifier.height(8.dp))
        val monthExpenses = summary.expenses.filter { YearMonth.from(it.date) == month }
        val monthLedger = summary.ledger.filter { YearMonth.from(it.date) == month }
        val monthInvestments = summary.investmentTransactions.filter { YearMonth.from(it.date) == month }
        if (monthExpenses.isEmpty() && monthLedger.isEmpty() && monthInvestments.isEmpty()) {
            EmptyState("No activity.", "Folio will build this month as you record money movement.")
        } else {
            monthLedger.sortedByDescending { it.date }.forEach { entry ->
                SimpleHistoryRow(entry.date.format(DateLabel), entry.name, if (entry.type.name == "INCOME") "+${euro(entry.amount)}" else "−${euro(entry.amount)}")
            }
            monthExpenses.sortedByDescending { it.date }.forEach { expense ->
                SimpleHistoryRow(expense.date.format(DateLabel), expense.category.label, "−${euro(expense.amount)}")
            }
            monthInvestments.sortedByDescending { it.date }.forEach { tx ->
                val holding = summary.investments.firstOrNull { it.id == tx.holdingId }
                SimpleHistoryRow(tx.date.format(DateLabel), holding?.name ?: "Investment", "−${euro(tx.amount)}")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NetWorthHistoryScreen(summary: FolioSummary, onBack: () -> Unit) {
    var range by remember { mutableStateOf(ChartRange.YEAR) }
    val filtered = filteredHistory(summary.balanceHistory, range)
    val chart = normalizeHistory(filtered)
    val change = if (filtered.size >= 2) filtered.last().value - filtered.first().value else 0.0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        BackHeader("Net-worth history", onBack)
        Spacer(Modifier.height(30.dp))
        QuietLabel("CURRENT")
        Text(euro(summary.totalBalance), fontSize = 42.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (filtered.size >= 2) signedEuro(change) + " in selected range" else "History builds from your Folio activity",
            color = if (change >= 0) FolioGain else FolioLoss,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
        HistoryChart(chart, "No net-worth snapshots in this range.")
        RangeRow(range) { range = it }
        Spacer(Modifier.height(28.dp))
        QuietLabel("SNAPSHOTS")
        Spacer(Modifier.height(8.dp))
        filtered.takeLast(12).reversed().forEachIndexed { index, snapshot ->
            val local = Instant.ofEpochMilli(snapshot.atMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
            SimpleHistoryRow(local.format(DateTimeLabel), "Net worth", euro(snapshot.value))
            if (index != filtered.takeLast(12).lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun UpdateResult(context: Context, state: UpdateUiState, downloaded: File?, onDownload: (BetaRelease) -> Unit) {
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
                    Button(onClick = { BetaUpdater.install(context, downloaded) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Text("Install update")
                    }
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
private fun AddExpenseSheet(onDismiss: () -> Unit, onSave: (ExpenseCategory, Double, String) -> Unit) {
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Add expense", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(26.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(18.dp))
            QuietLabel("CATEGORY")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries) { item ->
                    CategoryChip(item.glyph, item.label, category == item) { category = item }
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
            SaveButton(enabled = amount.toAmount() > 0.0) { onSave(category, amount.toAmount(), note) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(summary: FolioSummary, onDismiss: () -> Unit, onSave: (ExpenseCategory, Double) -> Unit) {
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var amount by remember(category) { mutableStateOf(summary.budgetFor(category)?.monthlyLimit?.let { MoneyNumber.format(it) } ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Monthly budget", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            SmallNote("Keep it minimal: only set limits for categories you actually want to watch.")
            Spacer(Modifier.height(18.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries) { item ->
                    CategoryChip(item.glyph, item.label, category == item) {
                        category = item
                        amount = summary.budgetFor(item)?.monthlyLimit?.let { MoneyNumber.format(it) } ?: ""
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(10.dp))
            SmallNote("Enter 0 to remove this category budget.")
            Spacer(Modifier.height(24.dp))
            SaveButton(enabled = true) { onSave(category, amount.toAmount()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInvestmentSheet(
    onDismiss: () -> Unit,
    onSave: (InvestmentKind, String, String, Double, String, String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isin by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(InvestmentKind.ETF) }
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var figi by remember { mutableStateOf("") }
    var exchange by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OpenFigiService.Instrument>>(emptyList()) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var lookingUp by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Add investment", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            SmallNote("Enter an ISIN first. Folio resolves the instrument with OpenFIGI; you choose the exact listing.")
            Spacer(Modifier.height(20.dp))

            TextField(
                value = isin,
                onValueChange = { isin = it.filter { ch -> ch.isLetterOrDigit() }.take(12).uppercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ISIN · e.g. IE00B4L5Y983") },
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    lookingUp = true
                    lookupError = null
                    results = emptyList()
                    scope.launch {
                        OpenFigiService.lookupIsin(isin)
                            .onSuccess { found ->
                                results = found
                                if (found.isEmpty()) lookupError = "No OpenFIGI matches found for this ISIN."
                            }
                            .onFailure { lookupError = it.message ?: "Couldn't look up this ISIN." }
                        lookingUp = false
                    }
                },
                enabled = !lookingUp && isin.length == 12,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(22.dp),
            ) { Text(if (lookingUp) "Looking up…" else "Find by ISIN") }

            lookupError?.let {
                Spacer(Modifier.height(10.dp))
                SmallNote(it)
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                QuietLabel("MATCHING LISTINGS")
                Spacer(Modifier.height(8.dp))
                results.forEachIndexed { index, item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                name = item.name
                                symbol = item.ticker
                                kind = item.kind
                                figi = item.figi
                                exchange = item.exchange
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(item.name, fontSize = 14.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            listOf(item.ticker, item.exchange, item.securityType2).filter { it.isNotBlank() }.joinToString(" · "),
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != results.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Spacer(Modifier.height(20.dp))
            QuietLabel("SELECTED")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InvestmentKind.entries) { item -> TextChoiceChip(item.label, kind == item) { kind = item } }
            }
            Spacer(Modifier.height(12.dp))
            TextField(
                value = name,
                onValueChange = { name = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Instrument name") },
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = symbol,
                onValueChange = { symbol = it.take(20).uppercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ticker / symbol") },
                singleLine = true,
                colors = cleanFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(8.dp))
            SmallNote("Amount = the value you want Folio to track for this holding.")
            Spacer(Modifier.height(24.dp))
            SaveButton(enabled = name.isNotBlank() && amount.toAmount() > 0.0) {
                onSave(kind, name, symbol, amount.toAmount(), isin, figi, exchange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentHistorySheet(
    holding: InvestmentHolding,
    transactions: List<InvestmentTransaction>,
    onDismiss: () -> Unit,
    onAddContribution: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(holding.name, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                listOf(holding.symbol, holding.exchange, holding.isin).filter { it.isNotBlank() }.joinToString(" · "),
                fontFamily = Mono,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(26.dp))
            QuietLabel("TRACKED VALUE")
            Text(euro(holding.amount), fontSize = 38.sp)

            Spacer(Modifier.height(26.dp))
            QuietLabel("ADD CONTRIBUTION")
            Spacer(Modifier.height(10.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    onAddContribution(amount.toAmount())
                    amount = ""
                },
                enabled = amount.toAmount() > 0.0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Add to holding") }
            Spacer(Modifier.height(6.dp))
            SmallNote("A contribution moves the same amount out of your Folio cash balance.")

            Spacer(Modifier.height(26.dp))
            QuietLabel("HISTORY")
            Spacer(Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                SmallNote("No contribution history yet.")
            } else {
                transactions.sortedByDescending { it.date }.forEachIndexed { index, tx ->
                    SimpleHistoryRow(
                        tx.date.format(DateLabel),
                        if (tx.source.name == "RECURRING") "Recurring investment" else "Contribution",
                        "+${euro(tx.amount)}",
                    )
                    if (index != transactions.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(26.dp))
            TextButton(onClick = onRemove) { Text("Remove holding", color = FolioLoss) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentSheet(onDismiss: () -> Unit, onSave: (PaymentCategory, String, Double, Int) -> Unit) {
    var category by remember { mutableStateOf(PaymentCategory.HOME) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Add monthly payment", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            TextField(value = name, onValueChange = { name = it.take(40) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Name") }, singleLine = true, colors = cleanFieldColors())
            Spacer(Modifier.height(10.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(10.dp))
            DayField(day) { day = it }
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PaymentCategory.entries) { item -> CategoryChip(item.glyph, item.label, category == item) { category = item } }
            }
            Spacer(Modifier.height(24.dp))
            SaveButton(enabled = name.isNotBlank() && amount.toAmount() > 0.0) {
                onSave(category, name, amount.toAmount(), day.toIntOrNull() ?: 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIncomeSheet(onDismiss: () -> Unit, onSave: (String, Double, Int) -> Unit) {
    var name by remember { mutableStateOf("Salary") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("30") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Recurring income", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            TextField(value = name, onValueChange = { name = it.take(40) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Salary") }, singleLine = true, colors = cleanFieldColors())
            Spacer(Modifier.height(10.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(10.dp))
            DayField(day) { day = it }
            Spacer(Modifier.height(24.dp))
            SaveButton(enabled = name.isNotBlank() && amount.toAmount() > 0.0) {
                onSave(name, amount.toAmount(), day.toIntOrNull() ?: 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringInvestmentSheet(
    holdings: List<InvestmentHolding>,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int) -> Unit,
) {
    var holdingId by remember { mutableStateOf(holdings.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("2") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Recurring investment", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            if (holdings.isEmpty()) {
                EmptyState("Add a holding first.", "Recurring investments are linked to an ETF, stock, fund, or other holding.")
            } else {
                QuietLabel("HOLDING")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(holdings) { holding ->
                        TextChoiceChip(holding.symbol.ifBlank { holding.name.take(12) }, holdingId == holding.id) { holdingId = holding.id }
                    }
                }
                Spacer(Modifier.height(18.dp))
                AmountField(amount) { amount = it }
                Spacer(Modifier.height(10.dp))
                DayField(day) { day = it }
                Spacer(Modifier.height(24.dp))
                SaveButton(enabled = holdingId.isNotBlank() && amount.toAmount() > 0.0) {
                    onSave(holdingId, amount.toAmount(), day.toIntOrNull() ?: 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetBalanceSheet(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf(if (current > 0) MoneyNumber.format(current) else "") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Cash balance", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(24.dp))
            SaveButton(enabled = true) { onSave(amount.toAmount()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmClearSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Clear Folio?", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            SmallNote("This removes your local balances, expenses, budgets, recurring items, investments, and history. App lock preference is kept.")
            Spacer(Modifier.height(22.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Text("Clear local data") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("€0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Normal),
        colors = cleanFieldColors(),
    )
}

@Composable
private fun DayField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(2)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Day of month") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = cleanFieldColors(),
    )
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(22.dp)) {
        Text("Save")
    }
}

@Composable
private fun CategoryChip(glyph: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonochromeEmoji(glyph, if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground, 16)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 11.sp, color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun TextChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 11.sp, color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PaymentRow(payment: MonthlyPayment, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        MonochromeEmoji(payment.category.glyph, MaterialTheme.colorScheme.onBackground, 22)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(payment.name, fontSize = 14.sp)
            Text("Day ${payment.dayOfMonth}", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(euro(payment.amount), fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        StatusDot(payment.paid)
    }
}

@Composable
private fun IncomeRow(income: RecurringIncome, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        MonochromeEmoji(income.glyph, MaterialTheme.colorScheme.onBackground, 22)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(income.name, fontSize = 14.sp)
            Text("Day ${income.dayOfMonth}", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+${euro(income.amount)}", fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        StatusDot(income.received)
    }
}

@Composable
private fun RecurringInvestmentRow(row: RecurringInvestment, holding: InvestmentHolding?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("↑", fontFamily = Mono, fontSize = 19.sp, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(holding?.name ?: "Missing holding", fontSize = 14.sp)
            Text("Day ${row.dayOfMonth}", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(euro(row.amount), fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        StatusDot(row.applied)
    }
}

private data class UpcomingItem(val title: String, val glyph: String, val amount: Double, val due: LocalDate, val incoming: Boolean)

private fun upcomingItems(summary: FolioSummary): List<UpcomingItem> {
    val paymentItems = summary.payments.filterNot { it.paid }.map {
        UpcomingItem(it.name, it.category.glyph, it.amount, it.dueDate, false)
    }
    val incomeItems = summary.incomes.filterNot { it.received }.map {
        UpcomingItem(it.name, it.glyph, it.amount, it.dueDate, true)
    }
    val investmentItems = summary.recurringInvestments.filterNot { it.applied }.mapNotNull { row ->
        val holding = summary.investments.firstOrNull { it.id == row.holdingId } ?: return@mapNotNull null
        UpcomingItem(holding.name, "📈", row.amount, row.dueDate, false)
    }
    return (paymentItems + incomeItems + investmentItems).sortedBy { it.due }
}

@Composable
private fun UpcomingRow(item: UpcomingItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        MonochromeEmoji(item.glyph, MaterialTheme.colorScheme.onBackground, 20)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp)
            Text(
                if (item.due.isBefore(LocalDate.now())) "Overdue · ${item.due.format(DateLabel)}" else item.due.format(DateLabel),
                fontFamily = Mono,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text((if (item.incoming) "+" else "−") + euro(item.amount), fontSize = 13.sp)
    }
}

@Composable
private fun BudgetRow(category: ExpenseCategory, spent: Double, limit: Double) {
    val progress = if (limit <= 0.0) 0f else (spent / limit).toFloat().coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonochromeEmoji(category.glyph, MaterialTheme.colorScheme.onBackground, 18)
            Spacer(Modifier.width(10.dp))
            Text(category.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${euro(spent)} / ${euro(limit)}", fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            if (progress > 0f) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(Modifier.size(8.dp).background(if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun MoneyRow(glyph: String, title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        MonochromeEmoji(glyph, MaterialTheme.colorScheme.onBackground, 20)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(trailing, fontSize = 13.sp)
    }
}

@Composable
private fun MonochromeEmoji(glyph: String, color: Color, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(glyph, size) { NotoEmojiRenderer.render(context, glyph, size) }
    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(size.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
    )
}

@Composable
private fun PageHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
        Spacer(Modifier.weight(1f))
        if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "Add") }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ClickableSectionHeader(label: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QuietLabel(label)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action, fontSize = 12.sp) }
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
private fun SimpleHistoryRow(date: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(date, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp)
    }
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
            if (normalized > 0f) {
                Box(Modifier.fillMaxWidth(normalized).height(5.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
            }
        }
    }
}

@Composable
private fun ThreeSegmentSwitch(selected: RecurringTab, onSelect: (RecurringTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp)).padding(3.dp)) {
        RecurringTab.entries.forEach { tab ->
            Segment(
                label = when (tab) {
                    RecurringTab.PAYMENTS -> "Payments"
                    RecurringTab.INCOME -> "Income"
                    RecurringTab.INVESTMENTS -> "Invest"
                },
                selected = selected == tab,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) MaterialTheme.colorScheme.background else Color.Transparent, RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 11.sp) }
}

@Composable
private fun MenuRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 16.dp)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
        Text(title, fontSize = 16.sp)
        Spacer(Modifier.height(5.dp))
        Text(detail, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuietLabel(text: String) {
    Text(text, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun HistoryChart(values: List<Float>, emptyMessage: String) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(118.dp), contentAlignment = Alignment.CenterStart) {
            Text(emptyMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Sparkline(values, Modifier.fillMaxWidth().height(118.dp))
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onBackground
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        drawLine(guide, Offset(0f, size.height * .85f), Offset(size.width, size.height * .85f), strokeWidth = 1f)
        if (values.isEmpty()) return@Canvas
        if (values.size == 1) {
            drawCircle(line, radius = 4f, center = Offset(size.width, size.height * (1f - values.first().coerceIn(0f, 1f))))
            return@Canvas
        }
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
private fun RangeRow(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ChartRange.entries.forEach { range ->
            val active = range == selected
            Text(
                range.label,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(range) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
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
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        BottomItem(RootTab.HOME, selected, Icons.Outlined.Home, "Home", Modifier.weight(1f), onSelect)
        BottomItem(RootTab.EXPENSES, selected, Icons.Outlined.ReceiptLong, "Expenses", Modifier.weight(1f), onSelect)
        BottomItem(RootTab.INVESTMENTS, selected, Icons.Outlined.TrendingUp, "Investments", Modifier.weight(1f), onSelect)
        BottomItem(RootTab.RECURRING, selected, Icons.Outlined.Event, "Recurring", Modifier.weight(1f), onSelect)
        BottomItem(RootTab.MORE, selected, Icons.Outlined.MoreHoriz, "More", Modifier.weight(1f), onSelect)
    }
}

@Composable
private fun BottomItem(
    tab: RootTab,
    selected: RootTab,
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onSelect: (RootTab) -> Unit,
) {
    val active = tab == selected
    Box(modifier.height(54.dp).clickable { onSelect(tab) }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun cleanFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

private fun filteredHistory(history: List<ValueSnapshot>, range: ChartRange): List<ValueSnapshot> {
    if (history.isEmpty()) return emptyList()
    val cutoff = range.days?.let { System.currentTimeMillis() - it * 86_400_000L }
    val filtered = history.filter { cutoff == null || it.atMillis >= cutoff }
    return if (filtered.isEmpty()) history.takeLast(1) else filtered
}

private fun normalizeHistory(history: List<ValueSnapshot>): List<Float> {
    if (history.isEmpty()) return emptyList()
    val values = history.map { it.value }
    if (values.size == 1) return listOf(.5f)
    val min = values.minOrNull() ?: return emptyList()
    val max = values.maxOrNull() ?: return emptyList()
    if (max == min) return List(values.size) { .5f }
    return values.map { ((it - min) / (max - min)).toFloat().coerceIn(.08f, .92f) }
}

private fun historySeries(history: List<ValueSnapshot>, range: ChartRange): List<Float> = normalizeHistory(filteredHistory(history, range))

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun String.toAmount(): Double = replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
private fun euro(value: Double): String = "€${MoneyNumber.format(value)}"
private fun signedEuro(value: Double): String = if (value >= 0) "+${euro(value)}" else "−${euro(-value)}"
