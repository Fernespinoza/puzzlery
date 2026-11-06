package io.github.nandomango702.stpuzzlerelay.backup

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User-facing backup: both SharedPreferences stores serialized to one
 * JSON file (format spec: backup-export-spec.md in the project docs).
 *
 * Export is a wholesale dump — every key in both stores, each value
 * tagged with its exact SharedPreferences type — so nothing can be
 * silently left behind, and keys added by future versions are
 * covered automatically. The schema_version is for the day a key's
 * TYPE or the file format itself changes: parseImport type-checks
 * the keys it knows about (see expectedTag), because a value stored
 * under the wrong type makes every later typed read of that key
 * throw, which would crash-loop the app on ordinary screens.
 */
object Backup {
    const val SCHEMA_VERSION = 1
    const val APP_TAG = "Puzzlery"

    /** Import reads are capped: a real backup is tens of KB. */
    const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

    private const val STORE_DEFAULT = "default"
    private const val STORE_STATE = "state"

    // Type tags (single letters keep the file small and greppable).
    private const val T_STRING = "s"
    private const val T_BOOLEAN = "b"
    private const val T_INT = "i"
    private const val T_LONG = "l"
    private const val T_FLOAT = "f"
    private const val T_STRING_SET = "ss"

    /** The whole backup as one JSON object, ready to write to a file. */
    fun buildExport(context: Context): JSONObject {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val stores = JSONObject()
        stores.put(STORE_DEFAULT, encodeStore(PreferenceManager.getDefaultSharedPreferences(context)))
        stores.put(
            STORE_STATE,
            encodeStore(context.getSharedPreferences(PrefsConstants.STATE_PREFS_NAME, Context.MODE_PRIVATE)),
        )
        // longVersionCode is API 28+ and the XXX zone pattern is
        // API 24+; minSdk is 21, so use the compat paths (the plain
        // Z pattern renders the same offset without the colon).
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        return JSONObject()
            .put("app", APP_TAG)
            .put("schema_version", SCHEMA_VERSION)
            .put("app_version", info.versionName ?: "")
            .put("version_code", versionCode)
            .put(
                "exported_at",
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
            )
            .put("stores", stores)
    }

    /** Suggested name in the save picker: puzzlery-backup-2026-10-30.json */
    fun suggestedFileName(): String =
        "puzzlery-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

    /** A fully validated backup, decoded to native values, ready to apply. */
    class ParsedBackup(
        val schemaVersion: Int,
        /** From the file's metadata, for the confirm prompt ("" when absent). */
        val exportedAt: String,
        val appVersion: String,
        /** Store name → (key → String/Boolean/Int/Long/Float/Set<String>). */
        val stores: Map<String, Map<String, Any>>,
        /** Entries the type filter dropped — the confirm prompt reports them. */
        val dropped: Int,
    )

    /** Why a candidate import file was rejected. */
    enum class ImportFailure { NOT_JSON, WRONG_APP, SCHEMA_TOO_NEW, MALFORMED }

    sealed interface ImportParse {
        class Success(val backup: ParsedBackup) : ImportParse
        class Failure(val reason: ImportFailure) : ImportParse
    }

    /**
     * Parses and validates a backup file's text WITHOUT modifying
     * any app state (the current stores are read only to type-check
     * entries against what this device already holds). Only a
     * [ImportParse.Success] may proceed to apply — that is what makes
     * a corrupt or foreign file harmless. Entries whose type is
     * wrong for their key are dropped, not fatal: one bad key must
     * not block restoring everything else, and must never reach a
     * typed getter.
     */
    fun parseImport(context: Context, text: String): ImportParse {
        val root = try {
            JSONObject(text)
        } catch (e: Throwable) {
            // Throwable, not Exception: a hostile file can also fail
            // with an Error (stack overflow from deep nesting, OOM
            // from sheer size) and validation is the boundary where
            // those must become a clean rejection.
            return ImportParse.Failure(ImportFailure.NOT_JSON)
        }
        if (root.optString("app") != APP_TAG) {
            return ImportParse.Failure(ImportFailure.WRONG_APP)
        }
        if (!root.has("schema_version")) {
            return ImportParse.Failure(ImportFailure.MALFORMED)
        }
        val schema = root.optInt("schema_version", -1)
        if (schema > SCHEMA_VERSION) {
            return ImportParse.Failure(ImportFailure.SCHEMA_TOO_NEW)
        }
        if (schema < 1) {
            return ImportParse.Failure(ImportFailure.MALFORMED)
        }
        val storesJson = root.optJSONObject("stores")
            ?: return ImportParse.Failure(ImportFailure.MALFORMED)
        val current = mapOf(
            STORE_DEFAULT to PreferenceManager.getDefaultSharedPreferences(context).all,
            STORE_STATE to context.getSharedPreferences(
                PrefsConstants.STATE_PREFS_NAME, Context.MODE_PRIVATE,
            ).all,
        )
        val stores = mutableMapOf<String, Map<String, Any>>()
        for (storeName in listOf(STORE_DEFAULT, STORE_STATE)) {
            val storeJson = storesJson.optJSONObject(storeName)
                ?: return ImportParse.Failure(ImportFailure.MALFORMED)
            val decoded = mutableMapOf<String, Any>()
            val keys = storeJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = decodeEntry(storeJson.optJSONObject(key))
                    ?: return ImportParse.Failure(ImportFailure.MALFORMED)
                decoded[key] = value
            }
            stores[storeName] = decoded
        }
        // A structurally valid file with no entries at all would
        // wipe the device clean for nothing; it is a damaged (or
        // doctored) backup, not a real one.
        if (stores.values.all { it.isEmpty() }) {
            return ImportParse.Failure(ImportFailure.MALFORMED)
        }
        var dropped = 0
        for (storeName in listOf(STORE_DEFAULT, STORE_STATE)) {
            stores[storeName] = stores.getValue(storeName).filter { (key, value) ->
                val tag = tagOf(value)
                val expected = expectedTag(storeName, key)
                val keep = if (expected != null) {
                    tag == expected
                } else {
                    // Unknown key: the one type fact available is the
                    // value this device already holds under it.
                    val existing = current.getValue(storeName)[key]
                    existing == null || tagOf(existing) == tag
                }
                if (!keep) {
                    dropped++
                    Log.w("Backup", "dropping wrong-typed entry $storeName/$key")
                }
                keep
            }
        }
        // The emptiness check must hold AFTER filtering too (Claude
        // review round 2): a file whose every entry is wrong-typed
        // passes the check above, filters down to nothing, and
        // would wipe the device exactly like an empty file.
        if (stores.values.all { it.isEmpty() }) {
            return ImportParse.Failure(ImportFailure.MALFORMED)
        }
        return ImportParse.Success(
            ParsedBackup(
                schema,
                root.optString("exported_at"),
                root.optString("app_version"),
                stores,
                dropped,
            )
        )
    }

    /**
     * Replaces BOTH stores wholesale with a validated backup's
     * contents: clear, write every entry back with its original
     * type, commit synchronously. Both stores are snapshotted first;
     * if either write fails, both are rolled back to the snapshot
     * and false is returned — a half-imported state is worse than
     * none. Callers should restart the app afterwards so no screen
     * keeps pre-import state in memory.
     */
    fun applyImport(context: Context, backup: ParsedBackup): Boolean {
        val targets = backup.stores.keys.map { storeName ->
            storeName to prefsFor(context, storeName)
        }
        val before = targets.associate { (name, prefs) -> name to snapshot(prefs) }
        var ok = true
        try {
            for ((storeName, prefs) in targets) {
                if (!writeStore(prefs, backup.stores.getValue(storeName))) ok = false
            }
        } catch (e: Exception) {
            ok = false
        }
        if (!ok) {
            for ((storeName, prefs) in targets) {
                try {
                    if (!writeStore(prefs, before.getValue(storeName))) {
                        Log.e("Backup", "rollback commit failed for $storeName")
                    }
                } catch (e: Exception) {
                    Log.e("Backup", "rollback failed for $storeName", e)
                }
            }
        }
        return ok
    }

    private fun prefsFor(context: Context, storeName: String): SharedPreferences =
        when (storeName) {
            STORE_STATE -> context.getSharedPreferences(
                PrefsConstants.STATE_PREFS_NAME, Context.MODE_PRIVATE,
            )
            else -> PreferenceManager.getDefaultSharedPreferences(context)
        }

    /** Deep-enough copy of a store's contents (sets are copied too). */
    private fun snapshot(prefs: SharedPreferences): Map<String, Any> =
        prefs.all.mapValues { (_, v) -> if (v is Set<*>) v.toSet() else v as Any }

    /** Clears [prefs] and writes [entries] with their runtime types. */
    private fun writeStore(prefs: SharedPreferences, entries: Map<String, Any>): Boolean {
        val editor = prefs.edit()
        editor.clear()
        for ((key, value) in entries) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        return editor.commit()
    }

    /** The type tag a decoded (or stored) value carries. */
    private fun tagOf(value: Any): String = when (value) {
        is String -> T_STRING
        is Boolean -> T_BOOLEAN
        is Int -> T_INT
        is Long -> T_LONG
        is Float -> T_FLOAT
        is Set<*> -> T_STRING_SET
        else -> "?"
    }

    /**
     * The type a key MUST hold, for the keys whose type is fixed by
     * the code that reads them — or null when the key is unknown to
     * this table. Keeping this in step with the readers is what
     * stands between a doctored (or cross-version) backup and a
     * ClassCastException crash loop.
     */
    private fun expectedTag(store: String, key: String): String? = when (store) {
        STORE_DEFAULT -> when {
            key == PrefsConstants.SHOW_SOLVE_TIMER_KEY ||
                key == PrefsConstants.SHOW_BEST_TIME_KEY ||
                key == PrefsConstants.SHOW_COMPLETION_BADGES_KEY ||
                key == PrefsConstants.CHECK_FOR_UPDATES_KEY ||
                key == PrefsConstants.COMPLETED_PROMPT_KEY ||
                key == PrefsConstants.VICTORY_FLASH_KEY ||
                key == PrefsConstants.CONTROLS_REMINDERS_KEY ||
                key == PrefsConstants.AUTO_ORIENT ||
                key == PrefsConstants.AMOLED_BLACK_KEY -> T_BOOLEAN
            key == PrefsConstants.CHOOSER_STYLE_KEY ||
                key == PrefsConstants.NIGHT_MODE_KEY ||
                key == "updateDismissedVersion" ||
                key == "daily_rotation_v1" ||
                key == "playlists_v1" -> T_STRING
            key == "updateCheckLastAt" -> T_LONG
            key.startsWith(PrefsConstants.STARRED_PREFIX) ||
                key.startsWith(PrefsConstants.SWAP_L_R_PREFIX) -> T_BOOLEAN
            key.startsWith(PrefsConstants.DAILY_COMPLETE_PREFIX) -> T_LONG
            key.startsWith("daily_attempt_save_") -> T_STRING
            key.startsWith("daily_attempt_timer_") -> T_LONG
            // started / usedsolve / usedrestart / day_attempted flags.
            key.startsWith("daily_attempt_") ||
                key.startsWith("daily_day_attempted_") -> T_BOOLEAN
            else -> null
        }
        STORE_STATE -> when {
            key == PrefsConstants.SAVED_BACKEND -> T_STRING
            key == PrefsConstants.PUZZLESGEN_CLEANUP_DONE -> T_BOOLEAN
            // The toastFirstFewTimes counters and the night-mode
            // seen markers are all Longs (state.getLong).
            key == PrefsConstants.SEEN_NIGHT_MODE ||
                key == PrefsConstants.SEEN_NIGHT_MODE_SETTING ||
                key == PrefsConstants.UNDO_NEW_GAME_SEEN ||
                key == PrefsConstants.REDO_NEW_GAME_SEEN ||
                key == PrefsConstants.LATIN_M_UNDO_SEEN -> T_LONG
            key.startsWith(PrefsConstants.BEST_TIME_PREFIX) ||
                key.startsWith(PrefsConstants.SOLVE_COUNT_PREFIX) ||
                key.startsWith(PrefsConstants.TOTAL_TIME_PREFIX) ||
                key.startsWith(PrefsConstants.SAVED_TIMER_PREFIX) -> T_LONG
            key.startsWith(PrefsConstants.SAVED_GAME_PREFIX) ||
                key.startsWith(PrefsConstants.LAST_PARAMS_PREFIX) -> T_STRING
            key.startsWith(PrefsConstants.SAVED_COMPLETED_PREFIX) ||
                key.startsWith(PrefsConstants.SAVED_USED_SOLVE_PREFIX) ||
                key.startsWith(PrefsConstants.SAVED_USED_RESTART_PREFIX) -> T_BOOLEAN
            else -> null
        }
        else -> null
    }

    /** One {t, v} entry → its native value, or null if it is malformed in any way. */
    private fun decodeEntry(entry: JSONObject?): Any? {
        if (entry == null || !entry.has("v")) return null
        val raw = entry.get("v")
        if (raw === JSONObject.NULL) return null
        return when (entry.optString("t")) {
            T_STRING -> raw as? String
            T_BOOLEAN -> raw as? Boolean
            T_INT -> (raw as? Number)?.let { n ->
                val l = n.toLong()
                if (l in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && isIntegral(n)) l.toInt() else null
            }
            T_LONG -> (raw as? Number)?.let { n -> if (isIntegral(n)) n.toLong() else null }
            T_FLOAT -> (raw as? Number)?.toFloat()
            T_STRING_SET -> (raw as? JSONArray)?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.get(i)
                    if (item !is String) return null
                    set.add(item)
                }
                set as Any
            }
            else -> null
        }
    }

    /** True when a parsed JSON number has no fractional part. */
    private fun isIntegral(n: Number): Boolean = when (n) {
        is Double, is Float -> n.toDouble() == n.toLong().toDouble()
        else -> true
    }

    private fun encodeStore(prefs: SharedPreferences): JSONObject {
        val out = JSONObject()
        // One snapshot of the store: prefs.all copies the whole map,
        // so asking for it per key would be O(n) copies — and the
        // entries could disagree with each other mid-export.
        val all = prefs.all
        // Sorted keys: deterministic output, so two exports of the
        // same state diff cleanly.
        for (key in all.keys.sorted()) {
            val entry = JSONObject()
            when (val value = all[key]) {
                is String -> entry.put("t", T_STRING).put("v", value)
                is Boolean -> entry.put("t", T_BOOLEAN).put("v", value)
                is Int -> entry.put("t", T_INT).put("v", value)
                is Long -> entry.put("t", T_LONG).put("v", value)
                is Float -> {
                    // JSONObject's double put throws on NaN/Infinity;
                    // a non-finite float has no JSON representation,
                    // so it is skipped rather than failing the export.
                    if (!value.isFinite()) continue
                    entry.put("t", T_FLOAT).put("v", value.toDouble())
                }
                is Set<*> -> {
                    val arr = JSONArray()
                    @Suppress("UNCHECKED_CAST")
                    for (item in (value as Set<String>).sorted()) arr.put(item)
                    entry.put("t", T_STRING_SET).put("v", arr)
                }
                // SharedPreferences holds only the types above; anything
                // else (including null) is skipped rather than guessed at.
                else -> continue
            }
            out.put(key, entry)
        }
        return out
    }
}
