package io.github.nandomango702.stpuzzlerelay

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import com.google.android.material.tabs.TabLayout
import io.github.nandomango702.stpuzzlerelay.NightModeHelper.ActivityWithNightMode
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.DAILY_COMPLETE_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SHOW_COMPLETION_BADGES_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.STATE_PREFS_NAME
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.SOLVE_COUNT_PREFIX
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.TOTAL_TIME_PREFIX
import io.github.nandomango702.stpuzzlerelay.daily.DailyPuzzle
import io.github.nandomango702.stpuzzlerelay.daily.DailyPuzzle.Mode
import io.github.nandomango702.stpuzzlerelay.daily.DailyPuzzle.ModeState
import io.github.nandomango702.stpuzzlerelay.databinding.ActivityStatsBinding
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Stats, in two tabs. Daily: the current month as a calendar where each
 * cell's left half is Easy and right half is Hard (check = solved, arrow
 * = in progress, empty = not started, gold border = both solved), plus a
 * detail panel for the selected day with both modes' status and Play
 * buttons for today and for any in-progress day. Per puzzle: one puzzle's
 * presets, each with its best time or "Not completed".
 *
 * V1 simplification: cells always show both halves. A day whose puzzle has
 * a single preset (no Hard) shows an empty right half; the detail panel,
 * which does know the mode split, shows only the Easy row there.
 */
class StatsActivity : ActivityWithNightMode() {
    private lateinit var binding: ActivityStatsBinding
    private var shownYear = 0
    private var shownMonth = 0 // 0-based
    private var selectedDate: String? = null // "YYYY-MM-DD"
    private lateinit var selectedPuzzle: BackendName
    private var showingPerPuzzle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Needs a Context (the frozen daily rotation lives in prefs), so
        // this can't be a property initializer any more.
        selectedPuzzle = DailyPuzzle.backendForDate(this, DailyPuzzle.todayString())
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        // Edge-to-edge is enforced (targetSdk 36), so lift the content above
        // the system bars, following the pattern in PlaylistEditorActivity.
        // Without the top inset the toolbar slides under the status bar and
        // the title clips through it. Sides too: in landscape the nav bar
        // sits on a side edge, and without the side insets the content
        // runs underneath it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(systemBars() or displayCutout() or ime())
            v.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.stats_title)

        refreshHeader()
        val today = Calendar.getInstance()
        shownYear = today.get(Calendar.YEAR)
        shownMonth = today.get(Calendar.MONTH)
        buildWeekdayHeader()
        binding.statsAboutButton.setOnClickListener { showStatsAbout() }
        binding.statsPrevMonth.setOnClickListener { shiftMonth(-1) }
        binding.statsNextMonth.setOnClickListener { shiftMonth(1) }
        selectedDate = DailyPuzzle.todayString()
        buildMonth()
        showDetail(selectedDate!!)
        setupTabs()
        binding.statsPuzzlePicker.setOnClickListener { showPuzzlePicker() }
        buildPresetList()
    }

    override fun onResume() {
        super.onResume()
        // Playing from here may have changed today's states.
        refreshHeader()
        buildMonth()
        selectedDate?.let { showDetail(it) }
        if (showingPerPuzzle) buildPresetList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Explanation for the stats page: header, calendar legend, and rules. */
    private fun showStatsAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.stats_about_title)
            .setMessage(R.string.stats_about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupTabs() {
        val tabs = binding.statsTabs
        tabs.addTab(tabs.newTab().setText(R.string.stats_tab_daily))
        tabs.addTab(tabs.newTab().setText(R.string.stats_tab_per_puzzle))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showingPerPuzzle = tab.position == 1
                binding.statsDailyGroup.visibility =
                    if (showingPerPuzzle) View.GONE else View.VISIBLE
                binding.statsPerPuzzleGroup.visibility =
                    if (showingPerPuzzle) View.VISIBLE else View.GONE
                if (showingPerPuzzle) buildPresetList()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    /**
     * Puzzle picker: the same order as the main menu — the Starred
     * section first, then the rest (each in list order) — from the same
     * starred store, so the Starred section here IS the main menu's.
     * The note under the picker says so.
     */
    private fun showPuzzlePicker() {
        // Completion badges in the picker (Fernando, 2026-11-03):
        // the same badge the main menu shows — same fraction
        // (top-level presets with a legit best over all top-level
        // presets, the engine default standing in for preset-less
        // puzzles), same toggle, same spoken labels.
        val state = getSharedPreferences(STATE_PREFS_NAME, MODE_PRIVATE)
        val showBadges = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(SHOW_COMPLETION_BADGES_KEY, true)
        val badges = mutableMapOf<BackendName, Pair<Int, Int>>()
        if (showBadges) {
            for (backend in BackendName.all) {
                try {
                    val paramsList = GameEngineImpl.getPresetsForBackend(backend)
                        .filter { it.params != null }.map { it.params!! }
                        .ifEmpty {
                            listOfNotNull(GameEngineImpl.getDefaultParams(backend))
                        }
                    val solved = paramsList.count {
                        state.getLong(GamePlay.bestTimeKeyFor(backend, it), -1) >= 0
                    }
                    if (paramsList.isNotEmpty() && solved > 0) {
                        badges[backend] = solved to paramsList.size
                    }
                } catch (e: Exception) {
                    // A puzzle whose presets can't be read gets no badge.
                }
            }
        }
        val (starred, others) =
            BackendName.all.partition { GameChooser.isStarred(this, it) }
        val items = mutableListOf<PickerItem>()
        if (starred.isNotEmpty()) {
            items.add(PickerItem.Header(getString(R.string.stats_picker_starred)))
            starred.forEach { items.add(PickerItem.Game(it)) }
        }
        items.add(PickerItem.Header(getString(R.string.stats_picker_others)))
        others.forEach { items.add(PickerItem.Game(it)) }
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun isEnabled(position: Int) = items[position] is PickerItem.Game
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) =
                if (items[position] is PickerItem.Header) 0 else 1

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return when (val item = items[position]) {
                    is PickerItem.Header -> TextView(this@StatsActivity).apply {
                        text = item.text
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(dp(16), dp(12), dp(16), dp(4))
                    }
                    is PickerItem.Game -> LinearLayout(this@StatsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(16), dp(8), dp(16), dp(8))
                        addView(ImageView(this@StatsActivity).apply {
                            setImageDrawable(item.backend.icon(this@StatsActivity))
                            importantForAccessibility =
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            layoutParams =
                                LinearLayout.LayoutParams(dp(32), dp(32))
                        })
                        addView(TextView(this@StatsActivity).apply {
                            text = item.backend.displayName
                            textSize = 16f
                            setPadding(dp(12), 0, 0, 0)
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
                            )
                        })
                        badges[item.backend]?.let { (solved, total) ->
                            addView(ImageView(this@StatsActivity).apply {
                                setImageDrawable(
                                    ClearedBadgeDrawable(
                                        solved.toFloat() / total,
                                        solved == total,
                                    )
                                )
                                importantForAccessibility =
                                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                layoutParams =
                                    LinearLayout.LayoutParams(dp(24), dp(24))
                            })
                            contentDescription = if (solved == total) {
                                getString(
                                    R.string.badge_cleared_label,
                                    item.backend.displayName,
                                )
                            } else {
                                getString(
                                    R.string.badge_progress_label,
                                    item.backend.displayName, solved, total,
                                )
                            }
                        }
                    }
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.stats_picker_title)
            .setAdapter(adapter) { _, which ->
                val item = items[which]
                if (item is PickerItem.Game) {
                    selectedPuzzle = item.backend
                    buildPresetList()
                }
            }
            .show()
    }

    private sealed interface PickerItem {
        data class Header(val text: String) : PickerItem
        data class Game(val backend: BackendName) : PickerItem
    }

    /**
     * Per-puzzle tab: the selected puzzle's presets, each with its best
     * time or "Not completed". Completed = a legit best exists (daily
     * solves record one too; Show-solution and restarted runs never do).
     * Presets come from the same bridge as the daily picker; puzzles with
     * no top-level presets get a single Default row. Tapping a row
     * launches that preset as a fresh ordinary game.
     */
    private fun buildPresetList() {
        val list = binding.statsPresetList
        list.removeAllViews()
        binding.statsPuzzlePicker.text =
            getString(R.string.stats_puzzle_picker_label, selectedPuzzle.displayName)
        val state = getSharedPreferences(STATE_PREFS_NAME, MODE_PRIVATE)
        val solves = state.getLong(SOLVE_COUNT_PREFIX + selectedPuzzle, 0)
        val totalTime = state.getLong(TOTAL_TIME_PREFIX + selectedPuzzle, 0)
        var bestMillis: List<Long> = emptyList()
        val rows: List<Triple<String, String?, String?>> = try {
            val presets = GameEngineImpl.getPresetsForBackend(selectedPuzzle)
                .filter { it.params != null }
            if (presets.isEmpty()) {
                val defaultParams = GameEngineImpl.getDefaultParams(selectedPuzzle)
                bestMillis = listOfNotNull(defaultParams?.let { bestMillisFor(state, it) })
                listOf(
                    Triple(
                        getString(R.string.stats_preset_default),
                        defaultParams,
                        defaultParams?.let { bestFor(state, it) }
                    )
                )
            } else {
                bestMillis = presets.mapNotNull { bestMillisFor(state, it.params!!) }
                presets.map { Triple(it.title, it.params, bestFor(state, it.params!!)) }
            }
        } catch (e: Exception) {
            list.addView(TextView(this).apply {
                text = getString(R.string.stats_presets_unavailable)
                textSize = 16f
            })
            return
        }
        for ((title, params, best) in rows) list.addView(presetRow(title, params, best))
        // TalkBack support tag: what Describe board can do for this
        // puzzle, once its level has been verified on-device.
        TalkBackSupport.phrase(this, selectedPuzzle)?.let { phrase ->
            list.addView(TextView(this).apply {
                text = getString(R.string.stats_tb_support, phrase)
                textSize = 14f
                alpha = 0.7f
                setPadding(0, dp(16), 0, 0)
            })
        }
        val avgBest = if (bestMillis.isNotEmpty()) {
            DailyPuzzle.formatTime(bestMillis.average().toLong())
        } else {
            "—"
        }
        list.addView(
            summaryCard(
                avgBest = avgBest,
                solves = solves,
                cleared = rows.count { it.third != null },
                total = rows.size,
                timeSpent = formatTotalTime(totalTime)
            )
        )
    }

    /**
     * Compact total time for the per-puzzle header: seconds under a
     * minute, whole minutes under an hour, hours + minutes above that
     * ("42m", "2h 15m").
     */
    private fun formatTotalTime(millis: Long): String {
        val seconds = millis / 1000
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}m"
        return "${minutes / 60}h ${minutes % 60}m"
    }

    private fun bestFor(state: SharedPreferences, params: String): String? {
        val key = GamePlay.bestTimeKeyFor(selectedPuzzle, params)
        val best = state.getLong(key, -1)
        return if (best >= 0) DailyPuzzle.formatTime(best) else null
    }

    private fun bestMillisFor(state: SharedPreferences, params: String): Long? {
        val best = state.getLong(GamePlay.bestTimeKeyFor(selectedPuzzle, params), -1)
        return if (best >= 0) best else null
    }

    /** Resolves a theme attribute (e.g. the shared card surface) to a colour. */
    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /**
     * Summary card under the preset list: a 2x2 grid with the two time
     * stats on the top row (average best time across the cleared presets,
     * total time spent) and the two count stats below (total legit solves,
     * preset coverage). All of the per-puzzle numbers live here; the tab
     * shows none of them above the list.
     */
    private fun summaryCard(
        avgBest: String,
        solves: Long,
        cleared: Int,
        total: Int,
        timeSpent: String
    ): View {
        val pct = if (total > 0) cleared * 100 / total else 0
        val cells = listOf(
            getString(R.string.stats_summary_avg_best) to avgBest,
            getString(R.string.stats_summary_time) to timeSpent,
            getString(R.string.stats_summary_solves) to solves.toString(),
            getString(R.string.stats_summary_cleared) to
                getString(R.string.stats_summary_cleared_value, cleared, total, pct)
        )
        fun cell(label: String, value: String): View {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                addView(TextView(this@StatsActivity).apply {
                    text = label
                    textSize = 12f
                    alpha = 0.7f
                })
                addView(TextView(this@StatsActivity).apply {
                    text = value
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                })
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(themeColor(R.attr.chooserSectionBackground))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            addView(TextView(this@StatsActivity).apply {
                text = getString(R.string.stats_summary_title)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            })
            for (row in 0 until 2) {
                addView(LinearLayout(this@StatsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    if (row == 1) setPadding(0, dp(8), 0, 0)
                    addView(cell(cells[row * 2].first, cells[row * 2].second))
                    addView(cell(cells[row * 2 + 1].first, cells[row * 2 + 1].second))
                })
            }
        }
    }

    private fun presetRow(title: String, params: String?, best: String?): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            addView(TextView(this@StatsActivity).apply {
                text = title
                textSize = 16f
                // Keep a gap before the status text: a long title (e.g.
                // "6x6 Penrose (kite/dart) - Hard") otherwise fills its
                // weighted slot edge to edge and runs straight into
                // "Not completed".
                setPadding(0, 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            addView(TextView(this@StatsActivity).apply {
                text = best ?: getString(R.string.stats_preset_not_completed)
                textSize = 16f
                if (best == null) alpha = 0.6f
            })
            if (params != null) {
                isClickable = true
                isFocusable = true
                val tv = TypedValue()
                if (theme.resolveAttribute(
                        android.R.attr.selectableItemBackground, tv, true
                    )
                ) {
                    setBackgroundResource(tv.resourceId)
                }
                contentDescription =
                    getString(R.string.stats_preset_play, selectedPuzzle.displayName, title)
                setOnClickListener { launchPreset(params) }
                addView(TextView(this@StatsActivity).apply {
                    text = "›"
                    textSize = 16f
                    alpha = 0.5f
                    setPadding(dp(8), 0, 0, 0)
                })
            }
        }
    }

    /**
     * Launches [params] of the selected puzzle as a fresh ordinary game —
     * the same params-URI path as the chooser's random picker, so a solve
     * from here records bests and counters exactly like any other game.
     */
    private fun launchPreset(params: String) {
        startActivity(Intent(this, GamePlay::class.java).apply {
            data = Uri.fromParts(GamePlay.OUR_SCHEME, "$selectedPuzzle:$params", null)
            putExtra(GamePlay.EXTRA_ORIGIN, GamePlay.ORIGIN_STATS)
        })
    }

    private fun refreshHeader() {
        binding.statsEasySummary.text = getString(
            R.string.stats_mode_summary,
            getString(R.string.daily_easy),
            DailyPuzzle.streak(this, Mode.EASY),
            DailyPuzzle.longestStreak(this, Mode.EASY),
            DailyPuzzle.totalSolved(this, Mode.EASY)
        )
        binding.statsHardSummary.text = getString(
            R.string.stats_mode_summary,
            getString(R.string.daily_hard),
            DailyPuzzle.streak(this, Mode.HARD),
            DailyPuzzle.longestStreak(this, Mode.HARD),
            DailyPuzzle.totalSolved(this, Mode.HARD)
        )
    }

    private fun buildWeekdayHeader() {
        val symbols = DateFormatSymbols.getInstance().shortWeekdays // 1..7, [0] empty
        val first = Calendar.getInstance().firstDayOfWeek
        val header = binding.statsWeekdayHeader
        header.removeAllViews()
        for (i in 0 until 7) {
            val dow = (first - 1 + i) % 7 + 1
            header.addView(TextView(this).apply {
                text = symbols[dow]
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                layoutParams = equalColumnParams()
            })
        }
    }

    private fun buildMonth() {
        val cal = Calendar.getInstance()
        cal.set(shownYear, shownMonth, 1)
        val offset = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        binding.statsMonthTitle.text =
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        // Announce the new month to TalkBack when paging with the
        // arrows (focus stays on the arrow, so without a live region
        // the change is silent).
        binding.statsMonthTitle.accessibilityLiveRegion =
            View.ACCESSIBILITY_LIVE_REGION_POLITE
        val grid = binding.statsGrid
        grid.removeAllViews()
        repeat(offset) { grid.addView(Space(this).apply { layoutParams = equalColumnParams() }) }
        for (day in 1..daysInMonth) {
            // Locale.ROOT: must match DailyPuzzle's ASCII storage keys.
            val dateStr = String.format(Locale.ROOT, "%04d-%02d-%02d", shownYear, shownMonth + 1, day)
            grid.addView(dayCell(dateStr, day))
        }
        val trailing = (7 - (offset + daysInMonth) % 7) % 7
        repeat(trailing) { grid.addView(Space(this).apply { layoutParams = equalColumnParams() }) }
        updateMonthNav()
    }

    /** Pages the calendar one month back or forward, then re-renders. */
    private fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance()
        cal.set(shownYear, shownMonth, 1)
        cal.add(Calendar.MONTH, delta)
        shownYear = cal.get(Calendar.YEAR)
        shownMonth = cal.get(Calendar.MONTH)
        // The detail pane describes a day in the month just left;
        // drop it rather than leave a stale day under the new month.
        selectedDate = null
        binding.statsDetailGroup.visibility = View.GONE
        buildMonth()
    }

    /**
     * Month paging bounds: never into the future; backwards at most
     * to the earlier of the oldest daily record's month or twelve
     * months ago, so there is always somewhere to page to. Past
     * months are view-only in practice: their attempt boards were
     * cleaned up monthly, so no day in them can offer a resume.
     */
    private fun updateMonthNav() {
        val now = Calendar.getInstance()
        val curYear = now.get(Calendar.YEAR)
        val curMonth = now.get(Calendar.MONTH)
        binding.statsNextMonth.isEnabled =
            shownYear < curYear || (shownYear == curYear && shownMonth < curMonth)
        val floor = earliestPageableMonth()
        binding.statsPrevMonth.isEnabled =
            shownYear > floor.first || (shownYear == floor.first && shownMonth > floor.second)
    }

    /** (year, month) of the oldest month the calendar may page back to. */
    private fun earliestPageableMonth(): Pair<Int, Int> {
        var earliest: String? = null
        for (key in PreferenceManager.getDefaultSharedPreferences(this).all.keys) {
            if (!key.startsWith(DAILY_COMPLETE_PREFIX)) continue
            val date = key.takeLast(10)
            // Full shape check before the toInt() calls below: a
            // planted key (e.g. from a doctored backup) whose date
            // fields are not digits must be skipped, not crash.
            val wellFormed = date.length == 10 && date[4] == '-' && date[7] == '-' &&
                date.substring(0, 4).all { it.isDigit() } &&
                date.substring(5, 7).all { it.isDigit() } &&
                date.substring(8, 10).all { it.isDigit() }
            if (!wellFormed) continue
            val current = earliest
            if (current == null || date < current) {
                earliest = date
            }
        }
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -12)
        var floorYear = cal.get(Calendar.YEAR)
        var floorMonth = cal.get(Calendar.MONTH)
        if (earliest != null) {
            val y = earliest.substring(0, 4).toInt()
            val m = earliest.substring(5, 7).toInt() - 1
            if (y < floorYear || (y == floorYear && m < floorMonth)) {
                floorYear = y
                floorMonth = m
            }
        }
        return floorYear to floorMonth
    }

    private fun dayCell(dateStr: String, day: Int): View {
        val easy = DailyPuzzle.dayModeState(this, dateStr, Mode.EASY)
        val hard = DailyPuzzle.dayModeState(this, dateStr, Mode.HARD)
        val today = DailyPuzzle.todayString()
        val isToday = dateStr == today
        val isFuture = dateStr > today // ISO dates compare lexicographically
        val isSelected = dateStr == selectedDate
        val isPerfect = easy.state == ModeState.SOLVED && hard.state == ModeState.SOLVED
        val touched = easy.state != ModeState.NOT_STARTED ||
            hard.state != ModeState.NOT_STARTED
        val night = isNight()
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = cellBackground(isPerfect, touched, isSelected, night)
            isClickable = true
            isFocusable = true
        }
        val number = TextView(this).apply {
            text = day.toString()
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            // Today is a cyan ring on the number. Selected is a border on
            // the tile itself (see cellBackground), never a circle behind
            // the number, so the two can never collide with the number or
            // the status marks.
            background = if (isToday) ringBackground(if (night) TODAY_RING else TODAY_RING_DAY) else null
            // Night: numbers are always white — tiles are dark and the
            // page background is dark, so nothing ever inverts to black.
            // Day: dark numbers on the light tiles, default elsewhere.
            if (night) {
                setTextColor(Color.WHITE)
            } else if (touched) {
                // The number sits on the light tile: dark text, or it
                // washes out (light text on a light tile).
                setTextColor(DARK_TEXT)
            }
            if (isToday) setTypeface(null, Typeface.BOLD)
            if (!isToday && (isFuture || !touched)) {
                alpha = 0.4f
            }
        }
        val marks = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        marks.addView(markView(easy.state))
        marks.addView(markView(hard.state))
        cell.addView(number)
        cell.addView(marks)
        cell.contentDescription = describeDay(dateStr, easy, hard, isToday, isFuture)
        cell.setOnClickListener {
            selectedDate = dateStr
            buildMonth()
            showDetail(dateStr)
        }
        cell.layoutParams = equalColumnParams()
        return cell
    }

    private fun markView(state: ModeState): TextView {
        // Fixed width keeps the left = Easy / right = Hard positions stable
        // even when one half is empty.
        return TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            // Fixed height for both marks: the ✓ and ▶ glyphs have different
            // font metrics, so wrap-content heights put their centres on
            // different lines. Identical boxes share one centre line.
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(18))
            when (state) {
                ModeState.SOLVED -> {
                    text = "✓"
                    setTextColor(if (isNight()) CHECK_GREEN else CHECK_GREEN_DAY)
                }
                ModeState.IN_PROGRESS -> {
                    text = "▶"
                    setTextColor(ARROW_AMBER)
                }
                ModeState.NOT_STARTED -> text = ""
            }
        }
    }

    private fun describeDay(
        dateStr: String,
        easy: DailyPuzzle.DayModeState,
        hard: DailyPuzzle.DayModeState,
        isToday: Boolean,
        isFuture: Boolean
    ): String {
        val date = formatDateLong(dateStr)
        if (isFuture) return "$date, not yet available"
        val parts = StringBuilder(date)
        if (isToday) parts.append(", today")
        parts.append(", ${getString(R.string.daily_easy)} ${stateText(easy)}")
        parts.append(", ${getString(R.string.daily_hard)} ${stateText(hard)}")
        return parts.toString()
    }

    private fun stateText(state: DailyPuzzle.DayModeState): String {
        return when (state.state) {
            ModeState.SOLVED -> getString(
                R.string.stats_solved_in,
                DailyPuzzle.formatTime(state.solveMillis ?: 0L)
            )
            ModeState.IN_PROGRESS -> getString(R.string.stats_in_progress)
            ModeState.NOT_STARTED -> getString(R.string.stats_not_started)
        }
    }

    private fun showDetail(dateStr: String) {
        val backend = DailyPuzzle.backendForDate(this, dateStr)
        val isToday = dateStr == DailyPuzzle.todayString()
        binding.statsDetailDate.text = formatDateLong(dateStr)
        binding.statsDetailPuzzle.text = backend.displayName
        val modes = DailyPuzzle.modesFor(backend)
        val easyState = DailyPuzzle.dayModeState(this, dateStr, Mode.EASY)
        binding.statsDetailEasy.text =
            "${getString(R.string.daily_easy)} — ${stateText(easyState)}"
        val hardParams = modes.find { it.first == Mode.HARD }?.second
        if (hardParams != null) {
            val hardState = DailyPuzzle.dayModeState(this, dateStr, Mode.HARD)
            binding.statsDetailHard.text =
                "${getString(R.string.daily_hard)} — ${stateText(hardState)}"
            binding.statsDetailHard.visibility = View.VISIBLE
        } else {
            binding.statsDetailHard.visibility = View.GONE
        }
        // Play buttons: today always, plus any in-progress day in
        // the CURRENT month (a past attempt within this month
        // resumes its board; without a board it generates that
        // date's puzzle fresh). Solved / untouched past days stay
        // view-only. The month condition is structural, not just a
        // comment: past months are view-only even if an old attempt
        // record somehow exists (e.g. carried in by a backup
        // import) — the monthly purge is the cleaner, not the guard.
        val inCurrentMonth =
            dateStr.substring(0, 7) == DailyPuzzle.todayString().substring(0, 7)
        if (isToday || (inCurrentMonth && easyState.state == ModeState.IN_PROGRESS)) {
            binding.statsEasyPlay.visibility = View.VISIBLE
            binding.statsEasyPlay.text = playLabel(easyState)
            val easyParams = modes.find { it.first == Mode.EASY }?.second
            binding.statsEasyPlay.setOnClickListener {
                launchDaily(dateStr, Mode.EASY, easyParams)
            }
        } else {
            binding.statsEasyPlay.visibility = View.GONE
        }
        if (hardParams != null) {
            val hardState = DailyPuzzle.dayModeState(this, dateStr, Mode.HARD)
            if (isToday || (inCurrentMonth && hardState.state == ModeState.IN_PROGRESS)) {
                binding.statsHardPlay.visibility = View.VISIBLE
                binding.statsHardPlay.text = playLabel(hardState)
                binding.statsHardPlay.setOnClickListener {
                    launchDaily(dateStr, Mode.HARD, hardParams)
                }
            } else {
                binding.statsHardPlay.visibility = View.GONE
            }
        } else {
            binding.statsHardPlay.visibility = View.GONE
        }
        binding.statsDetailGroup.visibility = View.VISIBLE
    }

    private fun playLabel(state: DailyPuzzle.DayModeState): String {
        return when (state.state) {
            ModeState.SOLVED -> getString(R.string.play_again)
            ModeState.IN_PROGRESS -> getString(R.string.daily_continue_attempt)
            ModeState.NOT_STARTED -> getString(R.string.daily_attempt)
        }
    }

    private fun launchDaily(dateStr: String, mode: Mode, params: String?) {
        val backend = DailyPuzzle.backendForDate(this, dateStr)
        startActivity(Intent(this, GamePlay::class.java).apply {
            data = Uri.fromParts(GamePlay.OUR_SCHEME, backend.toString(), null)
            putExtra(GamePlay.EXTRA_DAILY_DATE, dateStr)
            putExtra(GamePlay.EXTRA_DAILY_MODE, mode.name.lowercase())
            putExtra(GamePlay.EXTRA_ORIGIN, GamePlay.ORIGIN_STATS)
            params?.let { putExtra(GamePlay.EXTRA_DAILY_PARAMS, it) }
        })
    }

    private fun formatDateLong(dateStr: String): String {
        // SimpleDateFormat.parse throws on unparseable input rather than
        // returning null, so fail soft to the raw string.
        val parsed = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
        } catch (_: Exception) {
            null
        }
        return if (parsed != null) {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(parsed)
        } else {
            dateStr
        }
    }

    private fun equalColumnParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
    }

    /**
     * Cell background: a shaded tile on touched days (untouched days stay
     * flat), a gold border on perfect days, and a border on the selected
     * day. The selected border is the tile's own edge — white at night,
     * near-black by day — never a circle behind the number, so it can't
     * collide with the number or the status marks. The border shares the
     * tile's exact bounds, so selecting a day never resizes its fill; on a
     * selected perfect day it draws over the gold edge.
     */
    private fun cellBackground(
        isPerfect: Boolean,
        touched: Boolean,
        isSelected: Boolean,
        night: Boolean
    ): Drawable {
        val tile = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(
                if (touched) {
                    ContextCompat.getColor(
                        this@StatsActivity, R.color.stats_touched_background
                    )
                } else {
                    Color.TRANSPARENT
                }
            )
            if (isPerfect) setStroke(dp(2), if (night) GOLD else GOLD_DAY)
        }
        if (!isSelected) return tile
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), if (night) Color.WHITE else SELECTED_DAY)
        }
        // Same bounds as the tile: the outline traces the fill's own box
        // instead of wrapping it from outside (the old 3dp inset shrank the
        // fill whenever a day was selected).
        return LayerDrawable(arrayOf(tile, border))
    }

    private fun ringBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), color)
        }
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val GOLD = Color.parseColor("#FFB300")
        private val GOLD_DAY = Color.parseColor("#D97706")
        private val CHECK_GREEN = Color.parseColor("#4CAF50")
        private val CHECK_GREEN_DAY = Color.parseColor("#16A34A")
        private val ARROW_AMBER = Color.parseColor("#FF9800")
        private val TODAY_RING = Color.parseColor("#06B6D4")
        private val TODAY_RING_DAY = Color.parseColor("#0284C7")
        private val SELECTED_DAY = Color.parseColor("#111827")
        private val DARK_TEXT = Color.parseColor("#212121")
    }
}
