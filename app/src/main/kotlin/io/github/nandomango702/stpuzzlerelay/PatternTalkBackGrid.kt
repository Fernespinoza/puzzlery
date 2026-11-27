package io.github.nandomango702.stpuzzlerelay

import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * The TalkBack virtual grid for Pattern (Phase 2, tranche 2):
 * one accessibility stop per cell over the board GameView draws,
 * so a blind player can swipe cell to cell and hear each cell's
 * state and its row and column clue runs from [PatternGridModel].
 * The Solo grid (v0.6.0) is the template — same lazy capture,
 * same dirty-on-move discipline — so the two grids feel like one
 * system, because they are.
 *
 * Input differs from Solo's, because Pattern's input does: there
 * is no digit pad. Activating a cell applies the mode bar's
 * current mode to it, exactly as a touch tap would (GameView's
 * onPatternTalkBackActivated plans it through the same
 * PatternModePlanner the touch path uses), and each cell also
 * carries three custom actions — Fill cell, Mark cell, Clear
 * cell — that set its state directly, whatever mode the bar is
 * in, so a TalkBack player is never slower than the mode bar
 * allows. Pan mode needs no action of its own here: the grid
 * itself is the navigation, and activation in Pan mode cycles
 * the cell, as a Pan tap does.
 *
 * The model is captured lazily: GamePlay marks the grid dirty
 * after every key/touch the engine processes (through GameView's
 * onBoardMayHaveChanged), and the next accessibility query
 * re-captures — the same clue capture the pinned strips and the
 * mode bar read, so labels can never drift from either.
 */
class PatternTalkBackGrid(private val host: GameView) : ExploreByTouchHelper(host) {

    private var model: PatternGridModel? = null
    private var dirty = true

    /** The board may have changed; drop the model and make TalkBack re-query. */
    fun markDirty() {
        dirty = true
        invalidateRoot()
    }

    private fun ensureModel(): PatternGridModel? {
        if (dirty) {
            model = host.capturePatternGridModel()
            dirty = false
        }
        return model
    }

    /** The current model (freshly captured when dirty), for GamePlay's announcements. */
    internal fun currentModel(): PatternGridModel? = ensureModel()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val m = ensureModel() ?: return INVALID_ID
        val g = host.viewToGame(PointF(x, y))
        return m.virtualIdAt(g.x, g.y) ?: INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val m = ensureModel() ?: return
        for (i in 0 until m.w * m.h) virtualViewIds.add(i)
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val m = ensureModel()
        if (m == null || virtualViewId !in 0 until m.w * m.h) {
            node.contentDescription = ""
            node.setBoundsInParent(Rect())
            return
        }
        val row = virtualViewId / m.w
        val col = virtualViewId % m.w
        node.contentDescription = m.label(host.context, row, col)
        node.setBoundsInParent(host.gameRectToViewRect(m.gameRect(row, col)))
        node.isFocusable = true
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS)
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        node.addAction(
            AccessibilityActionCompat(
                R.id.tb_action_fill_cell,
                host.context.getString(R.string.tb_pattern_action_fill),
            )
        )
        node.addAction(
            AccessibilityActionCompat(
                R.id.tb_action_mark_cell,
                host.context.getString(R.string.tb_pattern_action_mark),
            )
        )
        node.addAction(
            AccessibilityActionCompat(
                R.id.tb_action_clear_cell,
                host.context.getString(R.string.tb_pattern_action_clear),
            )
        )
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        args: Bundle?,
    ): Boolean {
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK ->
                host.onPatternTalkBackActivated(virtualViewId, null)
            R.id.tb_action_fill_cell ->
                host.onPatternTalkBackActivated(virtualViewId, PatternCellState.FULL)
            R.id.tb_action_mark_cell ->
                host.onPatternTalkBackActivated(virtualViewId, PatternCellState.EMPTY)
            R.id.tb_action_clear_cell ->
                host.onPatternTalkBackActivated(virtualViewId, PatternCellState.UNKNOWN)
            else -> return false
        }
        return true
    }
}
