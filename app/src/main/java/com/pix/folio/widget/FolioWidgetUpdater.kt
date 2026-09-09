package com.pix.folio.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widget in sync after local finance changes.
 *
 * Glance updates can occasionally be coalesced while several finance writes happen back-to-back,
 * so Folio issues a second lightweight refresh after the first one settles.
 */
object FolioWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun request(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            FolioBalanceWidget().updateAll(appContext)
            delay(180)
            FolioBalanceWidget().updateAll(appContext)
        }
    }
}
