package io.github.nandomango702.stpuzzlerelay

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.TextAppearanceSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.gridlayout.widget.GridLayout
import androidx.preference.PreferenceManager
import io.github.nandomango702.stpuzzlerelay.Utils.sendFeedbackDialog
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.BackendName.Companion.byLowerCase
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl
import io.github.nandomango702.stpuzzlerelay.backend.LOOPY
import io.github.nandomango702.stpuzzlerelay.backend.MINES
import io.github.nandomango702.stpuzzlerelay.backend.PATTERN
import io.github.nandomango702.stpuzzlerelay.backend.SOLO
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CHOOSER_STYLE_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SAVED_BACKEND
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SHOW_COMPLETION_BADGES_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.STATE_PREFS_NAME
import io.github.nandomango702.stpuzzlerelay.daily.DailyPuzzle
import io.github.nandomango702.stpuzzlerelay.databinding.ChooserBinding
import io.github.nandomango702.stpuzzlerelay.databinding.DialogDailyBinding
import io.github.nandomango702.stpuzzlerelay.databinding.ListItemBinding
import io.github.nandomango702.stpuzzlerelay.launch.GameGenerator.Companion.executableIsMissing
import io.github.nandomango702.stpuzzlerelay.playlist.PlaylistsActivity
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class GameChooser : ActivityWithLoadButton(), OnSharedPreferenceChangeListener {
    private lateinit var binding: ChooserBinding
    private lateinit var prefs: SharedPreferences
    private var useGrid = false
    private var showDescriptions = true
    private var showGridNames = false
    // Original gravities of the shared item layout, captured at build time
    // so switching back out of "Icons with names" restores them exactly.
    private var origHighlightGravity = Gravity.NO_GRAVITY
    private var origTextGravity = Gravity.NO_GRAVITY
    private val itemBindings: MutableMap<BackendName, ListItemBinding> = LinkedHashMap()
    private var menu: Menu? = null
    private var scrollToOnNextLayout: BackendName? = null
    private var resumeTime: Long = 0
    private var wasNight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (executableIsMissing(this)) {
            finish()
            return
        }
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        wasNight = NightModeHelper.isNight(resources.configuration)
        val state = getSharedPreferences(STATE_PREFS_NAME, MODE_PRIVATE)
        val oldCS = state.getString(CHOOSER_STYLE_KEY, null)
        if (oldCS != null) {  // migrate to somewhere more sensible
            prefs.edit { putString(CHOOSER_STYLE_KEY, oldCS) }
            state.edit { remove(CHOOSER_STYLE_KEY) }
        }
        val chooserStyle = prefs.getString(CHOOSER_STYLE_KEY, "list")
        useGrid = chooserStyle == "grid" || chooserStyle == "gridnames"
        showDescriptions = chooserStyle == "list"
        showGridNames = chooserStyle == "gridnames"
        // The chooser is the app's front door (and the backup-import
        // relaunch target): purge stale daily attempt boards here too,
        // not only when the daily dialog opens, so an old month's
        // attempts can never linger resumable — e.g. ones carried in
        // by an imported backup.
        DailyPuzzle.purgeStaleAttempts(this, DailyPuzzle.todayString())
        binding = ChooserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Actions live in the bottom bar (thumb reachability): it is the
        // support action bar, so the options menu renders there. The top bar
        // keeps only the title.
        binding.toolbar.title = getString(R.string.app_name)
        setSupportActionBar(binding.bottomBar)
        // The bottom bar carries icons only; the title stays up top.
        supportActionBar?.setDisplayShowTitleEnabled(false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, windowInsets ->
            // Edge-to-edge pattern: extend the bar behind the navigation bar
            // (padding) and grow its height by the same amount, so the icons
            // always keep their full action-bar height instead of being
            // crushed by the inset. Just adding padding inside the fixed
            // height squeezes the icons to nothing on 3-button nav.
            // Sides are padded too, so in landscape the icons never slide
            // under a side navigation bar.
            val insets = selfHandledInsets(windowInsets)
            v.updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
            v.updateLayoutParams { height = bottomBarHeightPx() + insets.bottom }
            // The top AppBarLayout's fitsSystemWindows only handles the
            // status bar, never the sides, so in landscape the title
            // slides under a side navigation bar. Pad the toolbar's sides
            // here, where the insets are known good.
            binding.toolbar.updatePadding(left = insets.left, right = insets.right)
            // Single source of truth for clearance: the list clears the bar
            // by the bar's full grown height (this runs after the table's own
            // inset listener, so it wins if the two ever disagree).
            applyTableInsets(windowInsets)
            WindowInsetsCompat.CONSUMED
        }
        buildViews()
        // Automatic update check (Fernando, 2026-10-31): default-on,
        // throttled to once a day inside UpdateChecker.
        UpdateChecker.maybeAutoCheck(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.table) { v, windowInsets ->
            applyTableInsets(windowInsets)
            rethinkColumns(false)
            WindowInsetsCompat.CONSUMED
        }
        binding.scrollView.viewTreeObserver.addOnGlobalLayoutListener {
            if (scrollToOnNextLayout != null) {
                val v: View = itemBindings[scrollToOnNextLayout]!!.root
                binding.scrollView.requestChildRectangleOnScreen(
                    v,
                    Rect(0, 0, v.width, v.height),
                    true
                )
                scrollToOnNextLayout = null
            }
        }
        enableTableAnimations()
    }

    override fun onResume() {
        super.onResume()
        resumeTime = System.nanoTime()
        var currentBackend: BackendName? = null
        val state = getSharedPreferences(STATE_PREFS_NAME, MODE_PRIVATE)
        if (state.contains(SAVED_BACKEND)) {
            currentBackend = byLowerCase(state.getString(SAVED_BACKEND, null))
        }
        BackendName.all.forEach {
            val isCurrent = it === currentBackend
            itemBindings[it]!!.root.isActivated = isCurrent
            if (isCurrent) {
                // wait until we know the size
                scrollToOnNextLayout = it
            }
        }
        updateDailyTitle()
        refreshBadges()
        if (dailyReopenRequested) {
            dailyReopenRequested = false
            openDaily()
        }
    }

    private fun enableTableAnimations() {
        binding.table.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
    }

    private fun buildViews() {
        itemBindings.clear()
        BackendName.all.forEach { backend ->
            with (ListItemBinding.inflate(layoutInflater)) {
                itemBindings[backend] = this
                if (itemBindings.size == 1) {
                    // First item: capture the layout's own gravities so
                    // leaving "Icons with names" restores them exactly.
                    origHighlightGravity = currentGameHighlight.gravity
                    origTextGravity = text.gravity
                }
                icon.setImageDrawable(backend.icon(this@GameChooser))
                setItemText(this, backend)
                text.visibility = if (useGrid && !showGridNames) GONE else VISIBLE
                // Icons-only tiles hide the name label, which would leave
                // the tile silent for TalkBack: name it on the row itself.
                // (In the labelled modes the visible text speaks for the
                // row, so no description is set there.)
                root.contentDescription =
                    if (useGrid && !showGridNames) backend.displayName else null
                applyItemLayout(this)
                // Star toggle button: list mode only. Tapping it stars/unstars
                // exactly like long-pressing the row; the button shows an
                // outline when unstarred and the filled star when starred.
                starToggle.tag = backend
                starToggle.visibility = if (useGrid) GONE else VISIBLE
                starToggle.setOnClickListener { toggleStarred(backend) }
                with (root) {
                    ignoreTouchAfterResume()
                    setOnClickListener {
                        startActivity(Intent(this@GameChooser, GamePlay::class.java).apply {
                            data = Uri.fromParts(GamePlay.OUR_SCHEME, backend.toString(), null)
                        })
                    }
                    setOnLongClickListener {
                        toggleStarred(backend)
                        true
                    }
                    isFocusable = true
                    layoutParams = mkLayoutParams()
                }
                binding.table.addView(root)
            }
        }
        rethinkColumns(true)
        refreshBadges()
    }

    /**
     * Sets a chooser row's text: just the puzzle name in "Names only" and
     * "Icons with names" modes, name plus description in "Show
     * descriptions" mode. The icon and star toggle are the same in both
     * list modes. Grid tiles get a smaller name style so labels fit on
     * one line under the icon.
     */
    private fun setItemText(binding: ListItemBinding, backend: BackendName) {
        binding.text.text = SpannableStringBuilder(backend.displayName).apply {
            setSpan(
                TextAppearanceSpan(this@GameChooser,
                    if (showGridNames) R.style.ChooserItemNameGrid
                    else R.style.ChooserItemName),
                0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (showDescriptions) {
                append(": ")
                append(getString(backend.description))
                // Common names, so players recognize them: Solo is
                // Sudoku, Pattern is Nonograms (Nintendo's Picross),
                // Loopy is Slitherlink.
                when (backend) {
                    SOLO -> append(" aka Sudoku")
                    PATTERN -> append(" aka Nonograms / Picross")
                    LOOPY -> append(" aka Slitherlink")
                    else -> {}
                }
                // TalkBack support tag (verified Describe level), so
                // the description states what Describe can do here.
                TalkBackSupport.tag(this@GameChooser, backend)?.let {
                    append(" · ").append(it)
                }
            }
        }
    }

    /**
     * Arranges a shared item view for the current chooser mode. List modes
     * keep the horizontal row (icon, name, star toggle). "Icons with
     * names" stacks the tile vertically: icon over a centered name label.
     * The star toggle stays hidden in both grid modes; long-press still
     * stars, and the small corner star badge still marks starred games.
     */
    private fun applyItemLayout(binding: ListItemBinding) {
        if (showGridNames) {
            binding.currentGameHighlight.orientation = LinearLayout.VERTICAL
            binding.currentGameHighlight.gravity = Gravity.CENTER_HORIZONTAL
            binding.text.updateLayoutParams<LinearLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            binding.text.gravity = Gravity.CENTER_HORIZONTAL
            // One line per label; a name too long for a forced-4 tile
            // shows an ellipsis instead of clipping mid-word.
            binding.text.maxLines = 1
            binding.text.ellipsize = TextUtils.TruncateAt.END
        } else {
            binding.currentGameHighlight.orientation = LinearLayout.HORIZONTAL
            binding.currentGameHighlight.gravity = origHighlightGravity
            binding.text.updateLayoutParams<LinearLayout.LayoutParams> {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 1f
            }
            binding.text.gravity = origTextGravity
            binding.text.maxLines = Int.MAX_VALUE
            binding.text.ellipsize = null
        }
    }

    @SuppressLint("ClickableViewAccessibility") // Does not define a new click mechanism
    private fun View.ignoreTouchAfterResume() {
        setOnTouchListener { _: View?, _: MotionEvent? -> System.nanoTime() - resumeTime < 300000000 }
    }

    private fun mkLayoutParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply { setGravity(Gravity.CENTER_HORIZONTAL) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isNight = NightModeHelper.isNight(resources.configuration)
        if (wasNight != isNight) {
            BackendName.all.forEach {
                itemBindings[it]!!.icon.setImageDrawable(it.icon(this))
            }
        }
        rethinkColumns(wasNight != isNight)
        // https://github.com/chrisboyle/sgtpuzzles/issues/227
        menu?.let {
            it.clear()
            onCreateOptionsMenu(it)
        }
        wasNight = isNight
    }

    /**
     * Tile width for "Icons with names": just wide enough for the longest
     * puzzle name at label size (Small, sans-serif-light, matching the
     * grid label style), plus the label/highlight side padding, rounded
     * up so the longest name never wraps. Icons then grow to fill the
     * tile instead of floating in a wide cell.
     */
    private fun gridNamesTileWidthDip(): Int {
        val dm = resources.displayMetrics
        val paint = Paint().apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, dm)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        var maxPx = 0f
        BackendName.all.forEach { maxPx = maxOf(maxPx, paint.measureText(it.displayName)) }
        // TextView side padding (8dip each) + highlight padding (2dip each).
        return ceil(maxPx / dm.density + 20).toInt()
    }

    private var columns = 0
    private var colWidthPx = 0
    private fun rethinkColumns(force: Boolean) {
        val dm = resources.displayMetrics
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(systemBars() or displayCutout() or ime())
        val screenWidthPx = (dm.widthPixels - (insets?.left ?: 0) - (insets?.right ?: 0)).toDouble()
        val colWidthDipNeeded = if (showGridNames) gridNamesTileWidthDip()
            else if (useGrid) 72 else 298
        val screenWidthDip = screenWidthPx / dm.density
        var newColumns = floor(screenWidthDip / colWidthDipNeeded).toInt().coerceAtLeast(1)
        val newColWidthPx = floor(screenWidthPx / newColumns).toInt()
        if (force || columns != newColumns || colWidthPx != newColWidthPx) {
            columns = newColumns
            colWidthPx = newColWidthPx
            val (starred, others) = BackendName.all.partition { isStarred(it) }
            val anyStarred = starred.isNotEmpty()
            binding.gamesStarred.visibility = if (anyStarred) VISIBLE else GONE
            var row = 0
            if (anyStarred) {
                setGridCells(binding.gamesStarred, 0, row++, columns)
                row = setViewsGridCells(row, starred.map {itemBindings[it]!!}, true)
            }
            binding.gamesOthers.setText(if (anyStarred) R.string.games_others else R.string.games_others_none_starred)
            setGridCells(binding.gamesOthers, 0, row++, columns)
            setViewsGridCells(row, others.map {itemBindings[it]!!}, false)
        }
    }

    private fun setGridCells(v: View, x: Int, y: Int, w: Int) {
        v.layoutParams = (v.layoutParams as GridLayout.LayoutParams).apply {
            width = colWidthPx * w
            columnSpec = GridLayout.spec(x, w, GridLayout.START)
            rowSpec = GridLayout.spec(y, 1, GridLayout.START)
            setGravity(if (useGrid && w == 1) Gravity.CENTER_HORIZONTAL else Gravity.START)
        }
    }

    private fun setViewsGridCells(
        startRow: Int,
        itemBindings: List<ListItemBinding>,
        starred: Boolean
    ): Int {
        var col = 0
        var row = startRow
        itemBindings.forEach {
            // The on-icon star overlay is grid modes only: in list
            // modes the star toggle at the row's right edge already
            // shows (and changes) the same state.
            it.star.visibility = if (starred && useGrid) VISIBLE else GONE
            // In list modes the row stretches to the full cell width: the
            // name text (weight 1) then pushes the star toggle to the right
            // edge, and the current-game highlight covers the whole row.
            // "Icons with names" also stretches, so the tile's highlight
            // covers the whole cell and the label centers across it.
            // Icons-only grid mode keeps wrap_content so icons stay
            // centered in cells.
            it.currentGameHighlight.updateLayoutParams {
                width = if (useGrid && !showGridNames) ViewGroup.LayoutParams.WRAP_CONTENT
                        else ViewGroup.LayoutParams.MATCH_PARENT
            }
            // In "Icons with names" the icon grows to fill the tile (cell
            // width minus a small margin) instead of floating at 64dip in
            // a wide cell. Other modes keep the fixed 64dip icon. This
            // runs on every layout pass, so switching modes restores the
            // icon size too.
            val iconSizePx = if (showGridNames) {
                colWidthPx - TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            } else {
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 64f, resources.displayMetrics).toInt()
            }
            it.icon.updateLayoutParams { width = iconSizePx; height = iconSizePx }
            (it.icon.parent as View).updateLayoutParams {
                width = iconSizePx; height = iconSizePx
            }
            // Star toggle button state follows the same starred state; the
            // group this row was placed in tells us what that state is.
            val backend = it.starToggle.tag as BackendName
            it.starToggle.setImageResource(
                if (starred) R.drawable.ic_star else R.drawable.ic_star_outline)
            it.starToggle.contentDescription = getString(
                if (starred) R.string.unstar_game else R.string.star_game,
                backend.displayName)
            if (col >= columns) {
                col = 0
                row++
            }
            setGridCells(it.root, col++, row, 1)
        }
        row++
        return row
    }

    private fun isStarred(game: BackendName): Boolean = Companion.isStarred(this, game)

    private fun toggleStarred(game: BackendName) {
        prefs.edit { putBoolean("starred_$game", !isStarred(game)) }
        rethinkColumns(true)
    }

    /** Short label for the daily menu item, e.g. "Oct 21". */
    private fun dailyDateLabel(): String =
        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            .format(java.util.Calendar.getInstance().time)

    /**
     * Opens today's daily picker: one card per mode (Easy = default preset,
     * Hard = last top-level preset), each showing its preset title, streak,
     * today's status, and Attempt / Practice buttons. A single card is
     * shown when the puzzle has only one preset. A bottom row holds About
     * (readme), Stats (calendar), then Cancel pinned right — Stats sits
     * in the row rather than under the Hard card so it reads as belonging
     * to the whole picker, not to Hard.
     */
    private fun openDaily() {
        val date = DailyPuzzle.todayString()
        // Attempts from earlier months can never resume (stats shows the
        // current month only); drop them so the stored boards don't pile up.
        DailyPuzzle.purgeStaleAttempts(this, date)
        val backend = DailyPuzzle.backendForDate(this, date)
        val cards = try {
            dailyModeCards(backend)
        } catch (e: Exception) {
            // Preset bridge failed: fall back to the plain easy daily.
            Log.d("Daily", "daily presets unavailable, falling back", e)
            launchDaily(date, DailyPuzzle.Mode.EASY, null)
            return
        }
        val dialogBinding = DialogDailyBinding.inflate(layoutInflater)
        dialogBinding.dailyTitle.text =
            getString(R.string.daily_today_title, backend.displayName)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        setupDailyCard(dialog, date, backend, cards[0],
            dialogBinding.dailyEasyMode, dialogBinding.dailyEasyPreset,
            dialogBinding.dailyEasyStreak, dialogBinding.dailyEasyStatus,
            dialogBinding.dailyEasyAttempt, dialogBinding.dailyEasyPractice)
        if (cards.size > 1) {
            setupDailyCard(dialog, date, backend, cards[1],
                dialogBinding.dailyHardMode, dialogBinding.dailyHardPreset,
                dialogBinding.dailyHardStreak, dialogBinding.dailyHardStatus,
                dialogBinding.dailyHardAttempt, dialogBinding.dailyHardPractice)
        } else {
            dialogBinding.dailyHardGroup.visibility = GONE
            dialogBinding.dailyDivider.visibility = GONE
        }
        dialogBinding.dailyAboutButton.setOnClickListener { showDailyAbout() }
        dialogBinding.dailyStatsButton.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, StatsActivity::class.java))
        }
        dialogBinding.dailyCancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** Readme for dailies: rules, features, and the calendar legend. */
    private fun showDailyAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.daily_about_title)
            .setMessage(R.string.daily_about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private data class DailyCard(
        val mode: DailyPuzzle.Mode,
        val presetTitle: String,
        val params: String
    )

    /** Easy + Hard cards for [backend]; a single Easy card when only one preset exists. */
    private fun dailyModeCards(backend: BackendName): List<DailyCard> {
        val topPresets = GameEngineImpl.getPresetsForBackend(backend)
            .filter { it.params != null }
        // Easy is the FIRST preset, not the engine default: some games
        // default to a mid-list variant (Magnets' engine default is its
        // third preset — 6x5 Tricky with strip clues), which made the
        // daily's "Easy" a harder board than the label promised
        // (Fernando, 2026-10-29). Preset-less games fall back to the
        // engine default.
        val easyEntry = topPresets.firstOrNull()
        val easyParams = easyEntry?.params ?: GameEngineImpl.getDefaultParams(backend)
        val easy = DailyCard(
            DailyPuzzle.Mode.EASY,
            easyEntry?.title ?: getString(R.string.daily_easy),
            easyParams
        )
        val hardParams = topPresets.lastOrNull()?.params
        if (hardParams == null || hardParams == easyParams) return listOf(easy)
        return listOf(
            easy,
            DailyCard(DailyPuzzle.Mode.HARD, topPresets.last().title, hardParams)
        )
    }

    private fun setupDailyCard(
        dialog: AlertDialog,
        date: String,
        backend: BackendName,
        card: DailyCard,
        modeView: TextView,
        presetView: TextView,
        streakView: TextView,
        statusView: TextView,
        attemptButton: Button,
        practiceButton: Button
    ) {
        modeView.text = getString(
            if (card.mode == DailyPuzzle.Mode.HARD) R.string.daily_hard else R.string.daily_easy
        )
        presetView.text = card.presetTitle
        val doneMillis = DailyPuzzle.completionTime(this, date, card.mode)
        val streak = DailyPuzzle.streak(this, card.mode)
        streakView.text = if (streak > 0) getString(R.string.daily_streak, streak)
        else getString(R.string.daily_no_streak)
        statusView.text = if (doneMillis != null)
            getString(R.string.daily_solved_in, DailyPuzzle.formatTime(doneMillis))
        else getString(R.string.daily_not_solved)
        attemptButton.text = if (doneMillis != null) getString(R.string.play_again)
        else if (DailyPuzzle.hasResumableAttempt(this, date, card.mode)) getString(R.string.daily_continue_attempt)
        else getString(R.string.daily_attempt)
        attemptButton.setOnClickListener {
            dialog.dismiss()
            launchDaily(date, card.mode, card.params)
        }
        practiceButton.text = getString(R.string.daily_practice)
        practiceButton.setOnClickListener {
            dialog.dismiss()
            launchPractice(backend, card.params)
        }
    }

    private fun launchDaily(date: String, mode: DailyPuzzle.Mode, params: String?) {
        val backend = DailyPuzzle.backendForDate(this, date)
        startActivity(Intent(this, GamePlay::class.java).apply {
            data = Uri.fromParts(GamePlay.OUR_SCHEME, backend.toString(), null)
            putExtra(GamePlay.EXTRA_DAILY_DATE, date)
            putExtra(GamePlay.EXTRA_DAILY_MODE, mode.name.lowercase())
            putExtra(GamePlay.EXTRA_ORIGIN, GamePlay.ORIGIN_DAILY)
            params?.let { putExtra(GamePlay.EXTRA_DAILY_PARAMS, it) }
        })
    }

    /**
     * Launches [params] as an ordinary game (not a daily): Show-solution
     * available, nothing recorded. Uses the params-URI launch path, exactly
     * as if the preset had been picked from the in-game type menu — except
     * the params are raw (no orientation tweak), so the practice game is
     * the exact shape the daily serves.
     */
    private fun launchPractice(backend: BackendName, params: String) {
        startActivity(Intent(this, GamePlay::class.java).apply {
            data = Uri.fromParts(GamePlay.OUR_SCHEME, "$backend:$params", null)
        })
    }

    /**
     * Launches a uniformly random puzzle from the given pool, with a
     * uniformly random preset of it — exactly as if the user had tapped
     * the puzzle and then picked a preset at random. The params-URI
     * launch always starts a fresh game (it never resumes an
     * in-progress one). The pool comes from the live enabled set
     * (BackendName.all), never a hardcoded count, so the full upstream
     * puzzle set slots in later.
     */
    private fun launchRandom(pool: Collection<BackendName>) {
        val backend = pool.random()
        val params = presetsFor(backend).randomOrNull()
        if (params != null) {
            launchRandomPreset(backend, params)
        } else {
            startActivity(Intent(this, GamePlay::class.java).apply {
                data = Uri.fromParts(GamePlay.OUR_SCHEME, backend.toString(), null)
            })
        }
    }

    /** Launches [backend] with [params] as an ordinary game (fresh, records normally). */
    private fun launchRandomPreset(backend: BackendName, params: String) {
        startActivity(Intent(this, GamePlay::class.java).apply {
            data = Uri.fromParts(GamePlay.OUR_SCHEME, "$backend:$params", null)
        })
    }

    /**
     * Playable presets for [backend]: its top-level preset params, or
     * the default params alone when it has no top-level presets
     * (single-preset puzzles). Same definition as the stats screen's
     * per-puzzle tab. Empty when the preset bridge fails. Cached:
     * preset lists never change at runtime.
     */
    private val presetsCache = mutableMapOf<BackendName, List<String>>()

    private fun presetsFor(backend: BackendName): List<String> =
        presetsCache.getOrPut(backend) {
            try {
                val presets = GameEngineImpl.getPresetsForBackend(backend)
                    .filter { it.params != null }.map { it.params!! }
                if (presets.isNotEmpty()) presets
                else listOfNotNull(GameEngineImpl.getDefaultParams(backend))
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Whether [backend]'s [params] preset has a legit best (i.e. is solved). */
    private fun hasBest(backend: BackendName, params: String): Boolean {
        val state = getSharedPreferences(STATE_PREFS_NAME, MODE_PRIVATE)
        return state.getLong(GamePlay.bestTimeKeyFor(backend, params), -1) >= 0
    }

    /**
     * Recomputes every tile's completion badge and its spoken state
     * from current bests. A badge shows once at least one of the
     * puzzle's presets is legitimately solved: a check ringed by a
     * progress arc, turning solid gold at 100% (every preset solved).
     * Runs on resume — bests change while playing, and the Settings
     * toggle for badges lives behind this screen too.
     */
    private fun refreshBadges() {
        val show = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(SHOW_COMPLETION_BADGES_KEY, true)
        val iconsOnly = useGrid && !showGridNames
        BackendName.all.forEach { backend ->
            val binding = itemBindings[backend] ?: return@forEach
            val presets = if (show) presetsFor(backend) else emptyList()
            val solved = presets.count { hasBest(backend, it) }
            val badged = show && presets.isNotEmpty() && solved > 0
            // The badge's home depends on the mode: on the icon in the
            // grid modes, beside the star toggle in the list modes
            // (where the right edge is the row's status column).
            val home = if (useGrid) binding.clearedBadge else binding.clearedBadgeList
            val away = if (useGrid) binding.clearedBadgeList else binding.clearedBadge
            away.visibility = GONE
            if (badged) {
                home.visibility = VISIBLE
                home.setImageDrawable(
                    ClearedBadgeDrawable(
                        solved.toFloat() / presets.size,
                        solved == presets.size,
                    )
                )
            } else {
                home.visibility = GONE
            }
            // Spoken state rides on the row's content description,
            // layered over the same baseline buildViews uses: the
            // plain name in icons-only mode, nothing (children speak)
            // elsewhere. With a badge, the base becomes the name — or
            // the row's full visible text in labelled modes — plus
            // the badge suffix. The TalkBack support tag layers on
            // the same way: appended to whatever is spoken here,
            // unless the base already contains it (in descriptions
            // mode the visible text carries the tag, and in that
            // mode an untagged-by-badge row stays description-less
            // so the visible text speaks it directly).
            val tag = TalkBackSupport.tag(this, backend)
            fun spoken(base: String): String =
                if (tag == null || base.contains(tag)) base else "$base. $tag"
            // In descriptions mode the visible text already ends with
            // the tag; strip it from the base so every mode speaks the
            // same order — name/description, badge state, then tag.
            var baseText =
                if (iconsOnly) backend.displayName else binding.text.text.toString()
            if (tag != null) baseText = baseText.replace(" · $tag", "")
            binding.root.contentDescription = when {
                badged && solved == presets.size -> spoken(
                    getString(R.string.badge_cleared_label, baseText),
                )
                badged -> spoken(
                    getString(
                        R.string.badge_progress_label,
                        baseText, solved, presets.size,
                    ),
                )
                iconsOnly -> spoken(backend.displayName)
                tag != null && !showDescriptions -> spoken(baseText)
                else -> null
            }
        }
    }

    /** [backend]'s presets with no best yet — the 100% chase pool. */
    private fun unsolvedPresets(backend: BackendName): List<String> =
        presetsFor(backend).filter { !hasBest(backend, it) }

    /**
     * Launches a random unsolved preset: a random puzzle from [pool]
     * that still has unsolved presets, then a random unsolved preset of
     * it. Puzzle-first, so each puzzle is equally likely. When nothing
     * in the pool is unsolved, [emptyMessage] is toasted instead.
     */
    private fun launchRandomUnsolved(pool: Collection<BackendName>, emptyMessage: Int) {
        val unsolvedByBackend = pool.associateWith { unsolvedPresets(it) }
            .filterValues { it.isNotEmpty() }
        if (unsolvedByBackend.isEmpty()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_LONG).show()
        } else {
            val (backend, presets) = unsolvedByBackend.entries.random()
            launchRandomPreset(backend, presets.random())
        }
    }

    private fun launchRandomFavorite() {
        val starred = BackendName.all.filter { isStarred(it) }
        if (starred.isEmpty()) {
            Toast.makeText(this, R.string.random_no_favorites, Toast.LENGTH_LONG).show()
        } else {
            launchRandom(starred)
        }
    }

    private fun launchRandomFavoriteUnsolved() {
        val starred = BackendName.all.filter { isStarred(it) }
        if (starred.isEmpty()) {
            Toast.makeText(this, R.string.random_no_favorites, Toast.LENGTH_LONG).show()
        } else {
            launchRandomUnsolved(starred, R.string.random_favorites_unsolved_none)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        this.menu = menu
        menuInflater.inflate(R.menu.chooser, menu)
        // The bottom bar always shows every action as an icon. Six icons
        // fit any phone width; a seventh (Stats, next to Daily) is being
        // tried — if it crowds the bar on narrower phones, Stats goes back
        // to living only in the daily dialog. Stats also stays in the
        // daily dialog, and the daily entry carries today's date.
        for (i in listOf(R.id.load, R.id.settings, R.id.playlists, R.id.daily, R.id.stats, R.id.random, R.id.help_menu)) {
            menu.findItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        // The random-picker and help submenus would open downward from the
        // bottom bar, into the navigation-bar area. Show them as upward-opening
        // popups instead. Consuming the tap here preempts the framework
        // submenu: MenuItemImpl.invoke() runs this click listener first and
        // never shows a submenu when it returns true.
        menu.findItem(R.id.random).setOnMenuItemClickListener {
            showUpwardPopup(R.id.random, R.menu.popup_random)
            true
        }
        menu.findItem(R.id.help_menu).setOnMenuItemClickListener {
            showUpwardPopup(R.id.help_menu, R.menu.popup_help)
            true
        }
        updateDailyTitle()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        updateDailyTitle()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (handleMenuItem(item)) {
            // https://issuetracker.google.com/issues/36994881
            invalidateOptionsMenu()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateDailyTitle() {
        // Label the daily entry with today's date, e.g. "Daily · Oct 21".
        menu?.findItem(R.id.daily)?.title =
            "${getString(R.string.daily)} · ${dailyDateLabel()}"
    }

    /**
     * Shows a small menu above the bottom bar, anchored to the tapped icon
     * (action-item views carry their menu item's ID, so findViewById locates
     * the exact icon). Gravity.TOP makes the popup open upward instead of
     * downward into the navigation-bar area.
     */
    private fun showUpwardPopup(anchorItemId: Int, menuRes: Int) {
        val anchor = binding.bottomBar.findViewById<View>(anchorItemId) ?: binding.bottomBar
        PopupMenu(this, anchor).apply {
            gravity = Gravity.TOP
            setForceShowIcon(true)
            menuInflater.inflate(menuRes, menu)
            setOnMenuItemClickListener { handleMenuItem(it) }
        }.show()
    }

    private fun bottomBarHeightPx(): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true))
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        else (56 * resources.displayMetrics.density).roundToInt()
    }

    /**
     * The system-bar insets this screen handles itself. The root layout does
     * not fitSystemWindows, so these listeners are the single source of truth
     * — but only when the window actually draws edge-to-edge. Edge-to-edge is
     * enforced on Android 15+ for this app's targetSdk; on older versions the
     * decor reserves the system-bar areas itself, so applying the insets
     * again would double-count them.
     */
    private fun selfHandledInsets(windowInsets: WindowInsetsCompat): Insets =
        if (Build.VERSION.SDK_INT >= 35) windowInsets.getInsets(systemBars())
        else Insets.NONE

    /**
     * Applies the table's side and bottom clearance. Sides use the same
     * inset union rethinkColumns measures the grid from (system bars,
     * cutout, keyboard), so the grid fills the table exactly: padding from
     * system bars alone leaves the table wider than the grid whenever a
     * cutout sits on a side edge, and the grid ends early with a dead gap
     * after the last column. Gated like selfHandledInsets: below API 35
     * the decor reserves the bar areas itself.
     */
    private fun applyTableInsets(windowInsets: WindowInsetsCompat) {
        val insets = selfHandledInsets(windowInsets)
        val sides = if (Build.VERSION.SDK_INT >= 35)
            windowInsets.getInsets(systemBars() or displayCutout() or ime())
        else Insets.NONE
        binding.table.updatePadding(
            left = sides.left,
            right = sides.right,
            // Clear the bar's grown height, plus the largest of the nav-bar /
            // cutout / keyboard insets at the bottom edge, plus a margin.
            bottom = bottomBarHeightPx() + max(
                insets.bottom,
                windowInsets.getInsets(displayCutout() or ime()).bottom
            ) + (8 * resources.displayMetrics.density).roundToInt()
        )
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        var ret = true
        when (item.itemId) {
            R.id.settings -> startActivity(Intent(this, PrefsActivity::class.java))
            R.id.playlists -> startActivity(Intent(this, PlaylistsActivity::class.java))
            R.id.daily -> openDaily()
            R.id.stats -> startActivity(Intent(this, StatsActivity::class.java))
            R.id.random_all -> launchRandom(BackendName.all)
            R.id.random_favorites -> launchRandomFavorite()
            R.id.random_all_unsolved -> launchRandomUnsolved(BackendName.all, R.string.random_all_unsolved_none)
            R.id.random_favorites_unsolved -> launchRandomFavoriteUnsolved()
            R.id.load -> loadGame()
            R.id.contents -> startActivity(Intent(this, HelpActivity::class.java).apply {
                putExtra(HelpActivity.TOPIC, "index")
            })
            R.id.feedback -> sendFeedbackDialog(this)
            else -> ret = false
        }
        return ret
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != CHOOSER_STYLE_KEY) return
        val style = prefs.getString(CHOOSER_STYLE_KEY, "list")
        val newGrid = style == "grid" || style == "gridnames"
        val newShowDescriptions = style == "list"
        val newShowGridNames = style == "gridnames"
        if (useGrid == newGrid && showDescriptions == newShowDescriptions
            && showGridNames == newShowGridNames) return
        useGrid = newGrid
        showDescriptions = newShowDescriptions
        showGridNames = newShowGridNames
        updateDailyTitle()
        itemBindings.forEach { (backend, binding) ->
            setItemText(binding, backend)
            binding.text.visibility = if (useGrid && !showGridNames) GONE else VISIBLE
            binding.starToggle.visibility = if (useGrid) GONE else VISIBLE
            applyItemLayout(binding)
        }
        rethinkColumns(true)
    }

    companion object {

        private val DEFAULT_STARRED = setOf(SOLO, PATTERN, MINES, LOOPY)

        /**
         * One-shot hand-off from GamePlay: a daily game exited via
         * "Other game" asks the chooser to re-open the Daily screen
         * when it resumes, so the player lands back where they
         * started instead of on the plain puzzle list.
         */
        @Volatile
        private var dailyReopenRequested = false

        fun requestDailyReopen() {
            dailyReopenRequested = true
        }

        /**
         * Whether [game] is starred, from the same default-prefs store the
         * chooser uses — so the stats screen's per-puzzle picker shows
         * exactly the main menu's Starred section.
         */
        fun isStarred(context: Context, game: BackendName): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("starred_$game", DEFAULT_STARRED.contains(game))

    }
}