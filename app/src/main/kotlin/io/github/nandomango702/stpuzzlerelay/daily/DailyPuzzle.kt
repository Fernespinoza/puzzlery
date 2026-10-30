package io.github.nandomango702.stpuzzlerelay.daily

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl
import java.util.Calendar
import java.util.Locale

/**
 * One deterministic puzzle per calendar day, the same for every player.
 *
 * The puzzle type rotates through the currently enabled puzzles in a stable
 * order ([BackendName.all] is generated from CMakeLists.txt, so re-enabling
 * puzzles later automatically extends the rotation — the count is never
 * hardcoded). The seed is the date string, so generation is deterministic
 * for a given day. Each day offers two modes — [Mode.EASY] (the default
 * preset) and [Mode.HARD] (the last top-level preset) — with independent
 * completion records and streaks. Completions are recorded per date and mode
 * in SharedPreferences; only legit solves record (the daily hides
 * Show-solution entirely, and recording skips it defensively).
 */
object DailyPuzzle {
    /** Legacy per-date key (pre-mode dailies); reads count as EASY. */
    private const val KEY_PREFIX = "daily_complete_"
    private const val KEY_PREFIX_EASY = "daily_complete_easy_"
    private const val KEY_PREFIX_HARD = "daily_complete_hard_"

    enum class Mode(val keyPrefix: String) {
        EASY(KEY_PREFIX_EASY),
        HARD(KEY_PREFIX_HARD);

        companion object {
            fun fromString(value: String?): Mode =
                if (value == "hard") HARD else EASY
        }
    }

    /**
     * A date as "2026-10-21", in the device's local timezone. Locale.ROOT:
     * this string is a storage key and part of the daily seed, so it must
     * use ASCII digits in every locale (a default-locale %d produces e.g.
     * Arabic-Indic digits under ar/fa, splitting the record keyspace).
     */
    private fun dateString(cal: Calendar): String =
        String.format(
            Locale.ROOT, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

    /** Today's date as "2026-10-21", in the device's local timezone. */
    fun todayString(): String = dateString(Calendar.getInstance())

    private const val ROTATION_KEY = "daily_rotation_v1"

    /**
     * The daily rotation, frozen on first use: the ordered puzzle list is
     * persisted once, and puzzles a later version adds append at the END
     * (in name order among themselves) instead of re-sorting the whole
     * list — re-sorting would remap every past date to a different puzzle
     * and rewrite the calendar's history. Puzzles removed from the
     * catalog drop out.
     */
    fun rotation(context: Context): List<BackendName> {
        val current = BackendName.all.sortedBy { it.sourceName }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val storedNames = prefs.getString(ROTATION_KEY, null)
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val kept = storedNames.mapNotNull { BackendName.byLowerCase(it) }
        val appended = current.filter { it.sourceName !in storedNames.toSet() }
        val frozen = kept + appended
        if (frozen.map { it.sourceName } != storedNames) {
            prefs.edit {
                putString(ROTATION_KEY, frozen.joinToString(",") { it.sourceName })
            }
        }
        return frozen.ifEmpty { current }
    }

    /**
     * Which puzzle type is the daily for [dateStr] ("2026-10-21").
     * Rotates by day-of-year, so the same date always maps to the same type.
     */
    fun backendForDate(context: Context, dateStr: String = todayString()): BackendName {
        val backends = rotation(context)
        require(backends.isNotEmpty()) { "No puzzles enabled" }
        val (y, m, d) = dateStr.split("-").map { it.toInt() }
        val cal = Calendar.getInstance().apply {
            set(y, m - 1, d, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        return backends[(dayOfYear - 1) % backends.size]
    }

    /**
     * Records the first completion of [dateStr] in [mode]; later completions
     * of the same day and mode are kept as-is (first completion wins).
     * Returns the recorded solve time in milliseconds.
     */
    fun recordCompletion(
        context: Context,
        dateStr: String,
        mode: Mode,
        solveMillis: Long
    ): Long {
        // A solved attempt is no longer in progress: drop any stored board
        // so a later replay generates fresh (the first completion stands).
        clearAttempt(context, dateStr, mode)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = mode.keyPrefix + dateStr
        val existing = prefs.getLong(key, -1L)
        if (existing < 0) {
            prefs.edit { putLong(key, solveMillis) }
            return solveMillis
        }
        return existing
    }

    /** Solve time in milliseconds if [dateStr] was completed in [mode], else null. */
    fun completionTime(context: Context, dateStr: String, mode: Mode): Long? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var t = prefs.getLong(mode.keyPrefix + dateStr, -1L)
        // Legacy fallback: pre-mode dailies recorded under the plain key
        // were always the default (easy) preset.
        if (t < 0 && mode == Mode.EASY) {
            t = prefs.getLong(KEY_PREFIX + dateStr, -1L)
        }
        return if (t < 0) null else t
    }

    /**
     * In-progress daily attempts, kept apart from the per-backend save slot
     * (which normal games of the same type would overwrite — including
     * Practice games launched from the same picker). Once an attempt has
     * started, leaving and re-entering resumes it instead of regenerating
     * a fresh board, so the player can't retry the identical puzzle with
     * knowledge from the last attempt. Solved attempts are cleared by
     * [recordCompletion] and replay fresh.
     */
    private fun attemptSaveKey(dateStr: String, mode: Mode) =
        "daily_attempt_save_${mode.name.lowercase()}_$dateStr"
    private fun attemptTimerKey(dateStr: String, mode: Mode) =
        "daily_attempt_timer_${mode.name.lowercase()}_$dateStr"
    private fun attemptStartedKey(dateStr: String, mode: Mode) =
        "daily_attempt_started_${mode.name.lowercase()}_$dateStr"
    private fun attemptUsedSolveKey(dateStr: String, mode: Mode) =
        "daily_attempt_usedsolve_${mode.name.lowercase()}_$dateStr"
    private fun attemptUsedRestartKey(dateStr: String, mode: Mode) =
        "daily_attempt_usedrestart_${mode.name.lowercase()}_$dateStr"

    /**
     * Records the current state of an in-progress [dateStr]/[mode] attempt.
     * [started] latches: once an attempt has started it stays started even
     * if a later save happens before any interaction (e.g. right after a
     * restore). Callers skip solved games — a solved attempt is cleared
     * by [recordCompletion], never stored.
     */
    fun recordAttempt(
        context: Context,
        dateStr: String,
        mode: Mode,
        savedGame: String,
        timerMillis: Long,
        started: Boolean,
        usedShowSolution: Boolean,
        usedRestart: Boolean
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            putString(attemptSaveKey(dateStr, mode), savedGame)
            if (timerMillis >= 0) putLong(attemptTimerKey(dateStr, mode), timerMillis)
            else remove(attemptTimerKey(dateStr, mode))
            // Record-disqualifying flags persist with the attempt, like the
            // normal save slot, so a resume can't launder them away.
            putBoolean(attemptUsedSolveKey(dateStr, mode), usedShowSolution)
            putBoolean(attemptUsedRestartKey(dateStr, mode), usedRestart)
            if (started) putBoolean(attemptStartedKey(dateStr, mode), true)
            // Tiny permanent marker so the stats calendar can show this day
            // as attempted long after the full board was purged.
            if (started) putBoolean(attemptedDayKey(dateStr, mode), true)
        }
    }

    /** Saved board of an in-progress attempt, or null if there is none. */
    fun attemptSave(context: Context, dateStr: String, mode: Mode): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(attemptSaveKey(dateStr, mode), null)

    /** Saved solve-timer elapsed ms of an in-progress attempt, or -1. */
    fun attemptTimer(context: Context, dateStr: String, mode: Mode): Long =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(attemptTimerKey(dateStr, mode), -1L)

    /** Whether the attempt has started (the player has interacted with it). */
    fun attemptStarted(context: Context, dateStr: String, mode: Mode): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(attemptStartedKey(dateStr, mode), false)

    /** Whether Show solution was used during the attempt. */
    fun attemptUsedShowSolution(context: Context, dateStr: String, mode: Mode): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(attemptUsedSolveKey(dateStr, mode), false)

    /** Whether Restart was used during the attempt. */
    fun attemptUsedRestart(context: Context, dateStr: String, mode: Mode): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(attemptUsedRestartKey(dateStr, mode), false)

    /** True when re-entering should offer "Continue attempt". */
    fun hasResumableAttempt(context: Context, dateStr: String, mode: Mode): Boolean =
        attemptStarted(context, dateStr, mode) && attemptSave(context, dateStr, mode) != null

    /** Drops the stored attempt for [dateStr]/[mode], if any. */
    fun clearAttempt(context: Context, dateStr: String, mode: Mode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            remove(attemptSaveKey(dateStr, mode))
            remove(attemptTimerKey(dateStr, mode))
            remove(attemptStartedKey(dateStr, mode))
            remove(attemptUsedSolveKey(dateStr, mode))
            remove(attemptUsedRestartKey(dateStr, mode))
        }
    }

    /**
     * Drops attempt boards and attempted-day markers from months other than
     * [keepDateStr]'s month. Stats shows the current month only, so older
     * attempts can never resume. Completions are never touched (streaks
     * and totals need the full history).
     */
    fun purgeStaleAttempts(context: Context, keepDateStr: String) {
        val keepMonth = keepDateStr.take(7) // "YYYY-MM"
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stale = prefs.all.keys.filter { key ->
            (key.startsWith("daily_attempt_") || key.startsWith("daily_day_attempted_")) &&
                key.takeLast(10).let { date ->
                    date.length == 10 && date[4] == '-' && date[7] == '-' &&
                        date.take(7) != keepMonth
                }
        }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach { remove(it) } }
    }

    /**
     * Current [mode] streak: consecutive days with a completion, ending
     * today if today is solved, else ending yesterday (still alive — solve
     * today to keep it going). Zero when neither today nor yesterday
     * was solved. Computed from the completion records, so it can never
     * drift out of sync with them.
     */
    fun streak(context: Context, mode: Mode): Int {
        val cal = Calendar.getInstance()
        if (completionTime(context, dateString(cal), mode) == null) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var count = 0
        while (completionTime(context, dateString(cal), mode) != null) {
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    /** Formats milliseconds as mm:ss or h:mm:ss, matching the solve timer. */
    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    // ---- Stats calendar ----

    /** Per-mode state of one day's daily, for the stats calendar. */
    enum class ModeState { NOT_STARTED, IN_PROGRESS, SOLVED }

    /** State of one mode on one day, plus the solve time when solved. */
    data class DayModeState(val state: ModeState, val solveMillis: Long?)

    /** Marker key for "this mode was attempted on this date" (tiny, kept for history). */
    private fun attemptedDayKey(dateStr: String, mode: Mode) =
        "daily_day_attempted_${mode.name.lowercase()}_$dateStr"

    /** Whether [mode] was attempted on [dateStr] (started, solved or not). */
    fun dayAttempted(context: Context, dateStr: String, mode: Mode): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(attemptedDayKey(dateStr, mode), false)

    /**
     * State of [mode] on [dateStr] for the stats calendar. Solved wins
     * over attempted.
     */
    fun dayModeState(context: Context, dateStr: String, mode: Mode): DayModeState {
        completionTime(context, dateStr, mode)?.let {
            return DayModeState(ModeState.SOLVED, it)
        }
        if (dayAttempted(context, dateStr, mode) ||
            (dateStr == todayString() && hasResumableAttempt(context, dateStr, mode))
        ) {
            return DayModeState(ModeState.IN_PROGRESS, null)
        }
        return DayModeState(ModeState.NOT_STARTED, null)
    }

    /**
     * Preset params per mode for [backend] (Easy = first top-level
     * preset, Hard = last; preset-less games use the engine default
     * for Easy). Same mode split as the daily picker, minus titles.
     * Falls back to Easy-only on any preset-bridge failure.
     */
    fun modesFor(backend: BackendName): List<Pair<Mode, String?>> {
        return try {
            val topPresets = GameEngineImpl.getPresetsForBackend(backend)
                .filter { it.params != null }
            // First preset, not the engine default — see
            // GameChooser.dailyModeCards (Magnets' default is a
            // Tricky variant, not its simplest board).
            val easyParams = topPresets.firstOrNull()?.params
                ?: GameEngineImpl.getDefaultParams(backend)
            val easy = Mode.EASY to easyParams
            val hardParams = topPresets.lastOrNull()?.params
            if (hardParams == null || hardParams == easyParams) listOf(easy)
            else listOf(easy, Mode.HARD to hardParams)
        } catch (e: Exception) {
            listOf(Mode.EASY to null)
        }
    }

    /**
     * Distinct dates with a completion in [mode]. Legacy pre-mode records
     * count as EASY (but the mode-prefixed keys are not double-counted).
     */
    private fun solvedDates(context: Context, mode: Mode): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val dates = prefs.all.keys
            .filter { it.startsWith(mode.keyPrefix) }
            .map { it.removePrefix(mode.keyPrefix) }
            .toMutableSet()
        if (mode == Mode.EASY) {
            dates += prefs.all.keys
                .filter {
                    it.startsWith(KEY_PREFIX) &&
                        !it.startsWith(KEY_PREFIX_EASY) &&
                        !it.startsWith(KEY_PREFIX_HARD)
                }
                .map { it.removePrefix(KEY_PREFIX) }
        }
        return dates
    }

    /** Total days solved in [mode]. */
    fun totalSolved(context: Context, mode: Mode): Int = solvedDates(context, mode).size

    /** Longest streak of consecutive solved days in [mode]. */
    fun longestStreak(context: Context, mode: Mode): Int {
        val days = solvedDates(context, mode)
            .mapNotNull { daysFromCivil(it) }
            .toSortedSet()
        var longest = 0
        var run = 0
        var prev: Int? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }
        return longest
    }

    /** Days from civil (Howard Hinnant's algorithm); null for malformed dates. */
    private fun daysFromCivil(dateStr: String): Int? {
        val parts = dateStr.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        var (y, m, d) = parts
        y -= if (m <= 2) 1 else 0
        val era = if (y >= 0) y else y - 399
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
