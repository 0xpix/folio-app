package com.pix.folio.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pix.folio.BuildConfig
import com.pix.folio.data.UpdateStatus
import com.pix.folio.data.UpdateUiState
import com.pix.folio.model.AppFontChoice
import com.pix.folio.updates.BetaUpdater
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V07SettingsSheet(vm: V07ViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf(UpdateUiState()) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text(
                "Keep Folio quiet, automatic, and easy to scan.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            V07SectionLabel("Typography")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFontChoice.entries.forEach { choice ->
                    OutlinedButton(
                        onClick = { vm.updateFontChoice(choice) },
                        shape = RoundedCornerShape(20.dp),
                    ) { Text(if (vm.fontChoice == choice) "• ${choice.label}" else choice.label) }
                }
            }
            Text(
                "Pixify uses Pixelify Sans and is the default.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Automation")
            SettingSwitchRow(
                title = "Automatic monthly plan",
                detail = "Apply due salary, fixed payments and recurring investments without tapping each item.",
                checked = vm.autoRecurringEnabled,
                onCheckedChange = vm::updateAutoRecurringEnabled,
            )
            V07Divider()
            V07Metric(
                "Run recurring plan now",
                "Run",
                "Useful after changing a due date or adding a recurring item.",
                onClick = vm::runRecurringNow,
            )
            V07Divider()
            V07Metric(
                "Refresh market prices",
                if (vm.marketRefreshing) "Updating…" else "Refresh",
                "ETFs/stocks use their ticker; CS2 assets use the Steam market hash name.",
                onClick = if (vm.marketRefreshing) null else vm::refreshMarketPrices,
            )
            vm.marketRefreshLabel?.let {
                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Privacy")
            SettingSwitchRow(
                title = "App lock",
                detail = "Use device security when opening Folio.",
                checked = vm.appLockEnabled,
                onCheckedChange = vm::updateAppLockEnabled,
            )

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Updates")
            V07Metric("Version", BuildConfig.VERSION_NAME, BuildConfig.UPDATE_CHANNEL)
            if (BuildConfig.GITHUB_BETA_UPDATES) {
                val updateValue = when (updateState.status) {
                    UpdateStatus.CHECKING -> "Checking…"
                    UpdateStatus.UP_TO_DATE -> "Up to date"
                    UpdateStatus.AVAILABLE -> updateState.release?.tagName ?: "Available"
                    UpdateStatus.DOWNLOADING -> "Downloading…"
                    UpdateStatus.READY -> "Ready"
                    UpdateStatus.ERROR -> "Retry"
                    else -> "Check"
                }
                V07Divider()
                V07Metric(
                    "Beta update",
                    updateValue,
                    updateState.error ?: updateState.release?.notes?.lineSequence()?.firstOrNull(),
                    onClick = {
                        scope.launch {
                            updateState = UpdateUiState(UpdateStatus.CHECKING)
                            updateState = BetaUpdater.check(BuildConfig.VERSION_NAME)
                        }
                    },
                )
                if (updateState.status == UpdateStatus.AVAILABLE && updateState.release != null) {
                    OutlinedButton(
                        onClick = {
                            val release = updateState.release ?: return@OutlinedButton
                            scope.launch {
                                updateState = updateState.copy(status = UpdateStatus.DOWNLOADING, error = null)
                                BetaUpdater.download(context, release).fold(
                                    onSuccess = {
                                        downloadedApk = it
                                        updateState = updateState.copy(status = UpdateStatus.READY)
                                    },
                                    onFailure = {
                                        updateState = updateState.copy(status = UpdateStatus.ERROR, error = it.message)
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download ${updateState.release?.tagName}") }
                }
                if (updateState.status == UpdateStatus.READY && downloadedApk != null) {
                    OutlinedButton(
                        onClick = {
                            when (val result = BetaUpdater.install(context, downloadedApk!!)) {
                                BetaUpdater.InstallResult.Started -> Unit
                                BetaUpdater.InstallResult.PermissionRequested -> Toast.makeText(context, "Allow installs from Folio, then tap Install again.", Toast.LENGTH_LONG).show()
                                is BetaUpdater.InstallResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Install update") }
                }
            }

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Data")
            if (vm.canUndo) {
                V07Metric("Undo last change", "Undo", "Restores the previous local finance snapshot.", onClick = vm::undoLastChange)
                V07Divider()
            }
            TextButton(onClick = vm::clearAll) {
                Text("Clear all local finance data", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
