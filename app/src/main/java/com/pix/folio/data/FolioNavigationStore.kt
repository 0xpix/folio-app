package com.pix.folio.data

import android.content.Context

/** Small UI-state store for navigation that should survive app locking and process recreation. */
class FolioNavigationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastRootPage(): String? = prefs.getString(KEY_ROOT_PAGE, null)

    fun setLastRootPage(page: String) {
        prefs.edit().putString(KEY_ROOT_PAGE, page).apply()
    }

    private companion object {
        const val PREFS_NAME = "folio_navigation_v1"
        const val KEY_ROOT_PAGE = "root_page"
    }
}
