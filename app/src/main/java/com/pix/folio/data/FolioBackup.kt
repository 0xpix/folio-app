package com.pix.folio.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Portable, local-only Folio backup. No account or cloud is required.
 *
 * The backup intentionally captures only finance-owned preference files so beta updater
 * state and other app/runtime preferences do not leak into user data exports.
 */
object FolioBackup {
    const val FORMAT_VERSION = 1
    private const val META_PREFS = "folio_backup_meta_v1"
    private val financePreferenceFiles = listOf(
        "folio_store_v2",
        "folio_investment_tracking_v1",
        "folio_monthly_plan_v1",
    )

    fun export(context: Context): String {
        val now = System.currentTimeMillis()
        val files = JSONObject()
        financePreferenceFiles.forEach { name ->
            files.put(name, encodePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        }
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit().putLong("last_export_millis", now).apply()
        return JSONObject()
            .put("format", "folio-backup")
            .put("version", FORMAT_VERSION)
            .put("createdAt", Instant.ofEpochMilli(now).toString())
            .put("files", files)
            .toString(2)
    }

    fun import(context: Context, raw: String): Result<Unit> = runCatching {
        val root = JSONObject(raw)
        require(root.optString("format") == "folio-backup") { "Not a Folio backup" }
        val version = root.optInt("version", 0)
        require(version in 1..FORMAT_VERSION) { "Unsupported Folio backup version $version" }
        val files = root.getJSONObject("files")
        financePreferenceFiles.forEach { name ->
            val encoded = files.optJSONObject(name) ?: JSONObject()
            restorePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE), encoded)
        }
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit().putLong("last_restore_millis", System.currentTimeMillis()).apply()
    }

    fun validate(raw: String): Result<String> = runCatching {
        val root = JSONObject(raw)
        require(root.optString("format") == "folio-backup") { "Not a Folio backup" }
        val version = root.optInt("version", 0)
        require(version in 1..FORMAT_VERSION) { "Unsupported Folio backup version $version" }
        require(root.optJSONObject("files") != null) { "Backup data is incomplete" }
        root.optString("createdAt").ifBlank { "unknown date" }
    }

    fun lastExportMillis(context: Context): Long =
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).getLong("last_export_millis", 0L)

    fun lastRestoreMillis(context: Context): Long =
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).getLong("last_restore_millis", 0L)

    private fun encodePreferences(prefs: SharedPreferences): JSONObject {
        val result = JSONObject()
        prefs.all.toSortedMap().forEach { (key, value) ->
            val encoded = JSONObject()
            when (value) {
                is String -> encoded.put("type", "string").put("value", value)
                is Boolean -> encoded.put("type", "boolean").put("value", value)
                is Int -> encoded.put("type", "int").put("value", value)
                is Long -> encoded.put("type", "long").put("value", value)
                is Float -> encoded.put("type", "float").put("value", value.toDouble())
                is Set<*> -> {
                    val array = JSONArray()
                    value.filterIsInstance<String>().sorted().forEach(array::put)
                    encoded.put("type", "string_set").put("value", array)
                }
                else -> return@forEach
            }
            result.put(key, encoded)
        }
        return result
    }

    private fun restorePreferences(prefs: SharedPreferences, encoded: JSONObject) {
        val editor = prefs.edit().clear()
        val keys = encoded.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = encoded.optJSONObject(key) ?: continue
            when (value.optString("type")) {
                "string" -> editor.putString(key, value.optString("value"))
                "boolean" -> editor.putBoolean(key, value.optBoolean("value"))
                "int" -> editor.putInt(key, value.optInt("value"))
                "long" -> editor.putLong(key, value.optLong("value"))
                "float" -> editor.putFloat(key, value.optDouble("value").toFloat())
                "string_set" -> {
                    val array = value.optJSONArray("value") ?: JSONArray()
                    editor.putStringSet(key, buildSet {
                        for (index in 0 until array.length()) add(array.optString(index))
                    })
                }
            }
        }
        check(editor.commit()) { "Could not restore local Folio data" }
    }
}
