package com.pix.folio.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.pix.folio.data.FolioBackup
import com.pix.folio.data.UpdateStatus
import com.pix.folio.data.UpdateUiState
import com.pix.folio.model.AppFontChoice
import com.pix.folio.updates.BetaUpdater
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val V08BackupTimeFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun V08SettingsSheet(vm: V07ViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf(UpdateUiState()) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var backupRefresh by remember { mutableStateOf(0) }
    var scalableImportStatus by remember { mutableStateOf<String?>(null) }
    var scalableImportFailed by remember { mutableStateOf(false) }

    val scalableImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.fold(
                onSuccess = { raw ->
                    vm.importScalableSnapshot(raw).fold(
                        onSuccess = {
                            val snapshot = vm.scalableSnapshot
                            scalableImportFailed = false
                            scalableImportStatus = snapshot?.let {
                                "Imported ${it.holdings.size} holdings · broker total ${v07Euro(it.brokerAccountValue)}"
                            } ?: "Scalable snapshot imported securely"
                            Toast.makeText(context, scalableImportStatus, Toast.LENGTH_LONG).show()
                        },
                        onFailure = {
                            scalableImportFailed = true
                            scalableImportStatus = it.message ?: "Invalid Scalable snapshot"
                            Toast.makeText(
                                context,
                                scalableImportStatus,
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                },
                onFailure = {
                    scalableImportFailed = true
                    scalableImportStatus = it.message ?: "Could not read snapshot"
                    Toast.makeText(context, scalableImportStatus, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "w")!!.bufferedWriter().use { it.write(payload) }
            }.onSuccess {
                FolioBackup.markExported(context)
                backupRefresh += 1
                Toast.makeText(context, "Folio backup saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "Could not save backup", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.fold(
                onSuccess = { raw ->
                    vm.importBackupV08(raw).fold(
                        onSuccess = {
                            backupRefresh += 1
                            Toast.makeText(context, "Folio data restored", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { Toast.makeText(context, it.message ?: "Invalid Folio backup", Toast.LENGTH_LONG).show() },
                    )
                },
                onFailure = { Toast.makeText(context, it.message ?: "Could not read backup", Toast.LENGTH_LONG).show() },
            )
        }
    }

    val lastExportMillis = remember(backupRefresh) { FolioBackup.lastExportMillis(context) }
    val lastBackupLabel = if (lastExportMillis > 0L) {
        Instant.ofEpochMilli(lastExportMillis).atZone(ZoneId.systemDefault()).format(V08BackupTimeFormat)
    } else "Never"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp)
        ) {
            Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text("Private, local controls for Folio.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))
            V07SectionLabel("Typography")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFontChoice.entries.forEach { choice ->
                    OutlinedButton(onClick = { vm.updateFontChoice(choice) }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (vm.fontChoice == choice) "• ${choice.label}" else choice.label)
                    }
                }
            }
            Text("Pixify is the default Folio typeface.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Connections")
            val scalable = vm.scalableSnapshot
            if (scalable == null) {
                V07Metric(
                    "Scalable Capital",
                    "Import",
                    "Read-only snapshot from the official Scalable CLI. Folio never receives your password, 2FA or CLI session.",
                    onClick = {
                        scalableImportLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
            } else {
                val importedAt = scalable.createdAtUtc
                    .atZone(ZoneId.systemDefault())
                    .format(V08BackupTimeFormat)
                V07Metric(
                    "Scalable Capital",
                    "Imported",
                    "${scalable.holdings.size} holdings · ${v07Euro(scalable.brokerAccountValue)} broker total · encrypted locally · $importedAt",
                    onClick = {
                        scalableImportLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
                V07Divider()
                V07Metric(
                    "Replace Scalable snapshot",
                    "Import",
                    "Import a newer read-only snapshot. The previous encrypted snapshot is replaced.",
                    onClick = {
                        scalableImportLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                )
                V07Divider()
                V07Metric(
                    "Disconnect Scalable",
                    "Delete",
                    "Deletes Folio's encrypted broker snapshot and its Keystore key. Your Scalable CLI session is untouched.",
                    onClick = vm::disconnectScalable,
                )
            }
            Text(
                "Create the snapshot on your own computer with Scalable's official CLI in local read-only mode. Snapshot files are excluded from Git and are never included in Folio backups.",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            scalableImportStatus?.let { status ->
                Text(
                    status,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = if (scalableImportFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Recurring")
            V08SettingSwitchRow(
                title = "Automatic recurring",
                detail = "Post due income, bills, savings and investments when cash is available.",
                checked = vm.autoRecurringEnabled,
                onCheckedChange = vm::updateAutoRecurringEnabled,
            )
            V07Divider()
            V07Metric("Run recurring now", "Run", "Apply anything due today.", onClick = vm::runRecurringNow)
            V07Divider()
            V07Metric(
                "Refresh market prices",
                if (vm.marketRefreshing) "Updating…" else "Refresh",
                "Update tracked market values.",
                onClick = if (vm.marketRefreshing) null else vm::refreshMarketPrices,
            )

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Privacy")
            V08SettingSwitchRow(
                title = "App lock",
                detail = "Use device security when opening Folio.",
                checked = vm.appLockEnabled,
                onCheckedChange = vm::updateAppLockEnabled,
            )

            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Backup & restore")
            V07Metric("Last backup", lastBackupLabel)
            V07Divider()
            V07Metric(
                "Export Folio",
                "Export",
                "Save a local copy of your Folio data.",
                onClick = {
                    pendingExport = vm.exportBackupV08()
                    exportLauncher.launch("folio-${LocalDate.now()}.folio.json")
                },
            )
            V07Divider()
            V07Metric(
                "Restore Folio",
                "Import",
                "Restore from a Folio backup file.",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
            Spacer(Modifier.height(26.dp))
            V07SectionLabel("Updates")
            V07Metric("Installed version", BuildConfig.VERSION_NAME, BuildConfig.UPDATE_CHANNEL)
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
                val updateDetail = when {
                    updateState.error != null -> updateState.error
                    updateState.status == UpdateStatus.AVAILABLE -> "A new beta is ready. What's new appears below."
                    updateState.status == UpdateStatus.READY -> "Download complete. Install when ready."
                    else -> null
                }

                V07Divider()
                V07Metric(
                    "Beta update",
                    updateValue,
                    updateDetail,
                    onClick = {
                        scope.launch {
                            updateState = UpdateUiState(UpdateStatus.CHECKING)
                            downloadedApk = null
                            updateState = BetaUpdater.check(BuildConfig.VERSION_NAME)
                        }
                    },
                )

                val release = updateState.release
                if (release != null && updateState.status in setOf(UpdateStatus.AVAILABLE, UpdateStatus.DOWNLOADING, UpdateStatus.READY)) {
                    Spacer(Modifier.height(14.dp))
                    V071UpdateNotes(release.notes)
                    TextButton(onClick = { BetaUpdater.openRelease(context, release) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open full GitHub release")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (updateState.status == UpdateStatus.AVAILABLE && release != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                updateState = updateState.copy(status = UpdateStatus.DOWNLOADING, error = null)
                                BetaUpdater.download(context, release).fold(
                                    onSuccess = {
                                        downloadedApk = it
                                        updateState = updateState.copy(status = UpdateStatus.READY)
                                    },
                                    onFailure = { updateState = updateState.copy(status = UpdateStatus.ERROR, error = it.message) },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download ${release.tagName}") }
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
            TextButton(onClick = vm::clearAll) { Text("Clear all local finance data", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun V08SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
