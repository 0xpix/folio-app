package com.pix.folio.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pix.folio.data.MarketPriceService
import com.pix.folio.data.RecurringMoneyProcessor
import com.pix.folio.widget.FolioWidgetUpdater

class FolioRecurringWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            // Market data is optional. A network/provider failure must never block recurring money.
            runCatching { MarketPriceService.refreshAll(applicationContext) }
            RecurringMoneyProcessor.process(applicationContext)
            FolioWidgetUpdater.request(applicationContext)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
