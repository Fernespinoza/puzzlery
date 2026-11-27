package io.github.nandomango702.stpuzzlerelay

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO
import android.content.res.Configuration.KEYBOARD_NOKEYS
import android.content.res.Configuration.NAVIGATIONHIDDEN_YES
import android.content.res.Configuration.NAVIGATION_DPAD
import android.content.res.Configuration.NAVIGATION_TRACKBALL
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MENU
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.SHOW_AS_ACTION_ALWAYS
import android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NavUtils
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.edit
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.TypedValueCompat.pxToDp
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl
import io.github.nandomango702.stpuzzlerelay.GameView.LimitDPIMode.LIMIT_AUTO
import io.github.nandomango702.stpuzzlerelay.GameView.LimitDPIMode.LIMIT_OFF
import io.github.nandomango702.stpuzzlerelay.GameView.LimitDPIMode.LIMIT_ON
import io.github.nandomango702.stpuzzlerelay.NightModeHelper.Companion.isNight
import io.github.nandomango702.stpuzzlerelay.Utils.readAllOf
import io.github.nandomango702.stpuzzlerelay.Utils.sendFeedbackDialog
import io.github.nandomango702.stpuzzlerelay.Utils.toastFirstFewTimes
import io.github.nandomango702.stpuzzlerelay.Utils.unlikelyBug
import io.github.nandomango702.stpuzzlerelay.backend.BRIDGES
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.BackendName.Companion.byLowerCase
import io.github.nandomango702.stpuzzlerelay.backend.GameEngine
import io.github.nandomango702.stpuzzlerelay.backend.GameEngine.KeysResult
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl.Companion.fromLaunch
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl.Companion.getDefaultParams
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl.Companion.getPrefs
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl.Companion.identifyBackend
import io.github.nandomango702.stpuzzlerelay.backend.FLOOD
import io.github.nandomango702.stpuzzlerelay.backend.GUESS
import io.github.nandomango702.stpuzzlerelay.backend.INERTIA
import io.github.nandomango702.stpuzzlerelay.backend.LOOPY
import io.github.nandomango702.stpuzzlerelay.backend.MINES
import io.github.nandomango702.stpuzzlerelay.backend.NET
import io.github.nandomango702.stpuzzlerelay.backend.PALISADE
import io.github.nandomango702.stpuzzlerelay.backend.PATTERN
import io.github.nandomango702.stpuzzlerelay.backend.SAMEGAME
import io.github.nandomango702.stpuzzlerelay.backend.SOLO
import io.github.nandomango702.stpuzzlerelay.backend.UNDEAD
import io.github.nandomango702.stpuzzlerelay.backend.UNEQUAL
import io.github.nandomango702.stpuzzlerelay.backend.UNTANGLE
import io.github.nandomango702.stpuzzlerelay.backend.UsedByJNI
import io.github.nandomango702.stpuzzlerelay.buttons.ArrowMode
import io.github.nandomango702.stpuzzlerelay.buttons.ButtonsView
import io.github.nandomango702.stpuzzlerelay.buttons.SEEN_SWAP_L_R_TOAST
import io.github.nandomango702.stpuzzlerelay.buttons.SWAP_L_R_KEY
import io.github.nandomango702.stpuzzlerelay.buttons.SwapMode
import io.github.nandomango702.stpuzzlerelay.config.ConfigBuilder
import io.github.nandomango702.stpuzzlerelay.config.ConfigBuilder.Event.CFG_SETTINGS
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.ARROW_KEYS_KEY_SUFFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.AUTO_ORIENT
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.AUTO_ORIENT_DEFAULT
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.BEST_TIME_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.FAVORITE_PRESET_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SOLVE_COUNT_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.TOTAL_TIME_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.BRIDGES_SHOW_H_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CHOOSER_STYLE_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.COMPLETED_PROMPT_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CONTROLS_REMINDERS_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.FULLSCREEN_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LAST_PARAMS_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LATIN_M_UNDO_SEEN
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LATIN_SHOW_M_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.UNEQUAL_SHOW_H_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LIMIT_DPI_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LONG_PRESS_TIMEOUT
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.MOUSE_BACK_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.MOUSE_LONG_PRESS_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.ORIENTATION_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.REDO_NEW_GAME_SEEN
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_BACKEND
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_COMPLETED_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_GAME_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.PRESET_PROGRESSION_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_TIMER_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_USED_RESTART_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_USED_SOLVE_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SHOW_BEST_TIME_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SHOW_SOLVE_TIMER_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.STATE_PREFS_NAME
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.STAY_AWAKE_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.UNDO_NEW_GAME_SEEN
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.UNDO_REDO_KBD_DEFAULT
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.UNDO_REDO_KBD_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.VICTORY_FLASH_KEY
import io.github.nandomango702.stpuzzlerelay.daily.DailyPuzzle
import io.github.nandomango702.stpuzzlerelay.databinding.CompletedDialogBinding
import io.github.nandomango702.stpuzzlerelay.databinding.MainBinding
import io.github.nandomango702.stpuzzlerelay.launch.DeprecatedProgressDialog
import io.github.nandomango702.stpuzzlerelay.playlist.PlaylistStore
import io.github.nandomango702.stpuzzlerelay.launch.GameGenerator
import io.github.nandomango702.stpuzzlerelay.launch.GameGenerator.Companion.cleanUpOldExecutables
import io.github.nandomango702.stpuzzlerelay.launch.GameGenerator.Companion.executableIsMissing
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.fromBookmark
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.fromContentURI
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.fromSeed
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.ofDailyAttempt
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.ofGameID
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.ofLocalState
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.ofSavedGameFromIntent
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.toGenerate
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.toGenerateFromChooser
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Companion.undoingOrRedoingNewGame
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Origin.BUTTON_OR_MENU_IN_ACTIVITY
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Origin.GENERATING_FROM_CHOOSER
import io.github.nandomango702.stpuzzlerelay.launch.GameLaunch.Origin.INTENT_COMPLEX_URI
import io.github.nandomango702.stpuzzlerelay.launch.MenuEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.MessageFormat
import java.util.concurrent.Future
import kotlin.random.Random
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.roundToInt

class GamePlay : ActivityWithLoadButton(), OnSharedPreferenceChangeListener, GameGenerator.Callback,
    ConfigBuilder.ActivityCallbacks, GameEngine.ActivityCallbacks {
    private lateinit var _binding: MainBinding
    private lateinit var statusBar: TextView
    private var timerView: TextView? = null
    private lateinit var buttons: ButtonsView
    private lateinit var mainLayout: RelativeLayout
    private lateinit var gameView: GameView
    private lateinit var patternModeBar: PatternModeBar
    private lateinit var prefs: SharedPreferences
    private lateinit var state: SharedPreferences
    private lateinit var gameGenerator: GameGenerator
    private var progress: DeprecatedProgressDialog? = null
    private val gameTypesById: MutableMap<Int, String> = linkedMapOf()
    private var gameTypesMenu = arrayOf<MenuEntry>()
    private var currentType = 0
    private var generationInProgress: Future<*>? = null
    private var solveEnabled = false
    private var customVisible = false
    private var undoEnabled = false
    private var redoEnabled = false
    private var undoIsLoadGame = false
    private var redoIsLoadGame = false
    private var undoToGame: String? = null

    /**
     * Set by leaveDailyToPractice(): the practice transition must not
     * leave the daily board behind as the undo target, or Undo would
     * restore the attempt as an ordinary game (the 7c hole). Consumed
     * by the next startGameConfirmed.
     */
    private var suppressUndoToGameOnce = false
    private var redoToGame: String? = null
    private var gameWantsTimer = false

    // Solve timer: starts on the player's first move, stops when the puzzle is solved,
    // and pauses while the puzzle isn't visible (time away doesn't count).
    // solvePausedAtRealtime marks when the current pause began, or null when not paused.
    private var solveStartRealtime: Long? = null
    private var solveFinalMillis: Long? = null
    private var solvePausedAtRealtime: Long? = null
    // True if "Show solution" was used on the current puzzle. Such a solve
    // must never count as a best time (it would always "win" in seconds).
    private var usedShowSolution = false
    /**
     * Set when the player restarts the current puzzle: that run no longer
     * counts for records — restarting lets you finish a known board in
     * seconds. Cleared for fresh puzzles in resetSolveTimer; leaving and
     * resuming does not set it.
     */
    private var usedRestart = false
    /**
     * True when the just-finished game set a new best time for its
     * preset: the completion dialog shows the "New best" line for it.
     * Set in completedInternal, cleared for fresh puzzles in
     * resetSolveTimer (so re-showing the dialog still shows it, but a
     * new game starts clean).
     */
    private var lastSolveSetNewBest = false

    // Playlist play mode: set from intent extras when launched from PlaylistsActivity.
    // playlistGames holds BackendName source names in play order; playlistIndex is the
    // position of the puzzle currently on screen (-1 when not in a playlist).
    private var playlistName: String? = null
    private var playlistGames: List<String> = emptyList()
    private var playlistIndex: Int = -1

    private fun inPlaylistMode() = playlistIndex in playlistGames.indices

    /** Date string ("2026-10-21") when this GamePlay is showing the daily puzzle, else null. */
    private var dailyModeDate: String? = null
    /** Which screen launched this game (EXTRA_ORIGIN); null = the chooser. */
    private var launchOrigin: String? = null

    /** Daily mode ("easy"/"hard") and its preset params; only meaningful in daily mode. */
    private var dailyMode: String? = null
    private var dailyModeParams: String? = null

    private fun inDailyMode() = dailyModeDate != null

    /**
     * Save-slot key for a backend in the state prefs. Ordinary games key
     * by the bare backend name; a game played as part of a playlist keys
     * by backend + playlist name, so playlist boards and general-play
     * boards of the same puzzle live apart and never overwrite each
     * other (Fernando, 2026-10-28: finishing Mines from the games list
     * used to spend the board a playlist run was sitting on). [playlist]
     * defaults to the running game's playlist.
     */
    private fun slotKey(backend: BackendName, playlist: String? = playlistName): String =
        if (playlist != null) "$backend|pl|$playlist" else backend.toString()

    private fun readPlaylistExtras(intent: Intent) {
        val games = intent.getStringArrayListExtra(EXTRA_PLAYLIST_GAMES)
        if (!games.isNullOrEmpty()) {
            playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME)
            playlistGames = games.toList()
            playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0).coerceIn(games.indices)
        } else {
            playlistName = null
            playlistGames = emptyList()
            playlistIndex = -1
        }
    }

    // specifically the screenshots test
    @get:VisibleForTesting
    var gameEngine = GameEngine.NOT_LOADED_YET
        private set
    var currentBackend: BackendName? = null
    private var startingBackend: BackendName? = null
    private var lastKeys = ""
    private var lastKeysIfArrows = ""
    private var menu: Menu? = null
    private var maybeUndoRedo = "" + GameView.UI_UNDO.toChar() + GameView.UI_REDO.toChar()
    private var everCompleted = false
    private var _wasNight = false
    private var appStartIntentOnResume: Intent? = null

    /** True when this instance was recreated with saved state (theme flip, process restore). */
    private var restoredInstance = false
    private var swapLROn = false

    private enum class UIVisibility(val value: Int) {
        UNDO(1), REDO(2), CUSTOM(4), SOLVE(8), STATUS(16)
    }

    private enum class MsgType {
        TIMER, COMPLETED, BACK_TIME_ELAPSED, SOLVE_TIMER
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Consume the press and do nothing, because the user pressed a key very recently
            // so it was probably accidental. If they meant it, they can try again, by which time
            // this handler will be disabled.
        }
    }

    private class PuzzlesHandler(outer: GamePlay) : Handler(Looper.getMainLooper()) {
        val ref = WeakReference(outer)

        override fun handleMessage(msg: Message) {
            val outer = ref.get()
            outer?.handleMessage(msg)
        }
    }

    val handler: Handler = PuzzlesHandler(this)

    private fun handleMessage(msg: Message) {
        when (MsgType.entries[msg.what]) {
            MsgType.TIMER -> {
                if (progress == null) {
                    gameEngine.timerTick()
                    if (currentBackend === INERTIA) {
                        gameView.ensureCursorVisible(gameEngine.cursorLocation)
                    }
                }
                if (gameWantsTimer) {
                    handler.sendMessageDelayed(
                        handler.obtainMessage(MsgType.TIMER.ordinal),
                        TIMER_INTERVAL.toLong()
                    )
                }
            }

            MsgType.COMPLETED -> {
                try {
                    completedInternal()
                } catch (activityWentAway: BadTokenException) {
                    // fine, nothing we can do here
                    Log.d(TAG, "completed failed!", activityWentAway)
                }
            }

            MsgType.BACK_TIME_ELAPSED -> {
                onBackPressedCallback.isEnabled = false
            }

            MsgType.SOLVE_TIMER -> {
                updateSolveTimerDisplay()
                if (solveStartRealtime != null && solveFinalMillis == null) {
                    handler.sendMessageDelayed(
                        handler.obtainMessage(MsgType.SOLVE_TIMER.ordinal),
                        SOLVE_TIMER_INTERVAL.toLong()
                    )
                }
            }
        }
    }

    /** Elapsed solve time in milliseconds; 0 if the timer hasn't started.
     * solveFinalMillis already holds a duration once solved, so it is
     * returned directly instead of subtracting the start time again. */
    private fun solveElapsedMillis(): Long {
        solveFinalMillis?.let { return it }
        val start = solveStartRealtime ?: return 0
        return SystemClock.elapsedRealtime() - start
    }

    private fun formatSolveTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    private fun updateSolveTimerDisplay() {
        val time = formatSolveTime(solveElapsedMillis())
        // While playing (not yet solved), append the best time when the
        // setting is on and a best exists. After a solve the clock shows
        // just the final time, so a new record never reads "time · Best time".
        timerView?.text = if (prefs.getBoolean(SHOW_BEST_TIME_KEY, true)
            && solveFinalMillis == null
        ) {
            bestTimeMillis()?.let { best ->
                "$time · ${getString(R.string.bestTimeDisplay, formatSolveTime(best))}"
            } ?: time
        } else {
            time
        }
    }

    /** Fastest legit solve time for the current puzzle preset, or null if none recorded. */
    private fun bestTimeMillis(): Long? {
        val key = bestTimeKey() ?: return null
        val best = state.getLong(key, -1)
        return if (best >= 0) best else null
    }

    /**
     * Storage key for the current game's best time: backend + its params
     * string, so each preset (size, difficulty, ...) keeps its own record.
     * Any W×H dimensions are sorted ascending first: the app flips w/h on
     * rotate, so without this the portrait and landscape orientations of
     * the same preset would keep separate records. Params without
     * dimensions pass through unchanged. Null when no game is running.
     */
    private fun bestTimeKey(): String? {
        val backend = currentBackend ?: return null
        val params = gameEngine.currentParams ?: return null
        return bestTimeKeyFor(backend, params)
    }

    /**
     * Shows or hides the solve clock per the "Show solve timer" setting.
     * Tracking itself keeps running either way, so re-enabling mid-game
     * shows the true elapsed time.
     */
    private fun applySolveTimerVisibility() {
        timerView?.visibility =
            if (prefs.getBoolean(SHOW_SOLVE_TIMER_KEY, true)) View.VISIBLE else View.GONE
    }

    /** Starts the solve timer on the player's first move of a puzzle. */
    private fun maybeStartSolveTimer() {
        if (solveStartRealtime == null && solveFinalMillis == null
            && currentBackend != null && progress == null
        ) {
            solveStartRealtime = SystemClock.elapsedRealtime()
            handler.sendMessage(handler.obtainMessage(MsgType.SOLVE_TIMER.ordinal))
        }
    }

    /** Freezes the timer at the moment the puzzle is solved. */
    private fun stopSolveTimer() {
        val start = solveStartRealtime
        if (start != null && solveFinalMillis == null) {
            solveFinalMillis = SystemClock.elapsedRealtime() - start
            handler.removeMessages(MsgType.SOLVE_TIMER.ordinal)
            updateSolveTimerDisplay()
        }
    }

    /** Clears the timer for a fresh puzzle. */
    private fun resetSolveTimer() {
        handler.removeMessages(MsgType.SOLVE_TIMER.ordinal)
        solveStartRealtime = null
        solveFinalMillis = null
        solvePausedAtRealtime = null
        usedShowSolution = false
        usedRestart = false
        lastSolveSetNewBest = false
        updateSolveTimerDisplay()
    }

    /**
     * Restores the solve timer after a saved game is reloaded, so the clock
     * resumes instead of resetting. Falls back to zero if nothing was saved.
     */
    private fun restoreSolveTimer() {
        val backend = currentBackend
        val elapsed = if (backend != null) {
            state.getLong(SAVED_TIMER_PREFIX + slotKey(backend), -1)
        } else {
            -1
        }
        resetSolveTimer()
        if (backend != null) {
            // The disqualifying flags were saved with the board (daily
            // resumes seed these keys from the attempt record first).
            usedShowSolution = state.getBoolean(SAVED_USED_SOLVE_PREFIX + slotKey(backend), false)
            usedRestart = state.getBoolean(SAVED_USED_RESTART_PREFIX + slotKey(backend), false)
        }
        if (elapsed >= 0) {
            solveStartRealtime = SystemClock.elapsedRealtime() - elapsed
            updateSolveTimerDisplay()
            handler.sendMessage(handler.obtainMessage(MsgType.SOLVE_TIMER.ordinal))
        }
    }

    /**
     * Freezes the solve timer when the puzzle stops being visible.
     * The start time is shifted forward on resume, so time away doesn't count.
     */
    private fun pauseSolveTimer() {
        if (solveStartRealtime != null && solveFinalMillis == null && solvePausedAtRealtime == null) {
            solvePausedAtRealtime = SystemClock.elapsedRealtime()
            handler.removeMessages(MsgType.SOLVE_TIMER.ordinal)
        }
    }

    /** Unfreezes the solve timer when the puzzle becomes visible again. */
    private fun resumeSolveTimer() {
        val pausedAt = solvePausedAtRealtime
        solvePausedAtRealtime = null
        val start = solveStartRealtime
        if (start != null && solveFinalMillis == null) {
            if (pausedAt != null) {
                solveStartRealtime = start + (SystemClock.elapsedRealtime() - pausedAt)
            }
            if (!handler.hasMessages(MsgType.SOLVE_TIMER.ordinal)) {
                handler.sendMessage(handler.obtainMessage(MsgType.SOLVE_TIMER.ordinal))
            }
        }
    }

    private fun showProgress(launch: GameLaunch) {        progress = DeprecatedProgressDialog(this, launch, {
            abort(null, launch.origin.shouldReturnToChooserOnFail)
        }, {
            resetBackendState(launch.whichBackend)
            currentBackend = null // prevent save undoing our reset
            abort(null, true)
        })
    }

    private fun resetBackendState(backend: BackendName) {
        val key = slotKey(backend)
        state.edit().apply {
            remove(SAVED_GAME_PREFIX + key)
            remove(SAVED_COMPLETED_PREFIX + key)
            // The remembered preset belongs to general play; a playlist
            // reset must not forget it.
            if (playlistName == null) remove(LAST_PARAMS_PREFIX + backend)
            remove(SAVED_TIMER_PREFIX + key)
            remove(SAVED_USED_SOLVE_PREFIX + key)
            remove(SAVED_USED_RESTART_PREFIX + key)
            apply()
        }
        prefs.edit { remove(backend.preferencesName) }
    }

    private fun dismissProgress() {
        try {
            progress?.dismiss()
        } catch (_: IllegalArgumentException) {
        } // race condition?
        progress = null
    }

    private fun saveToString(): String {
        check(currentBackend != null && progress == null) { "saveToString in invalid state" }
        val baos = ByteArrayOutputStream()
        gameEngine.serialise(baos)
        val saved = baos.toString()
        check(saved.isNotEmpty()) { "serialise returned empty string" }
        return saved
    }

    private fun save() {
        if (currentBackend == null) return
        val saved = saveToString()
        // Daily sessions save ONLY the attempt copy further below, never
        // the per-backend slot: writing the daily board there silently
        // replaced an in-progress normal game of the same puzzle, and a
        // later chooser tap then resumed the daily board as an ordinary
        // game (solving it credited the normal stats, not the daily). The
        // SAVED_BACKEND pointer is part of that normal resume state, so
        // it stays with the last normal game too.
        if (dailyModeDate == null) {
            // Playlist games save to their own per-playlist slot (see
            // slotKey); the SAVED_BACKEND resume pointer and the
            // remembered preset describe general play only, so playlist
            // saves leave both alone — the same reasoning as daily mode.
            val key = slotKey(currentBackend!!)
            state.edit().apply {
                remove("engineName")
                if (playlistName == null) {
                    putString(SAVED_BACKEND, currentBackend.toString())
                    putString(LAST_PARAMS_PREFIX + currentBackend, gameEngine.currentParams)
                }
                putString(SAVED_GAME_PREFIX + key, saved)
                putBoolean(SAVED_COMPLETED_PREFIX + key, everCompleted)
                // Persist the solve timer alongside the board, so leaving and coming
                // back resumes the clock instead of resetting it.
                val timerKey = SAVED_TIMER_PREFIX + key
                val elapsed = solveElapsedForSave()
                if (elapsed >= 0) putLong(timerKey, elapsed) else remove(timerKey)
                // Persist the record-disqualifying flags with the board, so
                // leaving and resuming can't launder a restarted or
                // solution-shown run back into a recordable one.
                putBoolean(SAVED_USED_SOLVE_PREFIX + key, usedShowSolution)
                putBoolean(SAVED_USED_RESTART_PREFIX + key, usedRestart)
                apply()
            }
        }
        // Daily mode: keep a separate copy of the in-progress attempt (see
        // DailyPuzzle). The per-backend slot above can't serve this: normal
        // games of the same type — including Practice from the same picker —
        // would overwrite it. Solved games are never stored: completion
        // clears the slot, and a replay after solving generates fresh.
        // The attempt counts as started once the solve timer is running,
        // i.e. the player has interacted with the board; before that,
        // re-entering just regenerates the identical board, so nothing
        // is lost by not resuming.
        dailyModeDate?.let { date ->
            if (solveFinalMillis == null) {
                DailyPuzzle.recordAttempt(
                    this, date, DailyPuzzle.Mode.fromString(dailyMode),
                    saved, solveElapsedForSave(), solveStartRealtime != null,
                    usedShowSolution, usedRestart
                )
            }
        }
    }

    /**
     * Accumulated solve time in ms for saving; -1 if the timer never started
     * or the puzzle is already solved (solved games are never restored).
     */
    private fun solveElapsedForSave(): Long {
        val start = solveStartRealtime ?: return -1
        if (solveFinalMillis != null) return -1
        val end = solvePausedAtRealtime ?: SystemClock.elapsedRealtime()
        return max(0, end - start)
    }

    /** The bottom inset (dp) the board area is currently laid out with. */
    private fun boardBottomInset(): Int {
        val bottomInsetPx = if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val insets = ViewCompat.getRootWindowInsets(gameView)?.getInsets(systemBars() or displayCutout() or ime())
            max(insets?.bottom?.minus(statusBar.height) ?: 0, 0)
        } else 0
        return pxToDp(bottomInsetPx.toFloat(), resources.displayMetrics).toInt()
    }

    fun gameViewResized() {
        if (progress == null && gameView.w > 10 && gameView.h > 10) {
            gameEngine.resizeEvent(gameView.wDip, gameView.hDip, boardBottomInset())
        }
    }

    /**
     * Describe board (TalkBack Phase 1): swaps a recording callbacks
     * in for the GameView, forces one full redraw through it via a
     * same-size resize (whose JNI side runs midend_force_redraw
     * synchronously), swaps back, and shows the captured board text
     * formatted as spoken rows by DescribeFormatter. The real view's
     * bitmap is never touched, so the player sees no flicker.
     */
    /** Solo's board shape: cells per side, and block size when it has blocks. */
    data class SoloShape(
        val cr: Int,
        val blockCols: Int?,
        val blockRows: Int?,
    )

    /** A board capture plus the geometry the TalkBack grid needs. */
    data class BoardCapture(
        val texts: List<DescribeRecorder.DrawnText>,
        /** Drawn lines (Solo's Killer cage segments); only the grid model reads them. */
        val lines: List<DescribeRecorder.DrawnLine>,
        /** Filled rects (Pattern's per-cell state fills, Mines' tile fills); recorded for those captures only. */
        val rects: List<DescribeRecorder.DrawnRect>,
        /** Polygons (Mines' flags); recorded for Mines captures only. */
        val polys: List<DescribeRecorder.DrawnPoly>,
        /** Solo's board shape, parsed from the game params; null when unparseable. */
        val soloShape: SoloShape?,
        /** The board's offset within the capture space (the frontend's centring). */
        val originX: Float,
        val originY: Float,
        /** The engine's game size along one axis (Solo boards are square). */
        val boardExtent: Int,
    )

    /**
     * One forced redraw of the current board through a
     * [DescribeRecorder]; returns the capture, or null when no
     * board is ready. Shared by Describe board and the TalkBack
     * virtual grid (SoloTalkBackGrid), so both always read the
     * same board the same way.
     */
    fun captureBoardTexts(): BoardCapture? {
        if (currentBackend == null || progress != null ||
            gameView.wDip <= 0 || gameView.hDip <= 0
        ) {
            return null
        }
        val inset = boardBottomInset()
        val recorder = DescribeRecorder(gameView)
        // Pattern's mode bar plans its gestures from the board's
        // cell states, which ride along with this capture as
        // recorded fills; Mines' TalkBack grid reads its cell
        // states from the fills too, and its flags from recorded
        // polygons. No other game pays for recording either.
        recorder.recordRects = currentBackend == PATTERN || currentBackend == MINES
        recorder.recordPolys = currentBackend == MINES
        gameEngine.setViewCallbacks(recorder)
        try {
            gameEngine.resizeEvent(gameView.wDip, gameView.hDip, inset)
        } finally {
            gameEngine.setViewCallbacks(gameView)
        }
        val gameSize = gameEngine.gameSizeInGameCoords
        return BoardCapture(
            texts = recorder.texts,
            lines = recorder.lines,
            rects = recorder.rects,
            polys = recorder.polys,
            soloShape = soloShapeFromParams(gameEngine.currentParams),
            // android.c centres the game in the view: (view − game)/2.
            originX = (gameView.wDip - gameSize.x) / 2f,
            originY = (gameView.hDip - inset - gameSize.y) / 2f,
            boardExtent = gameSize.x,
        )
    }

    /**
     * Pattern's clue texts + validated geometry for GameView's
     * pinned-clue redraw (v0.6.0). Same recorder capture as Describe
     * board; PatternClueGeometry.compute cross-checks the derived
     * margins against the engine's reported extent, so any mismatch
     * returns null here and the view simply doesn't pin.
     */
    fun capturePatternClues(): PatternClues? {
        if (currentBackend != PATTERN) return null
        val cap = captureBoardTexts() ?: return null
        val gameSize = gameEngine.gameSizeInGameCoords
        val params = gameEngine.currentParams?.substringBefore(':') ?: return null
        val m = Regex("""^(\d+)x(\d+)$""").find(params) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        val h = m.groupValues[2].toIntOrNull() ?: return null
        val geom = PatternClueGeometry.compute(
            w, h, gameEngine.engineTilesize, gameSize.x, gameSize.y,
        ) ?: return null
        return PatternClues.fromCapture(cap.texts, geom, cap.originX, cap.originY, cap.rects)
    }

    /**
     * Mines' board capture for the TalkBack grid: the shared
     * forced-redraw capture, plus the board's dimensions and
     * mine total parsed from its params (mines.c's
     * encode_params: "30x16n99" — the count rides after 'n' in
     * the full encoding; without one, decode_params defaults
     * it to a tenth of the board, mirrored here). The model
     * validates the geometry itself; this only shapes the data.
     */
    fun captureMinesBoard(): MinesCapture? {
        if (currentBackend != MINES) return null
        val cap = captureBoardTexts() ?: return null
        val params = gameEngine.currentParams?.substringBefore(':') ?: return null
        val m = Regex("""^(\d+)x(\d+)(?:n(\d+))?""").find(params) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        val h = m.groupValues[2].toIntOrNull() ?: return null
        val totalMines = m.groupValues[3].toIntOrNull() ?: (w * h / 10)
        val gameSize = gameEngine.gameSizeInGameCoords
        return MinesCapture(
            cap.texts, cap.lines, cap.rects, cap.polys,
            w, h, totalMines,
            gameSize.x, gameSize.y, gameEngine.engineTilesize,
            cap.originX, cap.originY,
        )
    }

    /**
     * Solo's board shape from its params (solo.c's encode_params):
     * "3x3" → 9 with 3×3 blocks, "2x3" → 6 with 2-wide 3-tall
     * blocks, jigsaw "9j" → 9 with no blocks.
     */
    private fun soloShapeFromParams(params: String?): SoloShape? {
        val p = params?.substringBefore(':') ?: return null
        val m = Regex("""^(\d+)(?:x(\d+)|j)""").find(p) ?: return null
        val c = m.groupValues[1].toIntOrNull() ?: return null
        val r = m.groupValues[2].toIntOrNull()
        val cr = c * (r ?: 1)
        if (cr !in 2..16) return null
        return SoloShape(cr, if (r != null) c else null, r)
    }

    private fun describeBoard() {
        val backend = currentBackend
        val capture = captureBoardTexts()
        if (backend == null || capture == null) {
            // No silent failure: a TalkBack user who asked for the
            // board must hear that it isn't ready, not nothing.
            Toast.makeText(this, R.string.describe_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val context = when {
            inDailyMode() -> getString(R.string.daily) + ", " +
                (if (dailyMode == "hard") getString(R.string.daily_hard)
                else getString(R.string.daily_easy))
            inPlaylistMode() -> "Playlist: " + (playlistName ?: "")
            else -> null
        }
        val status = statusBar.text?.toString()?.trim()?.ifEmpty { null }
        val body = DescribeFormatter.format(
            context, status, capture.texts,
            notesArePencil = backend == SOLO,
        )
        val pad = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(this).apply {
            text = body
            setPadding(pad, pad / 2, pad, pad / 2)
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.describe_board) + " — " + backend.displayName)
            .setView(ScrollView(this).apply { addView(textView) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // --- TalkBack Solo grid input (Phase 2, builds 2/2.1, 2026-11-06) ---
    //
    // The grid (SoloTalkBackGrid on GameView) owns navigation and
    // selection; input reuses the app's EXISTING control bar — its
    // keys are real Compose buttons with TalkBack labels (digits,
    // Backspace, Mark), so there is no separate pad to draw
    // (Fernando's call in build 2.1: the custom pad overlapped the
    // board and fought the grid; the bar was already accessible).
    // Selecting a cell jumps accessibility focus down to the bar.
    // Pencil entry is the engine's own gesture — activate the
    // selected cell again — as it is for sighted players.
    //
    // Selection state is a MIRROR of the engine's highlight,
    // maintained from the inputs this build itself sends — the
    // engine offers no way to read its highlight back. The mirror
    // follows solo.c's ANDROID tap rules: tapping a given clears
    // the highlight, tapping the selected cell again toggles
    // pencil mode, moving to another cell keeps the mode. (The
    // bar's "Mark" key is NOT the pencil toggle: in solo.c 'M'
    // fills every empty cell with all pencil marks.)

    private var tbSelectedCell: Int? = null
    private var tbPencilMode = false

    /** GameView reports the Solo TalkBack grid going active or inactive. */
    fun onTalkBackGridActiveChanged(active: Boolean) {
        if (!active) {
            tbSelectedCell = null
            tbPencilMode = false
        }
    }

    /** True while digit/backspace keys from the control bar should be announced. */
    private fun talkBackStripAnnounces(code: Int): Boolean =
        currentBackend == SOLO && ::gameView.isInitialized &&
            gameView.isTalkBackGridActive() && tbSelectedCell != null &&
            (code in '0'.code..'9'.code || code in 'a'.code..'z'.code ||
                code in 'A'.code..'Z'.code || code == '\b'.code)

    /** The board model before a control-bar key, for the result announcement. */
    private fun talkBackStripKeyBefore(code: Int, isRepeat: Boolean): SoloGridModel? =
        if (!isRepeat && talkBackStripAnnounces(code)) {
            gameView.currentTalkBackModel()
        } else {
            null
        }

    /** After a control-bar key, speak the selected cell's fresh state. */
    private fun talkBackStripKeyAfter(code: Int, before: SoloGridModel?) {
        if (before == null) return
        val id = tbSelectedCell ?: return
        announceTalkBackResult(id, before)
    }

    // FOCUS JUMP — PARKED (Fernando's call, 2026-11-08).
    // Selecting a cell was meant to move TalkBack focus to the
    // control bar's "1" key (his build 2.1 idea). Three attempts
    // never landed on his device (2.1: ButtonsView provider
    // children; 2.2: posted provider-tree search; 2.3/2.4:
    // window-tree search with a spoken diagnostic) and 2.3
    // crashed the app in the attempt; the verified parts of the
    // tranche (grid, bar keys, announcements) don't need it.
    // His ruling: he would still prefer the jump eventually, but
    // it is out for now — an app yanking focus may disorient
    // explore-by-touch users anyway — and the manual move
    // (select a cell, then move down to the control bar) is to
    // be documented in Solo's Help text instead (v0.6.0 Better
    // Help tranche). Revisit post-1.0 or on tester demand; the
    // full attempt history lives in the Solo grid design doc.

    /** GameView calls this after a grid cell is activated (the engine tap already sent). */
    fun talkBackCellSelected(virtualId: Int, modelBefore: SoloGridModel) {
        val row = virtualId / modelBefore.cr
        val col = virtualId % modelBefore.cr
        val cell = modelBefore.cellAt(row, col)
        if (cell.given) {
            // Tapping a given clears the engine's highlight.
            tbSelectedCell = null
            tbPencilMode = false
            announceForTalkBack(modelBefore.label(this, row, col))
            return
        }
        if (tbSelectedCell == virtualId) {
            tbPencilMode = !tbPencilMode // the engine toggles pencil on a re-tap
        } else {
            tbSelectedCell = virtualId
        }
        announceForTalkBack(
            getString(R.string.tb_selected_cell, row + 1, col + 1) +
                if (tbPencilMode) getString(R.string.tb_selected_pencil_suffix) else ""
        )
    }

    /**
     * After a control-bar key, speak the selected cell's fresh state, plus
     * any row/column/box this move just completed (filled and
     * conflict-free, newly complete versus [before]).
     */
    private fun announceTalkBackResult(id: Int, before: SoloGridModel?) {
        val after = gameView.currentTalkBackModel() ?: return
        if (id !in 0 until after.cr * after.cr) return
        val row = id / after.cr
        val col = id % after.cr
        val sb = StringBuilder(after.label(this, row, col))
        if (before != null) {
            for (unit in after.newlyCompletedUnits(before, row, col)) {
                sb.append(' ')
                sb.append(
                    when (unit) {
                        is SoloGridModel.Unit.Row ->
                            getString(R.string.tb_ann_row_complete, unit.index + 1)
                        is SoloGridModel.Unit.Column ->
                            getString(R.string.tb_ann_col_complete, unit.index + 1)
                        is SoloGridModel.Unit.Box ->
                            getString(R.string.tb_ann_box_complete, unit.index + 1)
                    }
                )
            }
        }
        announceForTalkBack(sb.toString())
    }

    /**
     * GameView calls this after a Pattern grid cell is activated
     * (the engine tap, if any, already sent). There is no
     * selection mirror to maintain — a Pattern activation acts on
     * the cell at once — so this only speaks the result: the
     * cell's fresh label from a re-capture, or its unchanged
     * label when the activation was a set-state no-op (the
     * spoken state is itself the confirmation nothing changed).
     * The solve announcement is the shared completion path in
     * completedInternal, as for every game.
     */
    fun patternTalkBackCellChanged(virtualId: Int, modelBefore: PatternGridModel) {
        val after = gameView.currentPatternTalkBackModel() ?: modelBefore
        if (virtualId !in 0 until after.w * after.h) return
        announceForTalkBack(
            after.label(this, virtualId / after.w, virtualId % after.w)
        )
    }

    /**
     * GameView calls this after a Mines grid activation (the
     * engine input, if any, already sent). Like Pattern, there
     * is no selection mirror — a Mines activation acts at once —
     * so this only speaks the result, with two Mines-specific
     * additions:
     *
     * - Death: when the activated square's fresh state is the
     *   mine the player hit, "Game over." leads the label. The
     *   app surfaces a loss only as the status bar's "DEAD!"
     *   and the drawn bang tile — neither reaches a TalkBack
     *   player at the moment it happens — so the grid speaks
     *   it, with the label itself giving the why. (A win needs
     *   nothing extra: the shared completion announcement in
     *   completedInternal already fires for Mines.)
     * - After a flag or unflag, the marked count is appended
     *   ("Marked: 3 of 10"), counted from the fresh capture —
     *   the same numbers the status bar shows a sighted
     *   player, spoken at the moment they change.
     */
    fun minesTalkBackCellChanged(
        virtualId: Int,
        modelBefore: MinesGridModel,
        action: MinesGridAction,
    ) {
        val after = gameView.currentMinesTalkBackModel() ?: modelBefore
        if (virtualId !in 0 until after.w * after.h) return
        val row = virtualId / after.w
        val col = virtualId % after.w
        val sb = StringBuilder()
        if (after.stateAt(row, col) == MinesCellState.BANG) {
            sb.append(getString(R.string.tb_mines_game_over)).append(' ')
        }
        sb.append(after.label(this, row, col))
        if (action == MinesGridAction.FLAG) {
            if (sb.lastOrNull() != '.') sb.append('.')
            sb.append(' ')
                .append(
                    getString(
                        R.string.tb_mines_marked_count,
                        after.flagCount, after.totalMines,
                    )
                )
                .append('.')
        }
        announceForTalkBack(sb.toString())
    }

    private fun announceForTalkBack(message: String) {
        window.decorView.announceForAccessibility(message)
    }

    fun suggestDensity(w: Int, h: Int): Float {
        return gameEngine.suggestDensity(w, h)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        state = getSharedPreferences(STATE_PREFS_NAME, MODE_PRIVATE)
        gameGenerator = GameGenerator()
        applyStayAwake()
        applyOrientation()
        super.onCreate(savedInstanceState)
        restoredInstance = savedInstanceState != null
        readPlaylistExtras(intent)
        launchOrigin = intent.getStringExtra(EXTRA_ORIGIN)
        if (executableIsMissing(this)) {
            finish()
            return
        }
        inflateContent()
        applyFullscreen()
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT)
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        appStartIntentOnResume = intent
        cleanUpOldExecutables(prefs, state, File(applicationInfo.dataDir))
    }

    // Also called from onConfigurationChanged()
    private fun inflateContent() {
        _binding = MainBinding.inflate(layoutInflater)
        setContentView(_binding.root)
        setSupportActionBar(_binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayUseLogoEnabled(false)
            addOnMenuVisibilityListener { visible: Boolean ->
                // https://code.google.com/p/android/issues/detail?id=69205
                if (!visible) {
                    this@GamePlay.invalidateOptionsMenu()
                    rethinkActionBarCapacity()
                }
            }
        }
        mainLayout = _binding.mainLayout
        statusBar = _binding.statusBar
        timerView = _binding.timerView
        applySolveTimerVisibility()
        updateSolveTimerDisplay()
        gameView = _binding.gameView
        patternModeBar = _binding.patternModeBar
        patternModeBar.onModeSelected = { mode ->
            gameView.setPatternMode(mode)
            // The bar's buttons carry the mode names as content
            // descriptions; the announcement is what a TalkBack
            // user hears on switching, since focus stays put.
            patternModeBar.announceForAccessibility(
                getString(PatternModeBar.descriptionRes(mode))
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(gameView) { v, windowInsets ->
            gameViewResized()
            WindowInsetsCompat.CONSUMED
        }
        buttons = _binding.buttons.apply {
            onKeyListener.value = { c: Int, isRepeat: Boolean ->
                val ch = c.toChar()
                val maybeLowercase =
                    if (!swapLROn && currentBackend === PALISADE && "HJKL".indexOf(ch) > -1) ch.lowercaseChar()
                    else ch
                runOnUiThread {
                    // TalkBack grid: capture the board before a
                    // control-bar digit/backspace so the result can
                    // be announced against it (build 2.1).
                    val tbBefore = talkBackStripKeyBefore(maybeLowercase.code, isRepeat)
                    sendKey(0, 0, maybeLowercase.code, isRepeat)
                    talkBackStripKeyAfter(maybeLowercase.code, tbBefore)
                }
            }
            onSwapLRListener.value = { swap: Boolean ->
                runOnUiThread {
                    setSwapLR(swap)
                    // The toggle's label carries the new mode on
                    // focus, but focus stays on the button when it
                    // flips, so say the new mode out loud — the
                    // Pattern mode bar's switch-announcement
                    // precedent. Harmless when TalkBack is off
                    // (nothing is listening); the toast below stays
                    // as the sighted feedback.
                    buttons.announceForAccessibility(
                        SwapMode.label(currentBackend ?: UNTANGLE, swap) { getString(it) }
                    )
                    toastFirstFewTimes(
                        this@GamePlay, state, SEEN_SWAP_L_R_TOAST, 4,
                        if (swap) R.string.toast_swap_l_r_on else R.string.toast_swap_l_r_off
                    )
                }
            }
        }
        gameView.requestFocus()
        _wasNight = isNight(resources.configuration)
        applyLimitDPI(false)
        applyLongPressTimeout()
        applyMouseLongPress()
        applyMouseBackKey()
        window.setBackgroundDrawable(null)
    }

    /** work around Android issue 21181 whose bug page has vanished :-(  */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return progress == null && (gameView.onKeyDown(keyCode, event)
                || super.onKeyDown(keyCode, event))
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KEYCODE_MENU) {
            if (hackForSubmenus == null) openOptionsMenu()
            hackForSubmenus!!.performIdentifierAction(R.id.game_menu, 0)
            return true
        }
        // work around Android issue 21181
        return progress == null && (gameView.onKeyUp(keyCode, event)
                || super.onKeyUp(keyCode, event))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        // Only delegate for MENU key, delegating for other keys might break something(?)
        if (progress == null && event.keyCode == KEYCODE_MENU && gameView.dispatchKeyEvent(event))
            true
        else
            super.dispatchKeyEvent(event)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the activity's intent current: without this, a later recreate
        // redelivers the FIRST launch intent of this task, replaying a stale
        // game (and the playlist's starting position) instead of the latest.
        setIntent(intent)
        readPlaylistExtras(intent)
        // Launch origin is per-launch too: it steers the exit path.
        launchOrigin = intent.getStringExtra(EXTRA_ORIGIN)
        // Daily mode is per-launch: any new intent without the extra leaves daily mode.
        dailyModeDate = intent.getStringExtra(EXTRA_DAILY_DATE)
        dailyMode = intent.getStringExtra(EXTRA_DAILY_MODE)
        dailyModeParams = intent.getStringExtra(EXTRA_DAILY_PARAMS)
        progress?.let {
            stopGameGeneration()
            dismissProgress()
        }
        var backendFromChooser: BackendName? = null
        // Don't regenerate on resurrecting a URL-bound activity from the recent list
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            val s = intent.getStringExtra("game")
            val u = intent.data
            if (!s.isNullOrEmpty()) {
                Log.d(TAG, "starting game from Intent, " + s.length + " bytes")
                val launch: GameLaunch = try {
                    ofSavedGameFromIntent(s)
                } catch (e: IllegalArgumentException) {
                    abort(e.message, true) // invalid file
                    return
                }
                startGame(launch)
                return
            } else if (u != null) {
                Log.d(TAG, "URI is: \"$u\"")
                if (OUR_SCHEME == u.scheme) {
                    val split = u.schemeSpecificPart.split(":".toRegex(), limit = 2).toTypedArray()
                    val incoming = byLowerCase(split[0])
                    if (incoming == null) {
                        abort("Unrecognised game in URI: $u", true)
                        return
                    }
                    if (split.size > 1) {
                        if (split[1].contains(":")) {
                            startGame(ofGameID(incoming, split[1], INTENT_COMPLEX_URI))
                        } else {  // params only
                            startGame(toGenerate(incoming, split[1], INTENT_COMPLEX_URI))
                        }
                        return
                    }
                    if (incoming == currentBackend && !everCompleted
                        && intent.getStringExtra(EXTRA_DAILY_DATE) == null
                        && !intent.getBooleanExtra(EXTRA_PLAYLIST_FRESH, false)
                    ) {
                        // already alive & playing incomplete game of that kind; keep it.
                        // (A daily launch always proceeds: the user explicitly asked for today's puzzle.)
                        return
                    }
                    backendFromChooser = incoming
                }
                if (backendFromChooser == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ("file".equals(u.scheme, ignoreCase = true) || u.scheme == null)
                    ) {
                        unlikelyBug(this, R.string.old_file_manager)
                        return
                    }
                    try {
                        checkSize(u)
                        startGame(fromContentURI(readAllOf(contentResolver.openInputStream(u), MAX_SAVE_SIZE)))
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        abort(e.message, true)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        abort(e.message, true)
                    }
                    return
                }
            }
        }
        if (backendFromChooser != null) {
            val dailyDate = dailyModeDate
            if (dailyDate != null) {
                // Daily puzzle: deterministic seed from the date, so every player
                // gets the identical puzzle that day, in the picked mode.
                // Raw preset params (no per-device orientation tweak).
                val params = dailyModeParams ?: getDefaultParams(backendFromChooser)
                val mode = DailyPuzzle.Mode.fromString(dailyMode)
                val attempt = if (DailyPuzzle.completionTime(this, dailyDate, mode) == null)
                    DailyPuzzle.attemptSave(this, dailyDate, mode) else null
                if (attempt != null) {
                    // Resume the in-progress attempt instead of regenerating:
                    // a fresh board after the player has already learned from
                    // the last attempt would let them cheese the daily.
                    // (A solved mode replays fresh instead: the first
                    // completion already stands, so there's nothing to protect.)
                    // Seed the normal timer key with the attempt's elapsed
                    // time so the restore branch picks it up; same for the
                    // attempt's record-disqualifying flags.
                    val timer = DailyPuzzle.attemptTimer(this, dailyDate, mode)
                    state.edit {
                        val timerKey = SAVED_TIMER_PREFIX + backendFromChooser
                        if (timer >= 0) putLong(timerKey, timer) else remove(timerKey)
                        putBoolean(
                            SAVED_USED_SOLVE_PREFIX + backendFromChooser,
                            DailyPuzzle.attemptUsedShowSolution(this@GamePlay, dailyDate, mode)
                        )
                        putBoolean(
                            SAVED_USED_RESTART_PREFIX + backendFromChooser,
                            DailyPuzzle.attemptUsedRestart(this@GamePlay, dailyDate, mode)
                        )
                    }
                    Log.d(TAG, "resuming daily attempt for $dailyDate")
                    startGame(ofDailyAttempt(backendFromChooser, "$params#$dailyDate", attempt))
                    return
                }
                Log.d(TAG, "generating daily puzzle for $dailyDate")
                startGame(fromSeed(backendFromChooser, "$params#$dailyDate"))
                return
            }
            if (!restoredInstance && intent.getBooleanExtra(EXTRA_PLAYLIST_FRESH, false)) {
                // A playlist start-over: deal a fresh board instead of
                // resuming the in-progress one, which is what made Play
                // feel like it merely continued the old run. (Recreates
                // ignore the flag and resume, like any other launch.)
                Log.d(TAG, "playlist start-over; generating fresh")
                startGame(toGenerateFromChooser(backendFromChooser))
                return
            }
            // Playlist entries resume the playlist's own board for this
            // puzzle; chooser entries resume the general one (slotKey).
            val entryPlaylist = intent.getStringExtra(EXTRA_PLAYLIST_NAME)
                ?.takeIf {
                    !intent.getStringArrayListExtra(EXTRA_PLAYLIST_GAMES).isNullOrEmpty()
                }
            val entryKey = slotKey(backendFromChooser, entryPlaylist)
            val savedGame =
                state.getString(SAVED_GAME_PREFIX + entryKey, null)
            // We have a saved game, and if it's completed the user probably wants a fresh one.
            // Theoretically we could silently load it and ask midend_status() but remembering is
            // still faster and some people play large games.
            val wasCompleted = state.getBoolean(
                SAVED_COMPLETED_PREFIX + entryKey,
                false
            )
            if (savedGame == null || wasCompleted) {
                // Preset progression (v0.7.0): a plain chooser entry
                // with no board to resume deals the puzzle's next
                // unsolved preset instead of the historical default
                // generation. Playlist entries keep their own flow
                // and are never dealt; with the toggle off (or
                // nothing to deal) generation is exactly as before.
                // Resume precedence is structural: this branch only
                // runs when there is no in-progress board to resume.
                val deal = if (entryPlaylist == null) {
                    progressionDeal(backendFromChooser)
                } else {
                    null
                }
                if (deal != null) {
                    Log.d(TAG, "preset progression deal: $deal")
                    startGame(
                        toGenerate(backendFromChooser, deal, GENERATING_FROM_CHOOSER)
                    )
                } else {
                    Log.d(TAG, "generating as requested")
                    startGame(toGenerateFromChooser(backendFromChooser))
                }
                return
            }
            Log.d(TAG, "restoring last state of $backendFromChooser")
            startGame(ofLocalState(backendFromChooser, savedGame, true))
        } else if (!resumeSavedGame()) {
            Log.d(TAG, "no state, starting chooser")
            startChooserAndFinish()
        }
    }

    /**
     * Starts the game held in the normal save slot (SAVED_BACKEND). Used by
     * plain relaunches and by recreate restores. Returns false only when
     * there is no saved backend at all.
     */
    private fun resumeSavedGame(): Boolean {
        // A recreated playlist game resumes the board from its own
        // playlist slot; the general pointer below describes general
        // play only, so it can't stand in for it.
        val plName = intent.getStringExtra(EXTRA_PLAYLIST_NAME)
        if (plName != null) {
            val uriBackend = intent.data?.takeIf { it.scheme == OUR_SCHEME }
                ?.schemeSpecificPart?.split(":".toRegex(), limit = 2)
                ?.firstOrNull()?.let { byLowerCase(it) }
            if (uriBackend != null) {
                val plSaved = state.getString(
                    SAVED_GAME_PREFIX + slotKey(uriBackend, plName), null
                )
                if (plSaved != null) {
                    Log.d(TAG, "recreate; resuming playlist game of $uriBackend")
                    startGame(ofLocalState(uriBackend, plSaved, false))
                } else {
                    Log.d(TAG, "recreate; no playlist board, generating $uriBackend")
                    startGame(toGenerateFromChooser(uriBackend))
                }
                return true
            }
        }
        val savedBackend = byLowerCase(state.getString(SAVED_BACKEND, null)) ?: return false
        val savedGame = state.getString(SAVED_GAME_PREFIX + savedBackend, null)
        if (savedGame == null) {
            Log.e(TAG, "missing state for $savedBackend")
            startGame(toGenerateFromChooser(savedBackend))
        } else {
            Log.d(TAG, "normal launch; resuming game of $savedBackend")
            startGame(ofLocalState(savedBackend, savedGame, false))
        }
        return true
    }

    /**
     * The preset progression deal for a chooser entry on [backend]
     * (see [PresetProgression]): the next unsolved top-level preset
     * in picker order, or a random preset when every one is solved.
     * Null when the "Preset progression" setting is off, the puzzle
     * has no top-level presets, or the preset bridge fails — the
     * caller then generates exactly as it always has. Solved = a
     * legit best time is recorded, the standard Stats, the chooser
     * badges and the unsolved random pools all use. Top-level only,
     * scanned in the bridge's own order: the same set and order the
     * daily rotation and the completion math walk.
     */
    private fun progressionDeal(backend: BackendName): String? {
        val progressionOn = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(PRESET_PROGRESSION_KEY, true)
        val topPresets = try {
            GameEngineImpl.getPresetsForBackend(backend)
                .filter { it.params != null }
                .map { it.params!! }
        } catch (e: Exception) {
            return null
        }
        val solved = topPresets.filterTo(mutableSetOf()) {
            state.getLong(bestTimeKeyFor(backend, it), -1) >= 0
        }
        return PresetProgression.deal(topPresets, solved, progressionOn) { bound ->
            Random.nextInt(bound)
        }
    }

    private fun warnOfStateLoss(
        backend: BackendName,
        continueLoading: Runnable,
        returnToChooser: Boolean
    ) {
        var careAboutOldGame =
            !state.getBoolean(SAVED_COMPLETED_PREFIX + slotKey(backend), true)
        if (careAboutOldGame) {
            val savedGame = state.getString(SAVED_GAME_PREFIX + slotKey(backend), null)
            if (savedGame == null || savedGame.contains("NSTATES :1:1")) {
                careAboutOldGame = false
            }
        }
        if (careAboutOldGame) {
            AlertDialog.Builder(this@GamePlay)
                .setMessage(
                    MessageFormat.format(getString(R.string.replaceGame), backend.displayName)
                )
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    continueLoading.run()
                }
                .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int ->
                    abort(null, returnToChooser)
                }
                .show()
        } else {
            continueLoading.run()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        this.menu = menu
        menuInflater.inflate(R.menu.main, menu)
        applyUndoRedoKbd()
        return true
    }

    private var hackForSubmenus: Menu? = null
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        hackForSubmenus = menu
        updateUndoRedoEnabled()
        val enableType = generationInProgress != null || gameTypesById.isNotEmpty() || customVisible
        menu.findItem(R.id.type_menu).apply {
            isEnabled = enableType
            isVisible = enableType
        }
        return true
    }

    private fun orientGameType(backendName: BackendName?, type: String): String {
        if (!prefs.getBoolean(AUTO_ORIENT, AUTO_ORIENT_DEFAULT)
            || backendName === SOLO  // Solo is square whatever happens so no point
        ) {
            return type
        }
        val viewLandscape = gameView.w > gameView.h
        val matcher = DIMENSIONS.matcher(type)
        if (matcher.matches()) {
            with(matcher) {
                val w = group(1)!!.toInt()
                val h = group(3)!!.toInt()
                val typeLandscape = w > h
                if (typeLandscape != viewLandscape) {
                    return group(3)!! + group(2)!! + "x" + group(2)!! + group(1) + group(4)!!
                }
            }
        }
        return type
    }

    private fun updateUndoRedoEnabled() {
        val undoItem = menu!!.findItem(R.id.undo)
        val redoItem = menu!!.findItem(R.id.redo)
        undoItem.isEnabled = undoEnabled
        redoItem.isEnabled = redoEnabled
        undoItem.setIcon(if (undoEnabled) R.drawable.ic_action_undo else R.drawable.ic_action_undo_disabled)
        redoItem.setIcon(if (redoEnabled) R.drawable.ic_action_redo else R.drawable.ic_action_redo_disabled)
    }

    private fun startChooserAndFinish() {
        NavUtils.navigateUpFromSameTask(this)
    }

    /**
     * Exits back to the screen that launched this game. StatsActivity
     * stays in the task beneath GamePlay, so a plain finish() lands
     * back on it with its tab and month untouched. The Daily screen
     * lives inside the chooser, so finishing there asks the chooser
     * to re-open it on resume. Anything else keeps the historical
     * behaviour: navigate up to the chooser.
     */
    private fun exitToOrigin() {
        when (launchOrigin) {
            ORIGIN_STATS -> finish()
            ORIGIN_DAILY -> {
                GameChooser.requestDailyReopen()
                finish()
            }
            else -> startChooserAndFinish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        var ret = true
        // these all build menus on demand because we had to wait for anchor buttons to exist
        when (itemId) {
            R.id.game_menu -> doGameMenu()
            R.id.type_menu -> doTypeMenu()
            R.id.help_menu -> doHelpMenu()
            android.R.id.home -> if (inPlaylistMode()) finish() else exitToOrigin()
            R.id.undo -> sendKey(0, 0, GameView.UI_UNDO)
            R.id.redo -> sendKey(0, 0, GameView.UI_REDO)
            else -> ret = super.onOptionsItemSelected(item)
        }
        // https://code.google.com/p/android/issues/detail?id=69205
        invalidateOptionsMenu()
        rethinkActionBarCapacity()
        return ret
    }

    private fun doGameMenu() {
        val gameMenu = popupMenuWithIcons()
        gameMenu.menuInflater.inflate(R.menu.game_menu, gameMenu.menu)
        val solveItem = gameMenu.menu.findItem(R.id.solve)
        // No Show-solution in daily mode: either you solve it or you don't.
        solveItem.isEnabled = solveEnabled && !inDailyMode()
        solveItem.isVisible = solveEnabled && !inDailyMode()
        // No Restart in daily mode: retrying the identical board with
        // knowledge from the last attempt would give the daily away.
        val restartItem = gameMenu.menu.findItem(R.id.restart)
        restartItem.isEnabled = !inDailyMode()
        restartItem.isVisible = !inDailyMode()
        // In daily mode the New game slot becomes the practice exit
        // (Fernando, 2026-10-28): an ordinary fresh board of the same
        // puzzle and preset. The daily attempt is saved first and stays
        // resumable from the picker.
        if (inDailyMode()) {
            gameMenu.menu.findItem(R.id.new_game).title =
                getString(R.string.daily_leave_to_practice)
        }
        // Re-show the completion dialog if it was dismissed (e.g. via the back button).
        val showResultItem = gameMenu.menu.findItem(R.id.show_result)
        showResultItem.isEnabled = reshowCompletion != null
        showResultItem.isVisible = reshowCompletion != null
        // Bookmark (the v0.6.0 checkpoint): a mid-solve tool, so both
        // items are offered only while this run is unfinished — a
        // bookmark of or into a solved board is meaningless, and
        // gating on the run being unfinished is also what keeps a
        // post-completion restore from re-entering the recording
        // path. "Go to bookmark" stays disabled until one is set
        // for this run.
        val bookmarkRunLive = !everCompleted && currentBackend != null
        gameMenu.menu.findItem(R.id.set_bookmark).isEnabled = bookmarkRunLive
        gameMenu.menu.findItem(R.id.go_to_bookmark).isEnabled =
            bookmarkRunLive && bookmarkKey()?.let { Bookmark.has(it) } == true
        gameMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.new_game -> if (inDailyMode()) leaveDailyToPractice() else startNewGame()
                R.id.show_result -> reshowCompletion?.invoke()
                R.id.restart -> {
                    resetSolveTimer()
                    usedRestart = true
                    gameEngine.restartEvent()
                }
                R.id.solve -> solveMenuItemClicked()
                R.id.set_bookmark -> setBookmark()
                R.id.go_to_bookmark -> goToBookmark()
                R.id.load -> loadGame()
                R.id.save -> try {
                    saveLauncher.launch(suggestFilenameForShare())
                } catch (_: ActivityNotFoundException) {
                    unlikelyBug(this, R.string.saf_missing_short)
                }
                R.id.share -> share()
                R.id.describe_board -> describeBoard()
                R.id.settings -> startActivity(Intent(this@GamePlay, PrefsActivity::class.java)
                    .putExtra(PrefsActivity.PrefsMainFragment.BACKEND_EXTRA, currentBackend.toString()))
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        gameMenu.show()
    }

    /**
     * The daily's practice exit: bank the attempt exactly as it stands
     * (so the picker can resume it later), then start an ordinary fresh
     * board of the same puzzle at the daily mode's preset. The startGame
     * guard drops daily mode for the new launch, as it does for any
     * non-daily seed.
     */
    private fun leaveDailyToPractice() {
        val backend = currentBackend ?: return
        val params = dailyModeParams ?: getDefaultParams(backend)
        save()
        suppressUndoToGameOnce = true
        startGame(
            GameLaunch.toGenerate(backend, params, GameLaunch.Origin.CUSTOM_DIALOG)
        )
    }

    /**
     * Identity of the run currently on screen, for bookmark keying:
     * the game's save slot (which already separates playlist boards
     * from general-play boards of the same puzzle) plus the daily
     * date when this is a daily attempt, so a bookmark can only ever
     * be served back to the run that set it.
     */
    private fun bookmarkKey(): String? {
        val backend = currentBackend ?: return null
        return slotKey(backend) + "|" + (dailyModeDate ?: "")
    }

    /** "Set bookmark": snapshot the current board as this run's checkpoint. */
    private fun setBookmark() {
        val key = bookmarkKey() ?: return
        Bookmark.set(key, saveToString())
        Toast.makeText(this, R.string.bookmark_set, Toast.LENGTH_SHORT).show()
    }

    /**
     * "Go to bookmark": reload the checkpoint through the same
     * serialised-state path the save system uses (GameLaunch with a
     * saved state, loaded by GameEngineImpl.fromLaunch), with the
     * BOOKMARK_RESTORE origin so the run's timer and record flags
     * carry on untouched. One-way: no return point is created for
     * the state being left, and the bookmark itself is kept, so
     * going to it again is harmless.
     */
    private fun goToBookmark() {
        val key = bookmarkKey() ?: return
        val saved = Bookmark.savedFor(key) ?: return
        startGame(fromBookmark(saved))
        Toast.makeText(this, R.string.bookmark_restored, Toast.LENGTH_SHORT).show()
    }

    private fun solveMenuItemClicked() {
        try {
            // The flag goes on BEFORE the call on purpose: the completion
            // callback fires synchronously inside solveEvent() and reads
            // it to decide whether the solve may be recorded.
            usedShowSolution = true
            gameEngine.solveEvent()
        } catch (e: IllegalArgumentException) {
            // The solver refused and applied nothing, so the run was
            // never actually shown a solution: roll the flag back.
            usedShowSolution = false
            messageBox(getString(R.string.Error), e.message, false)
        }
    }

    private val typeClickListener = MenuItem.OnMenuItemClickListener { item: MenuItem ->
        val itemId = item.itemId
        if (itemId == R.id.custom) {
            gameEngine.configEvent(this, CFG_SETTINGS.jni, this, currentBackend!!)
        } else {
            val presetParams = orientGameType(
                currentBackend, gameTypesById[itemId]!!
            )
            Log.d(TAG, "preset: $itemId: $presetParams")
            startGame(
                toGenerate(currentBackend!!, presetParams, BUTTON_OR_MENU_IN_ACTIVITY)
            )
        }
        true
    }

    private fun doTypeMenu(
        menuEntries: Array<MenuEntry> = gameTypesMenu,
        includeCustom: Boolean = true
    ) {
        val typeMenu = PopupMenu(this@GamePlay, findViewById(R.id.type_menu))
        typeMenu.menuInflater.inflate(R.menu.type_menu, typeMenu.menu)
        for (entry in menuEntries) {
            // Favorite presets (starred in Stats) carry a star here
            // too, and solved presets (a legit best time is recorded)
            // carry a check, so the picker shows the same state the
            // stats screen and bests are keyed on. Display only — a
            // popup menu item can't take a long-press, so starring
            // itself lives on the stats screen's preset rows. The
            // glyphs are also what TalkBack speaks for the state:
            // "black star" and "check mark" lead the item's title.
            val favorite = entry.params != null && currentBackend != null &&
                state.getBoolean(favoriteKeyFor(currentBackend!!, entry.params), false)
            val solved = entry.params != null && currentBackend != null &&
                state.getLong(bestTimeKeyFor(currentBackend!!, entry.params), -1) >= 0
            val added = typeMenu.menu.add(
                R.id.typeGroup,
                entry.id,
                Menu.NONE,
                buildString {
                    if (favorite) append("★ ")
                    if (solved) append("✓ ")
                    append(orientGameType(currentBackend, entry.title))
                }
            )
            if (entry.params != null) {
                added.setOnMenuItemClickListener(typeClickListener)
                if (currentType == entry.id) {
                    added.isChecked = true
                }
            } else if (entry.submenu != null) {
                if (menuContainsCurrent(entry.submenu)) {
                    added.isChecked = true
                }
                added.setOnMenuItemClickListener {
                    doTypeMenu(entry.submenu, false)
                    true
                }
            }
        }
        typeMenu.menu.setGroupCheckable(R.id.typeGroup, true, true)
        if (includeCustom) {
            typeMenu.menu.findItem(R.id.custom).apply {
                isVisible = customVisible
                setOnMenuItemClickListener(typeClickListener)
                if (currentType < 0) isChecked = true
            }
        }
        typeMenu.show()
    }

    private fun menuContainsCurrent(submenu: Array<MenuEntry>?): Boolean {
        for (entry in submenu!!) {
            if (entry.id == currentType) {
                return true
            }
            if (entry.submenu != null && menuContainsCurrent(entry.submenu)) {
                return true
            }
        }
        return false
    }

    private fun doHelpMenu() {
        PopupMenu(this@GamePlay, findViewById(R.id.help_menu)).apply {
            setForceShowIcon(true)
            menuInflater.inflate(R.menu.help_menu, menu)
            menu.findItem(R.id.solve).apply {
                // No Show-solution in daily mode: either you solve it or you don't.
                isEnabled = solveEnabled && !inDailyMode()
                isVisible = solveEnabled && !inDailyMode()
            }
            menu.findItem(R.id.this_game).title = MessageFormat.format(
                getString(R.string.how_to_play_game), this@GamePlay.title
            )
            // Only games whose manual carries a strategy guide get
            // the second door to it (see hasStrategyGuide).
            menu.findItem(R.id.strategy_guide).isVisible = hasStrategyGuide()
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.this_game -> {
                        startActivity(Intent(this@GamePlay, HelpActivity::class.java)
                                .putExtra(HelpActivity.TOPIC, gameEngine.htmlHelpTopic()))
                        return@setOnMenuItemClickListener true
                    }
                    R.id.strategy_guide -> {
                        startActivity(Intent(this@GamePlay, HelpActivity::class.java)
                                .putExtra(
                                    HelpActivity.TOPIC,
                                    gameEngine.htmlHelpTopic() + "-guide"
                                ))
                        return@setOnMenuItemClickListener true
                    }
                    R.id.solve -> {
                        solveMenuItemClicked()
                        return@setOnMenuItemClickListener true
                    }
                    R.id.feedback -> {
                        sendFeedbackDialog(this@GamePlay)
                        return@setOnMenuItemClickListener true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    /**
     * Whether this game has a strategy guide, decided exactly the
     * way the Help screen decides: the manual pages that carry the
     * "Strategy guide" button link to a "<topic>-guide" asset page,
     * so the guide exists iff that asset does — no second list of
     * game names to drift out of sync. The localised folder is
     * checked first, then English, mirroring HelpActivity's own
     * fallback when it loads a page.
     */
    private fun hasStrategyGuide(): Boolean {
        val topic = gameEngine.htmlHelpTopic() ?: return false
        val guide = "$topic-guide.html"
        // locales[0] needs minSdkVersion 24
        @Suppress("DEPRECATION") val lang = resources.configuration.locale.language
        return try {
            resources.assets.list(lang)?.contains(guide) == true ||
                resources.assets.list("en")?.contains(guide) == true
        } catch (_: IOException) {
            false
        }
    }

    private fun popupMenuWithIcons(): PopupMenu =
        PopupMenu(this, findViewById(R.id.game_menu)).apply {
            setForceShowIcon(true)
        }

    private fun share() {
        val saved = saveToString()
        val uriWithMimeType: Uri = try {
            writeCacheFile(saved)
        } catch (_: IOException) {
            unlikelyBug(this, R.string.cache_fail_short)
            return
        }
        val intentBuilder = IntentBuilder(this)
            .setStream(uriWithMimeType)
            .setType(MIME_TYPE)
        startActivity(intentBuilder.createChooserIntent())
    }

    @Throws(IOException::class)
    private fun writeCacheFile(content: String): Uri {
        val shareDir = File(cacheDir, "share").apply { mkdir() }
        val file = File(shareDir, suggestFilenameForShare())
        FileOutputStream(file).use {
            it.write(content.toByteArray())
        }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun suggestFilenameForShare(): String = currentBackend!!.displayName + ".sgtp"

    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                val saved = saveToString()
                contentResolver.openFileDescriptor(uri, "w").use { pfd ->
                    if (pfd == null) throw IOException("Could not open $uri")
                    FileOutputStream(pfd.fileDescriptor).use { fos ->
                        fos.write(saved.toByteArray())
                    }
                }
            } catch (e: IOException) {
                messageBox(
                    getString(R.string.Error),
                    getString(R.string.save_failed_prefix) + e.message,
                    false
                )
            }
        }

    private fun isPalisadeFullCursor(): Boolean =
        if (currentBackend === PALISADE) getPrefs(this, currentBackend!!)?.contains("cursor-mode=full") ?: false else false

    private fun abort(why: String?, returnToChooser: Boolean) {
        stopGameGeneration()
        dismissProgress()
        if (!why.isNullOrEmpty()) {
            messageBox(getString(R.string.Error), why, returnToChooser)
        } else if (returnToChooser) {
            startChooserAndFinish()
            return
        }
        startingBackend = currentBackend
        currentBackend?.let {
            setKeys(gameEngine.requestKeys(it, gameEngine.currentParams, isPalisadeFullCursor()))
        }
    }

    private fun startNewGame() {
        startGame(
            toGenerate(
                currentBackend!!,
                orientGameType(currentBackend, gameEngine.currentParams!!),
                BUTTON_OR_MENU_IN_ACTIVITY
            )
        )
    }

    override fun startGame(launch: GameLaunch) {
        startGame(launch, false)
    }

    private fun startGame(launch: GameLaunch, isRedo: Boolean) {
        Log.d(TAG, "startGame: $launch")
        // Leaving the daily: any fresh game that isn't today's date-seeded
        // daily (New game, a preset switch, a custom seed, a restore)
        // becomes ordinary play. Without this, "New game" inside the daily
        // would silently stay flagged as the daily — and solving that
        // random puzzle would wrongly record a daily completion. The one
        // exception is a bookmark restore: it reloads a state from this
        // same run, so the run stays the daily attempt it already was.
        if (dailyModeDate != null
            && launch.origin != GameLaunch.Origin.BOOKMARK_RESTORE
        ) {
            val dailySeed =
                "${dailyModeParams ?: getDefaultParams(launch.whichBackend)}#$dailyModeDate"
            if (launch.seed != dailySeed) {
                dailyModeDate = null
                dailyMode = null
                dailyModeParams = null
            }
        }
        check(progress == null) { "startGame while already starting!" }
        val previousGame: String?
        if (isRedo || launch.needsGenerating) {
            gameEngine.purgeStates()
            redoToGame = null
            previousGame = if (currentBackend == null) null else saveToString()
        } else {
            previousGame = null
        }
        showProgress(launch)
        stopGameGeneration()
        startingBackend = launch.whichBackend
        if (launch.needsGenerating) {
            startGameGeneration(launch, previousGame)
        } else if (!launch.origin.isOfLocalState && launch.saved != null) {
            warnOfStateLoss(
                launch.whichBackend,
                { startGameConfirmed(launch, previousGame) },
                launch.origin.shouldReturnToChooserOnFail
            )
        } else {
            startGameConfirmed(launch, previousGame)
        }
    }

    private fun startGameGeneration(launch: GameLaunch, previousGame: String?) {
        val args = listOf(launch.whichBackend.toString()) +
                (launch.seed?.let { listOf("--seed", it) } ?: listOf(decideParams(launch)))
        generationInProgress =
            gameGenerator.generate(applicationInfo, launch, args, previousGame, this)
    }

    private fun decideParams(launch: GameLaunch): String {
        launch.params?.let {
            Log.d(TAG, "Using specified params: $it")
            return it
        }
        getLastParams(launch.whichBackend)?.let {
            Log.d(TAG, "Using last params: $it")
            return it
        }
        orientGameType(launch.whichBackend, getDefaultParams(launch.whichBackend)).let {
            Log.d(TAG, "Using default params with orientation: $it")
            return it
        }
    }

    override fun gameGeneratorSuccess(launch: GameLaunch, previousGame: String?) {
        runOnUiThread {
            generationInProgress = null
            startGameConfirmed(launch, previousGame)
        }
    }

    override fun gameGeneratorFailure(e: Exception, launch: GameLaunch) {
        runOnUiThread {
            if (launch.origin.shouldResetBackendStateOnFail() && hasState(launch.whichBackend)) {
                resetBackendState(launch.whichBackend)
                generationInProgress = null
                dismissProgress()
                startGame(launch)
            } else {
                abort(e.message, launch.origin.shouldReturnToChooserOnFail)  // probably bogus params
            }
        }
    }

    private fun hasState(backend: BackendName): Boolean =
        state.run {
            contains(SAVED_GAME_PREFIX + slotKey(backend))
                || contains(SAVED_COMPLETED_PREFIX + slotKey(backend))
                || (playlistName == null && contains(LAST_PARAMS_PREFIX + backend))
        }

    private fun startGameConfirmed(launch: GameLaunch, previousGame: String?) {
        check(!(launch.saved == null && launch.gameID == null)) { "startGameConfirmed with un-generated game" }
        val changingGame = previousGame == null || startingBackend !== identifyBackend(previousGame)
        val suppressUndo = suppressUndoToGameOnce
        suppressUndoToGameOnce = false
        undoToGame = if (previousGame != null && !changingGame && previousGame != launch.saved
            && !suppressUndo
        ) {
            previousGame
        } else {
            null
        }
        gameEngine = try {
            fromLaunch(launch, this, gameView, this)
        } catch (e: IllegalArgumentException) {
            abort(e.message, launch.origin.shouldReturnToChooserOnFail) // probably bogus params
            return
        }
        currentBackend = startingBackend
        // Bookmark lifecycle: a bookmark belongs to the one run that
        // set it. Any launch that starts a different run — new game,
        // preset or custom switch, game switch, undo/redo across a
        // new-game boundary, a fresh entry from the chooser — drops
        // it, so it can never be restored into another board. The two
        // launches that continue this same run keep it: a bookmark
        // restore itself (going to the checkpoint twice is harmless),
        // and a same-session recreate resume (rotation/theme flips
        // must not lose it — the state lives outside the Activity).
        if (launch.origin != GameLaunch.Origin.BOOKMARK_RESTORE
            && launch.origin != GameLaunch.Origin.RESTORING_LAST_STATE_APP_START
        ) {
            Bookmark.clear()
        }
        refreshColours()
        gameView.resetZoomForClear()
        gameView.clear()
        applyUndoRedoKbd()
        gameView.keysHandled = 0
        everCompleted = false
        reshowCompletion = null
        if (launch.origin == GameLaunch.Origin.BOOKMARK_RESTORE) {
            // Bookmark restore: this is the SAME run continuing, not a
            // reload — the ROADMAP's build-time checks for the feature.
            // The solve timer is NOT reset or re-based (no
            // resetSolveTimer / restoreSolveTimer call), and the
            // record-disqualifying flags (usedRestart, usedShowSolution)
            // are left exactly as they stand: restoring neither trips
            // the restart flag nor launders a restarted or
            // solution-shown run back into a recordable one.
        } else if (launch.saved != null && !launch.generated) {
            // Reloading a saved game: pick up the timer where it left off.
            restoreSolveTimer()
        } else {
            // Fresh game: start from zero. (Freshly generated launches
            // carry a save string too, so `saved != null` alone can't
            // tell them apart from a resume — that's what `generated` is
            // for. Without it, a new game resumed the previous game's
            // saved time instead of resetting.)
            resetSolveTimer()
        }
        val currentParams = orientGameType(currentBackend, gameEngine.currentParams!!)
        refreshPresets(currentParams)
        gameView.setDragModeFor(currentBackend)
        // The Pattern mode bar exists for Pattern only. It sits
        // in the layout chain above the timer/status/buttons
        // stack, so showing it shrinks GameView itself and the
        // board can never slide under it (the same accounting
        // that keeps the board off the existing bars — which is
        // why boardBottomInset needs no addition for it).
        // setDragModeFor has just reset the mode to Fill; sync
        // the bar's selection from the view's state.
        patternModeBar.visibility =
            if (currentBackend == PATTERN) View.VISIBLE else View.GONE
        patternModeBar.selectMode(gameView.patternMode)
        title = currentBackend!!.displayName
        // Daily indicator: the toolbar reads "Loopy · Daily" in daily mode
        // and plain "Loopy" in ordinary play, so the mode is always visible.
        // (The activity title stays plain so "How to play" menus read right.)
        supportActionBar?.title = if (inDailyMode())
            "${currentBackend!!.title} · ${getString(R.string.daily)}"
        else currentBackend!!.title
        val flags = gameEngine.uiVisibility
        changedState(
            flags and UIVisibility.UNDO.value > 0,
            flags and UIVisibility.REDO.value > 0
        )
        customVisible = flags and UIVisibility.CUSTOM.value > 0
        solveEnabled = flags and UIVisibility.SOLVE.value > 0
        setStatusBarVisibility(flags and UIVisibility.STATUS.value > 0)
        setKeys(gameEngine.requestKeys(currentBackend!!, currentParams, isPalisadeFullCursor()))
        inertiaFollow(false)
        // We have a saved completion flag but completion could have been undone; find out whether
        // it's really completed
        if (launch.origin.shouldHighlightCompletionOnLaunch() && gameEngine.isCompletedNow) {
            completed()
        }
        val hasArrows = computeArrowMode(currentBackend).hasArrows()
        gameEngine.setCursorVisibility(hasArrows)
        if (changingGame) {
            if (prefs.getBoolean(CONTROLS_REMINDERS_KEY, true)) {
                if (hasArrows || !showToastIfExists(currentBackend!!.controlsToastNoArrows)) {
                    showToastIfExists(currentBackend!!.controlsToast)
                }
            }
        }
        dismissProgress()
        gameView.rebuildBitmap()
        menu?.let { onPrepareOptionsMenu(it) }
        save()
    }

    private fun showToastIfExists(@StringRes reminderId: Int): Boolean {
        if (reminderId == 0) {
            return false
        }
        Toast.makeText(this@GamePlay, reminderId, Toast.LENGTH_LONG).show()
        return true
    }

    private fun refreshPresets(currentParams: String) {
        currentType = -1
        gameTypesMenu = gameEngine.presets
        gameTypesById.clear()
        populateGameTypesById(gameTypesMenu, currentParams)
    }

    private fun populateGameTypesById(menuEntries: Array<MenuEntry>, currentParams: String) {
        for (entry in menuEntries) {
            entry.params?.let {
                gameTypesById[entry.id] = it
                if (orientGameType(currentBackend, currentParams)
                        == orientGameType(currentBackend, it)) {
                    currentType = entry.id
                }
            }
            entry.submenu?.let {
                populateGameTypesById(it, currentParams)
            }
        }
    }

    private fun checkSize(uri: Uri) {
        val cursor =
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null, null)
        cursor?.use {
            if (!it.moveToFirst()) return
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            // A provider isn't required to expose SIZE: getColumnIndex
            // then returns -1, and isNull(-1) throws an exception type
            // the call site doesn't catch. Unknown size is fine — the
            // read itself is capped as a backstop.
            if (sizeIndex < 0 || it.isNull(sizeIndex)) return
            require(it.getLong(sizeIndex) <= MAX_SAVE_SIZE) { getString(R.string.file_too_big) }
        }
    }

    private fun getLastParams(whichBackend: BackendName): String? =
        state.getString(LAST_PARAMS_PREFIX + whichBackend, null)?.let {
            orientGameType(whichBackend, it)
        }

    private fun stopGameGeneration() {
        generationInProgress?.run {
            cancel(true)
            generationInProgress = null
        }
    }

    override fun onPause() {
        handler.removeMessages(MsgType.TIMER.ordinal)
        pauseSolveTimer()
        if (progress == null) save()
        super.onPause()
    }

    /** True for a launch whose URI names only a puzzle (no params/game ID in it). */
    private fun isBackendOnlyLaunch(launch: Intent): Boolean {
        val u = launch.data ?: return false
        return OUR_SCHEME == u.scheme && !u.schemeSpecificPart.contains(":")
    }

    override fun onResume() {
        super.onResume()
        // Re-assert the board's TalkBack label: a recreated activity
        // restores its game without replaying startGame, which is the
        // path that normally sets it.
        gameView.updateBoardLabel(currentBackend)
        resumeSolveTimer()
        appStartIntentOnResume?.let { launch ->
            appStartIntentOnResume = null
            if (restoredInstance && !inDailyLaunch(launch) && !isBackendOnlyLaunch(launch)
                && resumeSavedGame()
            ) {
                // This instance is a recreate (e.g. a theme flip mid-game),
                // not a fresh launch. Replaying a board-specific intent
                // (params URI, saved-game extra, content URI) would generate
                // or reload a fresh board and lose the one in progress;
                // save() already wrote that board to the save slot on pause,
                // so resume it instead. Daily and backend-only launches
                // replay safely (attempt slot / save slot) and keep the
                // normal path above.
                Log.d(TAG, "recreate restore; resumed saved game instead of replaying launch")
            } else {
                onNewIntent(launch)
            }
        }
    }

    private fun inDailyLaunch(launch: Intent): Boolean =
        launch.getStringExtra(EXTRA_DAILY_DATE) != null

    override fun onDestroy() {
        stopGameGeneration()
        gameGenerator.onDestroy()
        gameEngine.onDestroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(f: Boolean) {
        if (f && gameWantsTimer && currentBackend != null && !handler.hasMessages(MsgType.TIMER.ordinal)) {
            gameEngine.resetTimerBaseline()
            handler.sendMessageDelayed(handler.obtainMessage(MsgType.TIMER.ordinal), TIMER_INTERVAL.toLong())
        }
    }

    private fun setSwapLR(swap: Boolean) {
        if (!currentBackend!!.swapLRNatively) swapLROn = swap
        currentBackend!!.putSwapLR(this, swap)
        buttons.swapLR.value = swap
    }

    fun sendKey(p: PointF, k: Int) {
        sendKey(p.x.roundToInt(), p.y.roundToInt(), k)
    }

    @JvmOverloads
    fun sendKey(x: Int, y: Int, k: Int, isRepeat: Boolean = false) {
        if (progress != null || currentBackend == null) return
        if (k == GameView.UI_UNDO && undoIsLoadGame) {
            if (!isRepeat) {
                toastFirstFewTimes(this, state, UNDO_NEW_GAME_SEEN, 3, R.string.undo_new_game_toast)
                val launchUndo = undoingOrRedoingNewGame(undoToGame!!)
                redoToGame = saveToString()
                startGame(launchUndo)
            }
            return
        }
        if (k == GameView.UI_REDO && redoIsLoadGame) {
            if (!isRepeat) {
                toastFirstFewTimes(this, state, REDO_NEW_GAME_SEEN, 3, R.string.redo_new_game_toast)
                val launchRedo = undoingOrRedoingNewGame(redoToGame!!)
                redoToGame = null
                startGame(launchRedo, true)
            }
            return
        }
        maybeStartSolveTimer()
        gameEngine.keyEvent(x, y, maybeSwapMouse(k))
        gameView.onBoardMayHaveChanged()
        if (GameView.CURSOR_KEYS.contains(k)
            || (currentBackend === INERTIA && k == '\n'.code)
            || (currentBackend === UNTANGLE && k == ' '.code)) {
            gameView.ensureCursorVisible(gameEngine.cursorLocation)
        }
        if (k == 'M'.code && currentBackend!!.isLatin) {
            toastFirstFewTimes(
                this, state, LATIN_M_UNDO_SEEN, 3,
                R.string.latin_m_undo_toast
            )
        }
        gameView.requestFocus()
        onBackPressedCallback.isEnabled = true
        handler.removeMessages(MsgType.BACK_TIME_ELAPSED.ordinal)
        handler.sendMessageDelayed(
            handler.obtainMessage(MsgType.BACK_TIME_ELAPSED.ordinal),
            PREVENT_BACK_AFTER_KEY_PRESS_MILLIS.toLong()
        )
    }

    /**
     * Whether left/right mouse buttons are currently swapped on
     * their way to the engine. GameView's Pattern mode planner
     * reads this live so it can pre-invert its planned buttons
     * and land the intended one after [maybeSwapMouse] runs.
     */
    fun isSwapLROn(): Boolean = swapLROn

    private fun maybeSwapMouse(k: Int): Int {
        if (swapLROn && k >= GameView.FIRST_MOUSE && k <= GameView.LAST_MOUSE) {
            val whichButton: Int = (k - GameView.FIRST_MOUSE) % 3
            if (whichButton == 0) {
                return k + 2 // left; send right
            } else if (whichButton == 2) {
                return k - 2 // right; send left
            }
        }
        return k
    }

    private fun setKeyboardVisibility(whichBackend: BackendName?, c: Configuration) {
        val newArrowMode = computeArrowMode(whichBackend)
        val shouldHaveSwap =
            lastArrowMode === ArrowMode.ARROWS_LEFT_RIGHT_CLICK || whichBackend === PALISADE || whichBackend === NET
        val maybeSwapLRKey = if (shouldHaveSwap) SWAP_L_R_KEY.toString() else ""
        val newKeys =
            if (shouldShowFullSoftKeyboard(c)) filterKeys(newArrowMode) + maybeSwapLRKey + maybeUndoRedo else maybeSwapLRKey + maybeUndoRedo
        val swap = whichBackend?.getSwapLR(this) ?: false
        if (whichBackend?.swapLRNatively != true) swapLROn = swap
        with (buttons) {
            backend.value = whichBackend ?: UNTANGLE
            keys.value = newKeys
            arrowMode.value = newArrowMode
            swapLR.value = swap
            disableCharacterIcons.value = disableCharacterIcons(whichBackend)
            undoEnabled.value = this@GamePlay.undoEnabled
            redoEnabled.value = this@GamePlay.redoEnabled
        }
    }

    private fun disableCharacterIcons(whichBackend: BackendName?): String {
        if (whichBackend !== UNDEAD) return ""
        val undeadPrefs = getPrefs(this, whichBackend)
        return if (undeadPrefs?.split("\n".toRegex())?.contains("monsters=letters") == true) "GVZ" else ""
    }

    private fun computeArrowMode(whichBackend: BackendName?): ArrowMode {
        val arrowPref = prefs.getBoolean(
            getArrowKeysPrefName(whichBackend, resources.configuration),
            getArrowKeysDefault(whichBackend, resources)
        )
        return if (arrowPref) lastArrowMode else ArrowMode.NO_ARROWS
    }

    private fun filterKeys(arrowMode: ArrowMode): String {
        var filtered = lastKeys
        startingBackend?.let {
            if (it === BRIDGES && !prefs.getBoolean(BRIDGES_SHOW_H_KEY, false)
                    || it === UNEQUAL && !prefs.getBoolean(UNEQUAL_SHOW_H_KEY, false)) {
                filtered = filtered.replace("H", "")
            }
            if (it.isLatin && !prefs.getBoolean(LATIN_SHOW_M_KEY, true)) {
                filtered = filtered.replace("M", "")
            }
        }
        if (arrowMode.hasArrows()) {
            filtered = lastKeysIfArrows + filtered
        } else if (filtered.length == 1 && filtered[0] == '\b') {
            filtered = ""
        }
        return filtered
    }

    private fun setStatusBarVisibility(visible: Boolean) {
        if (!visible) statusBar.text = ""
        mainLayout.updateViewLayout(statusBar, statusBar.layoutParams.apply {
            height = if (visible) RelativeLayout.LayoutParams.WRAP_CONTENT else 0
        })
        gameView.requestApplyInsets()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val statusBarText = statusBar.text
        val statusBarHeight = statusBar.layoutParams.height
        inflateContent()
        statusBar.text = statusBarText
        setStatusBarVisibility(statusBarHeight != 0)
        gameEngine.setViewCallbacks(gameView)
        setKeyboardVisibility(startingBackend, newConfig)
        refreshColours()
        val isNight = isNight(newConfig)
        if (isNight != _wasNight) {
            _wasNight = isNight
            statusBar.setTextColor(
                ResourcesCompat.getColor(resources, R.color.status_bar_text, theme)
            )
            statusBar.setBackgroundColor(
                ResourcesCompat.getColor(resources, R.color.game_background, theme)
            )
        }
        rethinkActionBarCapacity()
        invalidateOptionsMenu() // for orientation of presets in type menu
    }

    /** ActionBar's capacity (width) has probably changed, so work around
     * [Android issue 36933746](https://issuetracker.google.com/issues/36933746)
     * (invalidateOptionsMenu() does not help here)  */
    private fun rethinkActionBarCapacity() {
        menu?.apply {
            val dm = resources.displayMetrics
            val screenWidthDIP = (dm.widthPixels.toDouble() / dm.density).roundToInt()
            val state =
                if (screenWidthDIP >= 480) (SHOW_AS_ACTION_ALWAYS or SHOW_AS_ACTION_WITH_TEXT) else SHOW_AS_ACTION_ALWAYS
            for (i in listOf(R.id.game_menu, R.id.type_menu, R.id.help_menu)) {
                findItem(i).setShowAsAction(state)
            }
            val undoRedoKbd = prefs.getBoolean(UNDO_REDO_KBD_KEY, UNDO_REDO_KBD_DEFAULT)
            val undoItem = findItem(R.id.undo)
            val redoItem = findItem(R.id.redo)
            undoItem.isVisible = !undoRedoKbd
            redoItem.isVisible = !undoRedoKbd
            if (!undoRedoKbd) {
                undoItem.setShowAsAction(state)
                redoItem.setShowAsAction(state)
                updateUndoRedoEnabled()
            }
            // emulator at 598 dip looks bad with title+undo; GT-N7100 at 640dip looks good
            supportActionBar?.setDisplayShowTitleEnabled(screenWidthDIP > 620 || undoRedoKbd)
        }
    }

    private fun messageBox(title: String, msg: String?, returnToChooser: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setOnCancelListener(if (returnToChooser) DialogInterface.OnCancelListener { startChooserAndFinish() } else null)
            .show()
    }

    @UsedByJNI
    override fun completed() {
        handler.sendMessageDelayed(handler.obtainMessage(MsgType.COMPLETED.ordinal), 0)
    }

    @UsedByJNI
    override fun inertiaFollow(isSolved: Boolean) {
        buttons.hidePrimary.value = !isSolved && currentBackend === INERTIA
    }

    /** Re-shows the completion dialog for the solved puzzle; null when there is none to show. */
    private var reshowCompletion: (() -> Unit)? = null

    private fun completedInternal() {
        // Stats record on a board's first completion only. The engine
        // reports completion on every transition into the solved state, so
        // undoing the last move and solving again re-enters this function;
        // ungated, the solve count and total time below would record the
        // same solve twice. (The best time escapes by luck: the first
        // final time sticks, so the re-run's "is it better?" check fails.)
        // The completion dialog further down still shows on every solve —
        // only the recording is once-per-board, matching the daily's
        // "first completion wins" rule.
        val firstCompletion = !everCompleted
        everCompleted = true
        stopSolveTimer()
        // TalkBack: announce the solve. The completion dialog can be
        // turned off in Settings (leaving only a toast), and a
        // screen-reader user otherwise gets no signal the board is
        // solved. Fires on every completion, like the dialog does.
        currentBackend?.let { backend ->
            // Respect "Show solve timer": a player who hid the clock
            // doesn't want the time read aloud either (Fernando,
            // 2026-10-29, from the Claude roadmap review).
            val timePart = if (prefs.getBoolean(SHOW_SOLVE_TIMER_KEY, true)) {
                solveFinalMillis?.let { " — ${formatSolveTime(it)}" } ?: ""
            } else ""
            window.decorView.announceForAccessibility(
                "${backend.displayName}: ${getString(R.string.COMPLETED)}$timePart"
            )
        }
        // Record a best time only for legit solves: a Show-solution solve
        // would always "win" in a few seconds and corrupt the record, and
        // a restarted run would do the same (restart, finish a known
        // board in seconds). Leaving and resuming is fine.
        // (No record when the timer never started, e.g. a completed game
        // reloaded from a save.)
        val final = solveFinalMillis
        if (firstCompletion && !usedShowSolution && !usedRestart && final != null) {
            bestTimeKey()?.let { key ->
                val best = state.getLong(key, -1)
                if (best < 0 || final < best) {
                    state.edit { putLong(key, final) }
                    lastSolveSetNewBest = true
                }
            }
        }
        // Per-puzzle totals: every legit solve counts, whatever the
        // preset (normal, daily, or playlist). Same guards as bests, so
        // Show-solution and restarted runs never inflate them. These
        // counters start with this build — the app never tracked solves
        // or total time before, so there is nothing to backfill from.
        val backend = currentBackend
        if (firstCompletion && !usedShowSolution && !usedRestart && final != null && backend != null) {
            state.edit {
                val countKey = SOLVE_COUNT_PREFIX + backend
                putLong(countKey, state.getLong(countKey, 0) + 1)
                val timeKey = TOTAL_TIME_PREFIX + backend
                putLong(timeKey, state.getLong(timeKey, 0) + final)
            }
        }
        // Daily puzzle: remember today's completion (first completion wins).
        // Show-solution solves never record: the daily hides the solve item
        // entirely, and this guard covers any other path to a solved board.
        if (firstCompletion && !usedShowSolution) {
            dailyModeDate?.let { date ->
                solveFinalMillis?.let { final ->
                    DailyPuzzle.recordCompletion(
                        this, date, DailyPuzzle.Mode.fromString(dailyMode), final
                    )
                }
            }
        }
        if (inPlaylistMode() && playlistIndex == playlistGames.size - 1) {
            reshowCompletion = ::showPlaylistCompleteDialog
            showPlaylistCompleteDialog()
            return
        }
        val copyStatusBar = currentBackend in setOf(MINES, FLOOD, SAMEGAME)
        val titleText = if (copyStatusBar) statusBar.text else getString(R.string.COMPLETED)
        if (!prefs.getBoolean(COMPLETED_PROMPT_KEY, true)) {
            Toast.makeText(this@GamePlay, titleText, Toast.LENGTH_SHORT).show()
            return
        }
        reshowCompletion = ::showCompletedDialog
        showCompletedDialog()
    }

    /**
     * Share-my-solve: builds the result text (emoji and plain
     * variants — see ShareSolve) and lets the player pick which to
     * send to the share sheet. The streak is read AFTER the
     * completion was recorded, so a daily's number includes today.
     */
    private fun shareSolve() {
        val backend = currentBackend ?: return
        val final = solveFinalMillis ?: return
        val daily = inDailyMode()
        val streak = if (daily) {
            DailyPuzzle.streak(this, DailyPuzzle.Mode.fromString(dailyMode))
        } else {
            null
        }
        val presetTitle = try {
            GameEngineImpl.getPresetsForBackend(backend)
                .firstOrNull { it.params == gameEngine.currentParams }?.title
        } catch (e: Exception) {
            null
        }
        val (emoji, plain) = ShareSolve.build(
            puzzleName = backend.displayName,
            sizeLabel = ShareSolve.sizeLabel(gameEngine.currentParams, presetTitle),
            timeText = formatSolveTime(final),
            newBest = lastSolveSetNewBest,
            dailyDate = if (daily) dailyModeDate else null,
            streak = streak,
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.share_result_title)
            .setItems(arrayOf(emoji, plain)) { _, which ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, if (which == 0) emoji else plain)
                }
                startActivity(Intent.createChooser(send, getString(R.string.share_result_title)))
            }
            .show()
    }

    /** The per-puzzle "COMPLETED!" dialog. Safe to call again to re-show it after dismissal. */
    private fun showCompletedDialog() {
        val copyStatusBar = currentBackend in setOf(MINES, FLOOD, SAMEGAME)
        val titleText = if (copyStatusBar) statusBar.text else getString(R.string.COMPLETED)
        val d = Dialog(this, R.style.Dialog_Completed)
        val lp = d.window!!.attributes
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        d.window!!.attributes = lp
        val dialogBinding = CompletedDialogBinding.inflate(LayoutInflater.from(d.context))
        d.setContentView(dialogBinding.root)
        dialogBinding.completedTitle.text = if (prefs.getBoolean(SHOW_SOLVE_TIMER_KEY, true)) {
            solveFinalMillis?.let { final ->
                "$titleText · ${formatSolveTime(final)}"
            } ?: titleText
        } else {
            titleText
        }
        // The tell: only a solve that actually set a new best for its
        // preset gets the line (first-ever best counts). Show-solution
        // and restarted runs never set the flag, and it shows even with
        // the timer display off — bests record underneath either way.
        dialogBinding.completedNewBest.visibility =
            if (lastSolveSetNewBest) View.VISIBLE else View.GONE
        // An accidental tap outside must not lose the dialog (and the Next button in playlists).
        d.setCanceledOnTouchOutside(false)
        darkenTopDrawable(dialogBinding.newGame)
        dialogBinding.newGame.setOnClickListener {
            d.dismiss()
            startNewGame()
        }
        darkenTopDrawable(dialogBinding.typeMenu)
        dialogBinding.typeMenu.setOnClickListener {
            d.dismiss()
            if (hackForSubmenus == null) openOptionsMenu()
            hackForSubmenus!!.performIdentifierAction(R.id.type_menu, 0)
        }
        val style = prefs.getString(CHOOSER_STYLE_KEY, "list")
        val useGrid = style == "grid"
        dialogBinding.otherGame.setCompoundDrawablesWithIntrinsicBounds(
            0,
            if (useGrid) R.drawable.ic_action_view_as_grid else R.drawable.ic_action_view_as_list,
            0,
            0
        )
        darkenTopDrawable(dialogBinding.otherGame)
        dialogBinding.otherGame.setOnClickListener {
            d.dismiss()
            // In a playlist, "Other game" exits the playlist back to the playlists list.
            if (inPlaylistMode()) finish() else exitToOrigin()
        }
        // Share-my-solve: only when this completion has a recorded
        // time (a completed board reloaded from a save has none).
        dialogBinding.shareResult.visibility =
            if (solveFinalMillis != null) View.VISIBLE else View.GONE
        darkenTopDrawable(dialogBinding.shareResult)
        dialogBinding.shareResult.setOnClickListener { shareSolve() }
        if (inPlaylistMode()) {
            dialogBinding.nextGame.visibility = View.VISIBLE
            darkenTopDrawable(dialogBinding.nextGame)
            dialogBinding.nextGame.setOnClickListener {
                d.dismiss()
                advancePlaylist()
            }
        }
        d.show()
    }

    /**
     * Writes the current playlist position back into the activity's intent,
     * so a recreate (e.g. a theme flip) resumes the playlist where it is
     * instead of replaying the original launch at index 0.
     */
    private fun syncPlaylistIntent() {
        val backend = playlistGames.getOrNull(playlistIndex)?.let { byLowerCase(it) } ?: return
        intent.putExtra(EXTRA_PLAYLIST_INDEX, playlistIndex)
        intent.data = Uri.fromParts(OUR_SCHEME, backend.toString(), null)
        setIntent(intent)
        // Keep the playlist's saved run at the same position, so the
        // Playlists screen can offer to continue from here after a leave.
        playlistName?.let {
            PlaylistStore.saveSession(this, it, playlistGames, playlistIndex)
        }
    }

    /** Starts the next puzzle in the playlist; the solve timer resets via startGame. */
    private fun advancePlaylist() {
        val next = playlistIndex + 1
        if (next !in playlistGames.indices) return
        val backend = byLowerCase(playlistGames[next]) ?: return
        playlistIndex = next
        syncPlaylistIntent()
        startGame(toGenerateFromChooser(backend))
    }

    private fun showPlaylistCompleteDialog() {
        val name = playlistName ?: getString(R.string.playlists)
        val time = if (prefs.getBoolean(SHOW_SOLVE_TIMER_KEY, true)) {
            solveFinalMillis?.let { "\n" + getString(R.string.playlist_final_time, formatSolveTime(it)) } ?: ""
        } else {
            ""
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.playlist_complete)
            .setMessage(getString(R.string.playlist_complete_message, name) + time)
            .setPositiveButton(R.string.play_again) { _, _ ->
                val first = playlistGames.firstOrNull()?.let { byLowerCase(it) } ?: return@setPositiveButton
                playlistIndex = 0
                syncPlaylistIntent()
                startGame(toGenerateFromChooser(first))
            }
            .setNegativeButton(R.string.done) { _, _ ->
                // The run is finished; the Playlists screen stops offering it.
                playlistName?.let {
                    PlaylistStore.clearSession(this@GamePlay, it, playlistGames)
                }
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun darkenTopDrawable(b: Button) {
        val drawable = DrawableCompat.wrap(b.compoundDrawables[1].mutate())
        DrawableCompat.setTint(drawable, Color.BLACK)
        b.setCompoundDrawables(null, drawable, null, null)
    }

    @UsedByJNI
    override fun setStatus(status: String) {
        // Strip the engine's [M:SS] timer prefix (see ENGINE_TIMER_PREFIX):
        // it feeds both the status bar and the completion dialogs that
        // copy the status text, so stripping here hides it everywhere.
        val clean = ENGINE_TIMER_PREFIX.replaceFirst(status, "")
        statusBar.apply {
            text = clean.ifEmpty { " " } // Ensure consistent height
            importantForAccessibility =
                if (clean.isEmpty()) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    @UsedByJNI
    override fun requestTimer(on: Boolean) {
        if (gameWantsTimer && on) return
        gameWantsTimer = on
        if (on) handler.sendMessageDelayed(handler.obtainMessage(MsgType.TIMER.ordinal), TIMER_INTERVAL.toLong())
        else handler.removeMessages(MsgType.TIMER.ordinal)
    }

    override fun customDialogError(error: String?) {
        dismissProgress()
        messageBox(getString(R.string.Error), error, false)
    }

    private var lastArrowMode = ArrowMode.NO_ARROWS

    private fun setKeys(result: KeysResult?) {
        if (result == null) return
        lastArrowMode = result.arrowMode ?: ArrowMode.ARROWS_LEFT_RIGHT_CLICK
        lastKeys = result.keys ?: ""
        lastKeysIfArrows = result.keysIfArrows ?: ""
        // Guess allows digits, but we don't want them in the virtual keyboard because they're already
        // on screen as the colours (if labels are enabled).
        val addDigits = if (startingBackend === GUESS) "1234567890" else ""
        gameView.setHardwareKeys(lastKeys + lastKeysIfArrows + addDigits)
        setKeyboardVisibility(startingBackend, resources.configuration)
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return
        if (key == null) return
        val configuration = resources.configuration
        // = already started
        when (key) {
            currentBackend?.preferencesName -> {
                gameEngine.loadPrefs(this)
                if (currentBackend === PALISADE) {
                    // will also call setKeyboardVisibility
                    setKeys(gameEngine.requestKeys(PALISADE, gameEngine.currentParams, isPalisadeFullCursor()))
                } else {
                    // might have changed swapLR, or pictures vs letters in Undead
                    setKeyboardVisibility(startingBackend, configuration)
                }
                gameViewResized() // just for redraw
            }
            getArrowKeysPrefName(currentBackend, configuration) -> {
                setKeyboardVisibility(startingBackend, configuration)
                gameEngine.setCursorVisibility(computeArrowMode(startingBackend).hasArrows())
                gameViewResized() // cheat - we just want a redraw in case size unchanged
            }
            FULLSCREEN_KEY -> applyFullscreen()
            STAY_AWAKE_KEY -> applyStayAwake()
            SHOW_SOLVE_TIMER_KEY -> applySolveTimerVisibility()
            SHOW_BEST_TIME_KEY -> updateSolveTimerDisplay()
            LIMIT_DPI_KEY -> applyLimitDPI(true)
            ORIENTATION_KEY -> applyOrientation()
            UNDO_REDO_KBD_KEY -> applyUndoRedoKbd()
            BRIDGES_SHOW_H_KEY, UNEQUAL_SHOW_H_KEY, LATIN_SHOW_M_KEY -> applyKeyboardFilters()
            LONG_PRESS_TIMEOUT -> applyLongPressTimeout()
            MOUSE_LONG_PRESS_KEY -> applyMouseLongPress()
            MOUSE_BACK_KEY -> applyMouseBackKey()
        }
    }

    private fun applyLongPressTimeout() {
        gameView.longPressTimeout = prefs.getInt(LONG_PRESS_TIMEOUT, ViewConfiguration.getLongPressTimeout())
    }

    private fun applyMouseLongPress() {
        val pref = prefs.getString(MOUSE_LONG_PRESS_KEY, "auto")
        gameView.alwaysLongPress = "always" == pref
        gameView.hasRightMouse = "never" == pref
    }

    private fun applyMouseBackKey() {
        gameView.mouseBackSupport = prefs.getBoolean(MOUSE_BACK_KEY, true)
    }

    private fun applyLimitDPI(alreadyStarted: Boolean) {
        val pref = prefs.getString(LIMIT_DPI_KEY, "auto")
        gameView.limitDpi =
            if ("auto" == pref) LIMIT_AUTO else if ("off" == pref) LIMIT_OFF else LIMIT_ON
        if (alreadyStarted) {
            gameView.rebuildBitmap()
        }
    }

    private fun applyFullscreen() {
        WindowInsetsControllerCompat(window, mainLayout).apply {
            if (prefs.getBoolean(FULLSCREEN_KEY, true)) {
                hide(systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(systemBars())
            }
        }
    }

    private fun applyStayAwake() {
        if (prefs.getBoolean(STAY_AWAKE_KEY, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity") // This is only done at the user's explicit request
    private fun applyOrientation() {
        val orientationPref = prefs.getString(ORIENTATION_KEY, "unspecified")
        requestedOrientation = when (orientationPref) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun refreshColours() {
        gameView.night = isNight(resources.configuration)
        currentBackend?.let {
            gameView.refreshColours(it, gameEngine.colours)
            gameView.clear()
            gameViewResized() // cheat - we just want a redraw
        }
    }

    private fun applyUndoRedoKbd() {
        val undoRedoKbd = prefs.getBoolean(UNDO_REDO_KBD_KEY, UNDO_REDO_KBD_DEFAULT)
        val wantKbd = if (undoRedoKbd) "UR" else ""
        if (wantKbd != maybeUndoRedo) {
            maybeUndoRedo = wantKbd
            startingBackend?.let {
                setKeyboardVisibility(it, resources.configuration)
            }
        }
        rethinkActionBarCapacity()
    }

    private fun applyKeyboardFilters() {
        setKeyboardVisibility(startingBackend, resources.configuration)
    }

    @UsedByJNI
    override fun changedState(canUndo: Boolean, canRedo: Boolean) {
        undoEnabled = canUndo || undoToGame != null
        undoIsLoadGame = !canUndo && undoToGame != null
        redoEnabled = canRedo || redoToGame != null
        redoIsLoadGame = !canRedo && redoToGame != null
        buttons.undoEnabled.value = undoEnabled
        buttons.redoEnabled.value = redoEnabled
        menu?.apply {
            findItem(R.id.undo)?.apply {
                isEnabled = undoEnabled
                setIcon(if (undoEnabled) R.drawable.ic_action_undo else R.drawable.ic_action_undo_disabled)
            }
            findItem(R.id.redo)?.apply {
                isEnabled = redoEnabled
                setIcon(if (redoEnabled) R.drawable.ic_action_redo else R.drawable.ic_action_redo_disabled)
            }
        }
    }

    @UsedByJNI
    override fun purgingStates() {
        redoToGame = null
    }

    @UsedByJNI
    override fun allowFlash(): Boolean {
        return prefs.getBoolean(VICTORY_FLASH_KEY, true)
    }

    @VisibleForTesting // specifically the screenshots test
    fun getGameView(): GameView {
        return _binding.gameView
    }

    companion object {
        private const val TAG = "GamePlay"
        const val OUR_SCHEME = "sgtpuzzles"
        const val MIME_TYPE = "text/prs.sgtatham.puzzles"
        /** Intent extras carrying playlist play-mode state from PlaylistsActivity. */
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
        const val EXTRA_PLAYLIST_GAMES = "playlist_games"
        const val EXTRA_PLAYLIST_INDEX = "playlist_index"
        const val EXTRA_PLAYLIST_FRESH = "playlist_fresh"
        /** Intent extra carrying the daily puzzle date ("2026-10-21"); also puts GamePlay in daily mode. */
        const val EXTRA_DAILY_DATE = "daily_date"
        /** Intent extra carrying the daily mode ("easy"/"hard"); only meaningful with EXTRA_DAILY_DATE. */
        const val EXTRA_DAILY_MODE = "daily_mode"
        /** Intent extra carrying the daily mode's preset params; defaults to the backend default. */
        const val EXTRA_DAILY_PARAMS = "daily_params"
        /**
         * Intent extra naming the screen that launched this game, so the
         * completion dialog's "Other game" exit returns there instead of
         * always landing on the plain puzzle list. Absent = the chooser.
         */
        const val EXTRA_ORIGIN = "launch_origin"
        /** Launched from StatsActivity (calendar day or per-puzzle preset). */
        const val ORIGIN_STATS = "stats"
        /** Launched from the chooser's Daily screen. */
        const val ORIGIN_DAILY = "daily"
        private val DIMENSIONS = Pattern.compile("(\\d+)( ?)x\\2(\\d+)(.*)")
        /**
         * Storage key for a puzzle preset's best time, shared with the
         * stats screen's per-puzzle tab, which looks bests up by key.
         * Any W×H dimensions are sorted ascending first: the app flips
         * w/h on rotate, so without this the portrait and landscape
         * orientations of the same preset would keep separate records.
         */
        fun bestTimeKeyFor(backend: BackendName, params: String): String =
            BEST_TIME_PREFIX + backend + "_" + normalizeBestKeyParams(params)

        /**
         * Storage key for a preset's favorite (starred) flag, in the
         * same state store and with the same backend + normalised
         * params identity as [bestTimeKeyFor] — so favorites, bests
         * and the stats screen always agree on which preset a record
         * belongs to, nested presets included. A preset is a favorite
         * iff this key holds Boolean true; un-starring removes it.
         */
        fun favoriteKeyFor(backend: BackendName, params: String): String =
            FAVORITE_PRESET_PREFIX + backend + "_" + normalizeBestKeyParams(params)

        private fun normalizeBestKeyParams(params: String): String {
            val matcher = DIMENSIONS.matcher(params)
            if (!matcher.matches()) return params
            val a = matcher.group(1)!!.toInt()
            val b = matcher.group(3)!!.toInt()
            if (a <= b) return params
            val sep = matcher.group(2)!!
            return "$b$sep" + "x" + sep + "$a" + matcher.group(4)!!
        }

        const val MAX_SAVE_SIZE: Long = 1000000 // 1MB; we only have 16MB of heap
        private const val TIMER_INTERVAL = 20
        private const val SOLVE_TIMER_INTERVAL = 500
        /**
         * The engine's own timer prefix on timed games' status text
         * (Simon's midend_rewrite_statusbar: "[M:SS] ", running from
         * board load). It disagrees with our solve timer (first move)
         * and confuses users, so setStatus strips it.
         */
        private val ENGINE_TIMER_PREFIX = Regex("^\\[\\d+:\\d{2}] ")
        private const val PREVENT_BACK_AFTER_KEY_PRESS_MILLIS = 600

        /** Whether to show data-entry keys, as opposed to undo/redo/swap-L-R which are always shown.
         * We show data-entry if we either don't have a real hardware keyboard (we usually don't),
         * or we're on the Android SDK's emulator, which has the host's keyboard, but showing the full
         * keyboard is useful for UI development and screenshots.  */
        private fun shouldShowFullSoftKeyboard(c: Configuration): Boolean {
            return c.hardKeyboardHidden != HARDKEYBOARDHIDDEN_NO || c.keyboard == KEYBOARD_NOKEYS || isProbablyEmulator
        }

        private val isProbablyEmulator: Boolean
            get() = Build.MODEL.startsWith("sdk_") || Build.MODEL.startsWith("Android SDK")

        fun getArrowKeysPrefName(whichBackend: BackendName?, c: Configuration): String =
            (whichBackend.toString() + ARROW_KEYS_KEY_SUFFIX
                    + if (hasDpadOrTrackball(c)) "WithDpad" else "")

        fun getArrowKeysDefault(whichBackend: BackendName?, resources: Resources): Boolean =
            if (hasDpadOrTrackball(resources.configuration) && !isProbablyEmulator) false else whichBackend?.isArrowsVisibleByDefault ?: false

        private fun hasDpadOrTrackball(c: Configuration): Boolean =
            (c.navigation == NAVIGATION_DPAD || c.navigation == NAVIGATION_TRACKBALL) && c.navigationHidden != NAVIGATIONHIDDEN_YES

        init {
            System.loadLibrary("puzzles")
        }
    }
}
