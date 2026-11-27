package io.github.nandomango702.stpuzzlerelay

import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * The TalkBack virtual grid for Mines (Phase 2, tranche 3):
 * one accessibility stop per square over the board GameView
 * draws, so a blind player can swipe square to square and hear
 * each square's state from [MinesGridModel]. Solo's grid
 * (v0.6.0) and Pattern's (v0.7.0) are the templates — same lazy
 * capture, same dirty-on-move discipline — so the three grids
 * feel like one system, because they are.
 *
 * Input mirrors touch exactly, because it goes through the
 * same sends: a plain activation is the primary touch action
 * (uncover a covered square; on an uncovered number it clears
 * around, as a tap does), and each square also carries the
 * custom actions its state makes meaningful — Uncover and
 * Flag on a covered square, Unflag on a flagged one, Chord
 * on an uncovered number — all wired in GameView's
 * onMinesTalkBackActivated through the same engine input
 * path (and the same swap-L/R handling in GamePlay.sendKey)
 * that real touches use. Once the game is over the squares
 * stay focusable so the revealed board can be read, but they
 * offer no actions: the engine accepts no more moves.
 *
 * The model is captured lazily: GamePlay marks the grid dirty
 * after every key/touch the engine processes (through
 * GameView's onBoardMayHaveChanged), and the next
 * accessibility query re-captures — the same forced-redraw
 * capture Describe board reads, so labels can never drift
 * from the drawn board.
 */
class MinesTalkBackGrid(private val host: GameView) : ExploreByTouchHelper(host) {

    private var model: MinesGridModel? = null
    private var dirty = true

    /** The board may have changed; drop the model and make TalkBack re-query. */
    fun markDirty() {
        dirty = true
        invalidateRoot()
    }

    private fun ensureModel(): MinesGridModel? {
        if (dirty) {
            model = host.captureMinesGridModel()
            dirty = false
        }
        return model
    }

    /** The current model (freshly captured when dirty), for GamePlay's announcements. */
    internal fun currentModel(): MinesGridModel? = ensureModel()

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
        if (m.gameOver) return
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS)
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        for (action in m.offeredActions(row, col)) {
            when (action) {
                MinesGridAction.UNCOVER -> node.addAction(
                    AccessibilityActionCompat(
                        R.id.tb_action_uncover_cell,
                        host.context.getString(R.string.tb_mines_action_uncover),
                    )
                )
                MinesGridAction.FLAG -> node.addAction(
                    AccessibilityActionCompat(
                        R.id.tb_action_flag_cell,
                        host.context.getString(
                            if (m.stateAt(row, col) == MinesCellState.FLAGGED) {
                                R.string.tb_mines_action_unflag
                            } else {
                                R.string.tb_mines_action_flag
                            }
                        ),
                    )
                )
                MinesGridAction.CHORD -> node.addAction(
                    AccessibilityActionCompat(
                        R.id.tb_action_chord_cell,
                        host.context.getString(R.string.tb_mines_action_chord),
                    )
                )
            }
        }
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        args: Bundle?,
    ): Boolean {
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK,
            R.id.tb_action_uncover_cell,
            ->
                host.onMinesTalkBackActivated(virtualViewId, MinesGridAction.UNCOVER)
            R.id.tb_action_flag_cell ->
                host.onMinesTalkBackActivated(virtualViewId, MinesGridAction.FLAG)
            R.id.tb_action_chord_cell ->
                host.onMinesTalkBackActivated(virtualViewId, MinesGridAction.CHORD)
            else -> return false
        }
        return true
    }
}
