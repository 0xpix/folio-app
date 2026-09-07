package com.pix.folio.updates

import android.content.Context
import com.pix.folio.data.BetaRelease
import com.pix.folio.data.UpdateStatus
import com.pix.folio.data.UpdateUiState
import java.io.File

object BetaUpdater {
    sealed interface InstallResult {
        data object Started : InstallResult
        data object PermissionRequested : InstallResult
        data class Error(val message: String) : InstallResult
    }

    suspend fun check(currentVersion: String = ""): UpdateUiState =
        UpdateUiState(UpdateStatus.ERROR, error = "GitHub beta updates are disabled in the Play build.")

    suspend fun download(context: Context, release: BetaRelease): Result<File> =
        Result.failure(IllegalStateException("GitHub beta updates are disabled in the Play build."))

    fun install(context: Context, apk: File): InstallResult =
        InstallResult.Error("GitHub beta updates are disabled in the Play build.")

    fun openRelease(context: Context, release: BetaRelease) = Unit
}
