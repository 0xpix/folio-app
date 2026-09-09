package com.pix.folio.data

import android.content.Context
import java.time.LocalDate

/**
 * Small companion store for purchase metadata that is independent from portfolio accounting.
 * This keeps the original transaction model backward-compatible while allowing Folio to
 * reconstruct a holding's value curve from the date the user actually bought it.
 */
class InvestmentTrackingStore(context: Context) {
    private val prefs = context.getSharedPreferences("folio_investment_tracking_v1", Context.MODE_PRIVATE)

    fun purchaseDate(holdingId: String): LocalDate? =
        prefs.getString("purchase_date_$holdingId", null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun setPurchaseDate(holdingId: String, date: LocalDate) {
        prefs.edit().putString("purchase_date_$holdingId", date.toString()).apply()
    }

    fun clearPurchaseDate(holdingId: String) {
        prefs.edit().remove("purchase_date_$holdingId").apply()
    }
}
