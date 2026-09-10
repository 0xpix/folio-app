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

    /** Build a user-initiated portable export. Call [markExported] only after the file was written. */
    fun export(context: Context): String = buildPayload(context, System.currentTimeMillis())

    /** Records a successful user-visible export, never an attempted/cancelled export. */
    fun markExported(context: Context) {
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_export_millis", System.currentTimeMillis())
            .apply()
    }

    /** Internal safety snapshot used by the Room migration bridge; does not pretend to be an exported file. */
    internal fun snapshot(context: Context): String = buildPayload(context, System.currentTimeMillis())

    private fun buildPayload(context: Context, createdAtMillis: Long): String {
        val files = JSONObject()
        financePreferenceFiles.forEach { name ->
            files.put(name, encodePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        }
        return JSONObject()
            .put("format", "folio-backup")
            .put("version", FORMAT_VERSION)
            .put("createdAt", Instant.ofEpochMilli(createdAtMillis).toString())
            .put("files", files)
            .toString(2)
    }

    fun import(context: Context, raw: String): Result<Unit> = runCatching {
        val incomingFiles = parseAndValidate(raw).second
        val previousFiles = parseAndValidate(snapshot(context)).second

        try {
            restoreFiles(context, incomingFiles)
        } catch (restoreError: Throwable) {
            runCatching { restoreFiles(context, previousFiles) }
            throw restoreError
        }

        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_restore_millis", System.currentTimeMillis())
            .apply()
    }

    fun validate(raw: String): Result<String> = runCatching {
        val (root, _) = parseAndValidate(raw)
        root.optString("createdAt").ifBlank { "unknown date" }
    }

    fun lastExportMillis(context: Context): Long =
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).getLong("last_export_millis", 0L)

    fun lastRestoreMillis(context: Context): Long =
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).getLong("last_restore_millis", 0L)

    private fun parseAndValidate(raw: String): Pair<JSONObject, JSONObject> {
        val root = JSONObject(raw)
        require(root.optString("format") == "folio-backup") { "Not a Folio backup" }
        val version = root.optInt("version", 0)
        require(version in 1..FORMAT_VERSION) { "Unsupported Folio backup version $version" }
        val files = root.optJSONObject("files") ?: error("Backup data is incomplete")
        financePreferenceFiles.forEach { name ->
            require(files.optJSONObject(name) != null) { "Backup data is incomplete: $name" }
        }
        validateEncodedFiles(files)
        return root to files
    }

    private fun validateEncodedFiles(files: JSONObject) {
        val supportedTypes = setOf("string", "boolean", "int", "long", "float", "string_set")
        financePreferenceFiles.forEach { name ->
            val encoded = files.getJSONObject(name)
            val keys = encoded.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = encoded.optJSONObject(key) ?: error("Invalid backup entry: $name/$key")
                val type = value.optString("type")
                require(type in supportedTypes) { "Unsupported backup value type: $type" }
                require(value.has("value")) { "Invalid backup entry: $name/$key" }
                if (type == "string_set") {
                    require(value.optJSONArray("value") != null) { "Invalid string set: $name/$key" }
                }
            }
        }
    }

    private fun restoreFiles(context: Context, files: JSONObject) {
        financePreferenceFiles.forEach { name ->
            restorePreferences(
                context.getSharedPreferences(name, Context.MODE_PRIVATE),
                files.getJSONObject(name),
            )
        }
    }

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
            val value = encoded.getJSONObject(key)
            when (value.getString("type")) {
                "string" -> editor.putString(key, value.getString("value"))
                "boolean" -> editor.putBoolean(key, value.getBoolean("value"))
                "int" -> editor.putInt(key, value.getInt("value"))
                "long" -> editor.putLong(key, value.getLong("value"))
                "float" -> editor.putFloat(key, value.getDouble("value").toFloat())
                "string_set" -> {
                    val array = value.getJSONArray("value")
                    editor.putStringSet(key, buildSet {
                        for (index in 0 until array.length()) add(array.getString(index))
                    })
                }
            }
        }
        check(editor.commit()) { "Could not restore local Folio data" }
    }
}
