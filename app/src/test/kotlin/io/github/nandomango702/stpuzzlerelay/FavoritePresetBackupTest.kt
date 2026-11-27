package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backup.Backup
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.BEST_TIME_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.FAVORITE_PRESET_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.STATE_PREFS_NAME
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Favorite presets (v0.7.0): the new tracked state must ride the
 * backup like everything else. These tests pin the three properties
 * the feature's safety rests on:
 *
 *  - a favorite is keyed exactly like its preset's best time (same
 *    backend + normalised params identity, different prefix only),
 *    so favorites, bests and Stats can never disagree about a preset;
 *  - a favorite survives an export → import round trip as a Boolean;
 *  - a wrong-typed favorite entry in a doctored backup is dropped by
 *    the type filter (never reaches a typed getter), without failing
 *    the rest of the import.
 *
 * The identical round-trip assertions also run in this environment
 * as a standalone harness against the real Backup.kt (JUnit and
 * Mockito are not resolvable in the offline build sandbox), the
 * same split the pinned-clues tranche used.
 */
class FavoritePresetBackupTest {

    /** In-memory SharedPreferences: the whole interface, map-backed. */
    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): Map<String, Any> = map.toMap()
        override fun getString(key: String, defValue: String?): String? =
            map[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            (map[key] as? Set<String>) ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            (map[key] as? Number)?.toInt() ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            (map[key] as? Number)?.toLong() ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            (map[key] as? Number)?.toFloat() ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var cleared = false

            override fun putString(key: String, value: String?) = apply {
                pending[key] = value
            }

            override fun putStringSet(key: String, values: Set<String>?) = apply {
                pending[key] = values?.toSet()
            }

            override fun putInt(key: String, value: Int) = apply {
                pending[key] = value
            }

            override fun putLong(key: String, value: Long) = apply {
                pending[key] = value
            }

            override fun putFloat(key: String, value: Float) = apply {
                pending[key] = value
            }

            override fun putBoolean(key: String, value: Boolean) = apply {
                pending[key] = value
            }

            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { cleared = true }

            override fun commit(): Boolean {
                if (cleared) map.clear()
                for ((key, value) in pending) {
                    if (value == null) map.remove(key) else map[key] = value
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }

    /**
     * A Context whose two stores are the given fakes. The default
     * store reaches Backup through androidx PreferenceManager, which
     * asks the context for "<package>_preferences" — the argThat
     * stub routes every non-state name to the default fake.
     */
    private fun contextWith(
        defaultPrefs: FakeSharedPreferences,
        statePrefs: FakeSharedPreferences,
    ): Context {
        val context = mock(Context::class.java)
        `when`(context.packageName)
            .thenReturn("io.github.nandomango702.stpuzzlerelay")
        val pm = mock(PackageManager::class.java)
        `when`(pm.getPackageInfo(anyString(), anyInt()))
            .thenReturn(mock(PackageInfo::class.java))
        `when`(context.packageManager).thenReturn(pm)
        `when`(context.getSharedPreferences(eq(STATE_PREFS_NAME), anyInt()))
            .thenReturn(statePrefs)
        `when`(
            context.getSharedPreferences(
                argThat { name -> name != STATE_PREFS_NAME }, anyInt(),
            )
        ).thenReturn(defaultPrefs)
        return context
    }

    @Test
    fun favoriteKeyMirrorsBestTimeKey() {
        for (params in listOf("3x3", "4x3", "2x3", "6x6c", "10x10d")) {
            val favorite = GamePlay.favoriteKeyFor(BackendName.SOLO, params)
            val best = GamePlay.bestTimeKeyFor(BackendName.SOLO, params)
            assertEquals(
                best.removePrefix(BEST_TIME_PREFIX),
                favorite.removePrefix(FAVORITE_PRESET_PREFIX),
            )
        }
        // The shared normaliser sorts W×H ascending, so the same
        // preset reached in either orientation is the same favorite.
        assertEquals(
            GamePlay.favoriteKeyFor(BackendName.SOLO, "3x4"),
            GamePlay.favoriteKeyFor(BackendName.SOLO, "4x3"),
        )
    }

    @Test
    fun favoriteSurvivesExportImportRoundTrip() {
        val favoriteKey = GamePlay.favoriteKeyFor(BackendName.SOLO, "3x3")
        val bestKey = GamePlay.bestTimeKeyFor(BackendName.SOLO, "3x3")
        val sourceState = FakeSharedPreferences().apply {
            map[favoriteKey] = true
            map[bestKey] = 754_000L
        }
        val sourceDefault = FakeSharedPreferences().apply {
            map["nightMode"] = "dark"
        }
        val export = Backup.buildExport(contextWith(sourceDefault, sourceState))
        // The favorite is in the file, tagged as a Boolean.
        val stateJson = export.getJSONObject("stores").getJSONObject("state")
        assertEquals("b", stateJson.getJSONObject(favoriteKey).getString("t"))
        assertEquals(true, stateJson.getJSONObject(favoriteKey).getBoolean("v"))

        val targetState = FakeSharedPreferences()
        val targetDefault = FakeSharedPreferences()
        val targetContext = contextWith(targetDefault, targetState)
        val parsed = Backup.parseImport(targetContext, export.toString())
        val success = parsed as? Backup.ImportParse.Success
            ?: throw AssertionError("import rejected: $parsed")
        assertEquals(0, success.backup.dropped)
        assertEquals(true, success.backup.stores["state"]!![favoriteKey])
        assertTrue(Backup.applyImport(targetContext, success.backup))
        assertEquals(true, targetState.getBoolean(favoriteKey, false))
        assertEquals(754_000L, targetState.getLong(bestKey, -1))
    }

    @Test
    fun wrongTypedFavoriteIsDroppedNotFatal() {
        val favoriteKey = GamePlay.favoriteKeyFor(BackendName.SOLO, "3x3")
        val bestKey = GamePlay.bestTimeKeyFor(BackendName.SOLO, "3x3")
        // A doctored backup: the favorite stored as a string. The
        // type filter must drop exactly that entry and keep the rest.
        val text = JSONObject().apply {
            put("app", Backup.APP_TAG)
            put("schema_version", Backup.SCHEMA_VERSION)
            put("exported_at", "2026-11-24T00:00:00+0000")
            put("app_version", "0.6.0")
            put("stores", JSONObject().apply {
                put("default", JSONObject().apply {
                    put("nightMode", JSONObject().put("t", "s").put("v", "dark"))
                })
                put("state", JSONObject().apply {
                    put(favoriteKey, JSONObject().put("t", "s").put("v", "yes"))
                    put(bestKey, JSONObject().put("t", "l").put("v", 12345))
                })
            })
        }.toString()
        val targetState = FakeSharedPreferences()
        val targetDefault = FakeSharedPreferences()
        val parsed = Backup.parseImport(
            contextWith(targetDefault, targetState), text,
        )
        val success = parsed as? Backup.ImportParse.Success
            ?: throw AssertionError("import rejected: $parsed")
        assertEquals(1, success.backup.dropped)
        assertFalse(success.backup.stores["state"]!!.containsKey(favoriteKey))
        assertEquals(12345L, success.backup.stores["state"]!![bestKey])
        assertTrue(
            Backup.applyImport(
                contextWith(targetDefault, targetState), success.backup,
            )
        )
        assertFalse(targetState.contains(favoriteKey))
        assertEquals(12345L, targetState.getLong(bestKey, -1))
    }
}
