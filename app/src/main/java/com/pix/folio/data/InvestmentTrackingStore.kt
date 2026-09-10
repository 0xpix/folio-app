package com.pix.folio.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Purchase metadata kept independently from portfolio accounting while the beta data model
 * migrates. Date/time and an optional broker-reported owned-unit override are portable because
 * this preference file is included in Folio backups.
 */
class InvestmentTrackingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun purchaseDateTime(holdingId: String): LocalDateTime? {
        prefs.getString(key(KEY_PURCHASE_DATETIME, holdingId), null)?.let { raw ->
            runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { return it }
        }
        return prefs.getString(key(KEY_PURCHASE_DATE, holdingId), null)?.let { raw ->
            runCatching { LocalDate.parse(raw).atTime(LocalTime.NOON) }.getOrNull()
        }
    }

    fun purchaseDate(holdingId: String): LocalDate? = purchaseDateTime(holdingId)?.toLocalDate()

    fun setPurchaseDateTime(holdingId: String, dateTime: LocalDateTime) {
        prefs.edit()
            .putString(key(KEY_PURCHASE_DATETIME, holdingId), dateTime.toString())
            .putString(key(KEY_PURCHASE_DATE, holdingId), dateTime.toLocalDate().toString())
            .apply()
    }

    /** Date-only editor compatibility. New purchases inherit the current clock time. */
    fun setPurchaseDate(holdingId: String, date: LocalDate) {
        val time = purchaseDateTime(holdingId)?.toLocalTime() ?: LocalTime.now().withSecond(0).withNano(0)
        setPurchaseDateTime(holdingId, date.atTime(time))
    }

    /**
     * Optional current total units copied from the brokerage. This is used only when positive and
     * lets Folio calculate an exact current value for EUR-denominated quotes.
     */
    fun ownedUnits(holdingId: String): Double? =
        prefs.getString(key(KEY_OWNED_UNITS, holdingId), null)
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 && it.isFinite() }

    fun setOwnedUnits(holdingId: String, units: Double?) {
        val editor = prefs.edit()
        if (units != null && units > 0.0 && units.isFinite()) {
            editor.putString(key(KEY_OWNED_UNITS, holdingId), units.toString())
        } else {
            editor.remove(key(KEY_OWNED_UNITS, holdingId))
        }
        editor.apply()
    }

    fun clearPurchaseDate(holdingId: String) {
        prefs.edit()
            .remove(key(KEY_PURCHASE_DATE, holdingId))
            .remove(key(KEY_PURCHASE_DATETIME, holdingId))
            .remove(key(KEY_OWNED_UNITS, holdingId))
            .apply()
    }

    private fun key(prefix: String, holdingId: String): String = "${prefix}_$holdingId"

    private companion object {
        const val PREFS_NAME = "folio_investment_tracking_v1"
        const val KEY_PURCHASE_DATE = "purchase_date"
        const val KEY_PURCHASE_DATETIME = "purchase_datetime"
        const val KEY_OWNED_UNITS = "owned_units"
    }
}
