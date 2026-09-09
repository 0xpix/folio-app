package com.pix.folio.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RecurringWorkScheduler {
    private const val PERIODIC_NAME = "folio-recurring-finance"
    private const val STARTUP_NAME = "folio-recurring-startup"

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext

        // Recurring salary, bills and contributions must not require connectivity.
        // The worker's market refresh is best-effort and recurring finance still runs offline.
        val periodic = PeriodicWorkRequestBuilder<FolioRecurringWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )

        val startup = OneTimeWorkRequestBuilder<FolioRecurringWorker>()
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            STARTUP_NAME,
            ExistingWorkPolicy.REPLACE,
            startup,
        )
    }
}
