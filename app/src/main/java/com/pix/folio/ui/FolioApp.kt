package com.pix.folio.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pix.folio.data.FolioNavigationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class V08RootTab {
    MONEY,
    HOME,
    PORTFOLIO,
}

@Composable
fun FolioApp(vm: V07ViewModel = viewModel()) {
    val context = LocalContext.current
    val tabs = V08RootTab.entries
    val navigationStore = remember(context.applicationContext) {
        FolioNavigationStore(context.applicationContext)
    }
    val initialTab = remember(navigationStore) {
        navigationStore.lastRootPage()
            ?.let { stored -> V08RootTab.entries.firstOrNull { it.name == stored } }
            ?: V08RootTab.HOME
    }
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(initialTab),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshMarketPrices()
    }

    LaunchedEffect(pagerState.currentPage) {
        tabs.getOrNull(pagerState.currentPage)?.let { tab ->
            navigationStore.setLastRootPage(tab.name)
        }
    }

    LaunchedEffect(vm.summary, vm.recurringSavingsRules) {
        withContext(Dispatchers.IO) { vm.mirrorToRoomV08() }
    }

    V07AppLockGate(vm) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) { page ->
            when (tabs[page]) {
                V08RootTab.MONEY -> V08MoneyScreen(vm)
                V08RootTab.HOME -> V08HomeScreen(
                    vm = vm,
                    onOpenMoney = {
                        scope.launch { pagerState.animateScrollToPage(tabs.indexOf(V08RootTab.MONEY)) }
                    },
                    onOpenPortfolio = {
                        scope.launch { pagerState.animateScrollToPage(tabs.indexOf(V08RootTab.PORTFOLIO)) }
                    },
                    onSettings = { showSettings = true },
                )
                V08RootTab.PORTFOLIO -> V08PortfolioScreen(vm)
            }
        }

        if (showSettings) {
            V08SettingsSheet(vm, onDismiss = { showSettings = false })
        }
    }
}

@Composable
private fun V07AppLockGate(vm: V07ViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context.findFragmentActivityV07()
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
            .setSubtitle("Use device security to view your finances")
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
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("FOLIO", letterSpacing = 2.8.sp, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Text("Locked", fontSize = 34.sp, lineHeight = 38.sp)
            Spacer(Modifier.height(7.dp))
            Text(
                "Your financial data is hidden.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = ::authenticate,
                modifier = Modifier.width(240.dp).height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text("UNLOCK FOLIO", fontSize = 15.sp, letterSpacing = 0.8.sp)
            }
        }
    }
}

private fun Context.findFragmentActivityV07(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}
