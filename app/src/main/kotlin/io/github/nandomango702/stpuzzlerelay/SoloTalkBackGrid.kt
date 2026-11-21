package io.github.nandomango702.stpuzzlerelay

import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * The TalkBack virtual grid for Solo (Phase 2, tranche 1):
 * one accessibility stop per cell over the board GameView draws,
 * so a blind player can swipe cell to cell and hear each cell's
 * state — given, entered, pencil marks, empty — from
 * [SoloGridModel].
 *
 * Build 1 (2026-11-06, Fernando's split) was navigation and
 * labels only. Build 2 adds input: activating a cell selects it
 * through the engine's own tap path (see GameView's
 * onTalkBackCellActivated), and GamePlay's digit pad enters values into the
 * selection. Announcements and the selection/pencil mirror live
 * in GamePlay; this class stays the board's accessibility face.
 *
 * The model is captured lazily: GamePlay marks the grid dirty
 * after every key/touch the engine processes, and the next
 * accessibility query re-captures (one forced redraw through the
 * Describe recorder — the same machinery Describe board uses, so
 * labels can never drift from what Describe would say).
 */
class SoloTalkBackGrid(private val host: GameView) : ExploreByTouchHelper(host) {

    private var model: SoloGridModel? = null
    private var dirty = true

    /** The board may have changed; drop the model and make TalkBack re-query. */
    fun markDirty() {
        dirty = true
        invalidateRoot()
    }

    private fun ensureModel(): SoloGridModel? {
        if (dirty) {
            model = host.captureSoloGridModel()
            dirty = false
        }
        return model
    }

    /** The current model (freshly captured when dirty), for GamePlay's announcements. */
    internal fun currentModel(): SoloGridModel? = ensureModel()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val m = ensureModel() ?: return INVALID_ID
        val g = host.viewToGame(PointF(x, y))
        return m.virtualIdAt(g.x, g.y) ?: INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val m = ensureModel() ?: return
        for (i in 0 until m.cr * m.cr) virtualViewIds.add(i)
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val m = ensureModel()
        if (m == null || virtualViewId !in 0 until m.cr * m.cr) {
            node.contentDescription = ""
            node.setBoundsInParent(Rect())
            return
        }
        val row = virtualViewId / m.cr
        val col = virtualViewId % m.cr
        node.contentDescription = m.label(host.context, row, col)
        node.setBoundsInParent(host.gameRectToViewRect(m.gameRect(row, col)))
        node.isFocusable = true
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS)
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        args: Bundle?,
    ): Boolean {
        if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
            host.onTalkBackCellActivated(virtualViewId)
            return true
        }
        return false
    }
}
