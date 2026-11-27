package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.nandomango702.stpuzzlerelay.backup.Backup
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.PRESET_PROGRESSION_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.STATE_PREFS_NAME
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Preset progression (v0.7.0, Round 2): the smart chooser deal and
 * its Settings toggle. These tests pin:
 *
 *  - the next-unsolved scan walks the preset list in picker order
 *    and returns the first preset with no legit best;
 *  - a fully solved puzzle falls back to the random pick, driven
 *    through the injected index so the choice is deterministic here;
 *  - the toggle-off path deals nothing (the caller then generates
 *    exactly as it always has), and never consults the random pick;
 *  - the toggle itself is a Boolean in the default store that rides
 *    the backup export/import, and a wrong-typed entry for it in a
 *    doctored backup is dropped by the type filter, not fatal.
 *
 * The identical assertions also run in this environment as a
 * standalone harness against the real sources (JUnit and Mockito
 * are not resolvable in the offline build sandbox), the same split
 * the favorite-presets tranche used.
 */
class PresetProgressionTest {

    private val presets = listOf("3x3", "4x4", "5x5", "6x6")

    @Test
    fun nextUnsolvedScansInPickerOrder() {
        assertEquals("3x3", PresetProgression.nextUnsolved(presets, emptySet()))
        assertEquals(
            "5x5",
            PresetProgression.nextUnsolved(presets, setOf("3x3", "4x4")),
        )
        // Solved entries that are not presets are ignored.
        assertEquals(
            "4x4",
            PresetProgression.nextUnsolved(presets, setOf("3x3", "9x9")),
        )
    }

    @Test
    fun nextUnsolvedIsNullWhenAllSolvedOrNone() {
        assertNull(PresetProgression.nextUnsolved(presets, presets.toSet()))
        assertNull(PresetProgression.nextUnsolved(emptyList(), emptySet()))
    }

    @Test
    fun dealPrefersNextUnsolvedWithoutTouchingRandom() {
        var randomCalls = 0
        val dealt = PresetProgression.deal(
            presets, setOf("3x3"), progressionOn = true,
        ) { _ ->
            randomCalls++
            0
        }
        assertEquals("4x4", dealt)
        assertEquals(0, randomCalls)
    }

    @Test
    fun dealFallsBackToRandomPresetWhenAllSolved() {
        // The injected index is used verbatim, with the preset
        // count as its bound.
        var seenBound = -1
        val dealt = PresetProgression.deal(
            presets, presets.toSet(), progressionOn = true,
        ) { bound ->
            seenBound = bound
            bound - 1
        }
        assertEquals(presets.size, seenBound)
        assertEquals("6x6", dealt)
        assertEquals(
            "3x3",
            PresetProgression.deal(presets, presets.toSet(), true) { 0 },
        )
    }

    @Test
    fun dealIsNullWhenProgressionOffOrNoPresets() {
        var randomCalls = 0
        val countingRandom: (Int) -> Int = { _ -> randomCalls++; 0 }
        assertNull(
            PresetProgression.deal(presets, emptySet(), false, countingRandom)
        )
        assertNull(
            PresetProgression.deal(
                presets, presets.toSet(), false, countingRandom,
            )
        )
        assertNull(
            PresetProgression.deal(emptyList(), emptySet(), true, countingRandom)
        )
        assertEquals(0, randomCalls)
    }

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
    fun toggleSurvivesExportImportRoundTrip() {
        // Off is the interesting value: the toggle defaults to on,
        // so only a stored false proves the key rode the backup.
        val sourceDefault = FakeSharedPreferences().apply {
            map[PRESET_PROGRESSION_KEY] = false
        }
        val sourceState = FakeSharedPreferences().apply {
            map["savedBackend"] = "solo"
        }
        val export = Backup.buildExport(contextWith(sourceDefault, sourceState))
        val defaultJson = export.getJSONObject("stores").getJSONObject("default")
        assertEquals(
            "b", defaultJson.getJSONObject(PRESET_PROGRESSION_KEY).getString("t")
        )
        assertEquals(
            false,
            defaultJson.getJSONObject(PRESET_PROGRESSION_KEY).getBoolean("v"),
        )

        val targetDefault = FakeSharedPreferences()
        val targetState = FakeSharedPreferences()
        val targetContext = contextWith(targetDefault, targetState)
        val parsed = Backup.parseImport(targetContext, export.toString())
        val success = parsed as? Backup.ImportParse.Success
            ?: throw AssertionError("import rejected: $parsed")
        assertEquals(0, success.backup.dropped)
        assertEquals(false, success.backup.stores["default"]!![PRESET_PROGRESSION_KEY])
        assertTrue(Backup.applyImport(targetContext, success.backup))
        assertEquals(false, targetDefault.getBoolean(PRESET_PROGRESSION_KEY, true))
    }

    @Test
    fun wrongTypedToggleIsDroppedNotFatal() {
        // A doctored backup: the toggle stored as a string. The type
        // filter must drop exactly that entry and keep the rest —
        // an unfiltered string would crash the toggle's getBoolean.
        val text = JSONObject().apply {
            put("app", Backup.APP_TAG)
            put("schema_version", Backup.SCHEMA_VERSION)
            put("exported_at", "2026-11-24T00:00:00+0000")
            put("app_version", "0.6.0")
            put("stores", JSONObject().apply {
                put("default", JSONObject().apply {
                    put(
                        PRESET_PROGRESSION_KEY,
                        JSONObject().put("t", "s").put("v", "yes"),
                    )
                    put("nightMode", JSONObject().put("t", "s").put("v", "dark"))
                })
                put("state", JSONObject().apply {
                    put("savedBackend", JSONObject().put("t", "s").put("v", "solo"))
                })
            })
        }.toString()
        val targetDefault = FakeSharedPreferences()
        val targetState = FakeSharedPreferences()
        val parsed = Backup.parseImport(
            contextWith(targetDefault, targetState), text,
        )
        val success = parsed as? Backup.ImportParse.Success
            ?: throw AssertionError("import rejected: $parsed")
        assertEquals(1, success.backup.dropped)
        assertFalse(success.backup.stores["default"]!!.containsKey(PRESET_PROGRESSION_KEY))
        assertEquals("dark", success.backup.stores["default"]!!["nightMode"])
        assertTrue(
            Backup.applyImport(
                contextWith(targetDefault, targetState), success.backup,
            )
        )
        assertFalse(targetDefault.contains(PRESET_PROGRESSION_KEY))
        // Absent reads as the default: progression on.
        assertTrue(targetDefault.getBoolean(PRESET_PROGRESSION_KEY, true))
    }
}
