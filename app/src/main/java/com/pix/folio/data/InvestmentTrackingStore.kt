package com.pix.folio.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Purchase metadata kept independently from portfolio accounting while the beta data model
 * migrates. v0.8 stores both date and time, while transparently reading the legacy date-only key.
 */
class InvestmentTrackingStore(context: Context) {
    private val prefs = context.getSharedPreferences("folio_investment_tracking_v1", Context.MODE_PRIVATE)

    fun purchaseDateTime(holdingId: String): LocalDateTime? {
        prefs.getString("purchase_datetime_$holdingId", null)?.let { raw ->
            runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { return it }
        }
        return prefs.getString("purchase_date_$holdingId", null)?.let { raw ->
            runCatching { LocalDate.parse(raw).atTime(LocalTime.NOON) }.getOrNull()
        }
    }

    fun purchaseDate(holdingId: String): LocalDate? = purchaseDateTime(holdingId)?.toLocalDate()

    fun setPurchaseDateTime(holdingId: String, dateTime: LocalDateTime) {
        prefs.edit()
            .putString("purchase_datetime_$holdingId", dateTime.toString())
            .putString("purchase_date_$holdingId", dateTime.toLocalDate().toString())
            .apply()
    }

    /** Date-only editor compatibility. Preserve an existing clock time when possible. */
    fun setPurchaseDate(holdingId: String, date: LocalDate) {
        val time = purchaseDateTime(holdingId)?.toLocalTime() ?: LocalTime.NOON
        setPurchaseDateTime(holdingId, date.atTime(time))
    }

    fun clearPurchaseDate(holdingId: String) {
        prefs.edit()
            .remove("purchase_date_$holdingId")
            .remove("purchase_datetime_$holdingId")
            .apply()
    }
}
