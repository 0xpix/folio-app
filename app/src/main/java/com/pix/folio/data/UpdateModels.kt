package com.pix.folio.data

import java.time.Instant

enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

data class BetaRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val publishedAt: Instant?,
    val htmlUrl: String,
    val apkName: String?,
    val apkUrl: String?,
    val checksumUrl: String?,
)

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val release: BetaRelease? = null,
    val error: String? = null,
    val checkedAtMillis: Long? = null,
)
