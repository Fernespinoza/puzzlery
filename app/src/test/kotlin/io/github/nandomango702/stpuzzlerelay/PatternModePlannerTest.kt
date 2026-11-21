package io.github.nandomango702.stpuzzlerelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirror of PatternModeHarness (the standalone main() used in
 * the offline environment) for the Pattern mode bar's pure
 * logic. The cycle expectations are pattern.c's own arithmetic
 * from interpret_move under STYLUS_BASED: LEFT sets
 * (currstate + 2) % 3, RIGHT (currstate + 1) % 3, in GRID_
 * numbering (EMPTY 0, FULL 1, UNKNOWN 2); MIDDLE always yields
 * GRID_UNKNOWN.
 */
class PatternModePlannerTest {

    private val p = PatternModePlanner
    private val l = PatternModePlanner.BUTTON_LEFT
    private val m = PatternModePlanner.BUTTON_MIDDLE
    private val r = PatternModePlanner.BUTTON_RIGHT
    private val e = PatternCellState.EMPTY
    private val f = PatternCellState.FULL
    private val u = PatternCellState.UNKNOWN

    @Test
    fun cycleTableMatchesPatternC() {
        assertEquals(e, p.cycleResult(l, f))
        assertEquals(u, p.cycleResult(l, e))
        assertEquals(f, p.cycleResult(l, u))
        assertEquals(f, p.cycleResult(r, e))
        assertEquals(u, p.cycleResult(r, f))
        assertEquals(e, p.cycleResult(r, u))
        assertEquals(u, p.cycleResult(m, e))
        assertEquals(u, p.cycleResult(m, f))
        assertEquals(u, p.cycleResult(m, u))
    }

    @Test
    fun tapPlansSetStateOrNoOp() {
        assertNull(p.tapButton(f, f))
        assertEquals(l, p.tapButton(f, e))
        assertEquals(m, p.tapButton(f, u))
        assertEquals(r, p.tapButton(e, f))
        assertNull(p.tapButton(e, e))
        assertEquals(m, p.tapButton(e, u))
        assertEquals(l, p.tapButton(u, f))
        assertEquals(r, p.tapButton(u, e))
        assertNull(p.tapButton(u, u))
        for (state in PatternCellState.values()) {
            for (target in PatternCellState.values()) {
                val btn = p.tapButton(state, target)
                assertTrue(
                    "tap plan $state -> $target is sound",
                    if (btn == null) state == target
                    else p.cycleResult(btn, state) == target
                )
            }
        }
    }

    @Test
    fun dragPlans() {
        for (state in PatternCellState.values()) {
            assertEquals(
                PatternModePlanner.DragPlan.Direct(m), p.dragPlan(state, u)
            )
        }
        assertEquals(PatternModePlanner.DragPlan.Direct(r), p.dragPlan(e, f))
        assertEquals(PatternModePlanner.DragPlan.Direct(l), p.dragPlan(u, f))
        assertEquals(PatternModePlanner.DragPlan.Direct(l), p.dragPlan(f, e))
        assertEquals(PatternModePlanner.DragPlan.Direct(r), p.dragPlan(u, e))
        assertEquals(
            PatternModePlanner.DragPlan.ResetThenPress(m, l), p.dragPlan(f, f)
        )
        assertEquals(
            PatternModePlanner.DragPlan.ResetThenPress(m, r), p.dragPlan(e, e)
        )
        for (state in PatternCellState.values()) {
            for (target in PatternCellState.values()) {
                val sound = when (val plan = p.dragPlan(state, target)) {
                    is PatternModePlanner.DragPlan.Direct ->
                        p.cycleResult(plan.button, state) == target
                    is PatternModePlanner.DragPlan.ResetThenPress ->
                        p.cycleResult(plan.resetButton, state) == u &&
                            p.cycleResult(plan.button, u) == target
                }
                assertTrue("drag plan $state -> $target is sound", sound)
            }
        }
    }

    @Test
    fun panCycleButtonDirection() {
        // Modebar2: uncolored -> black -> white -> uncolored.
        // pattern.c's LEFT press sets (currstate + 2) % 3 in
        // GRID_ numbering — exactly this direction; RIGHT is
        // the reverse cycle.
        val cycle = p.panCycleButton()
        assertEquals(l, cycle)
        assertEquals(f, p.cycleResult(cycle, u))
        assertEquals(e, p.cycleResult(cycle, f))
        assertEquals(u, p.cycleResult(cycle, e))
        val other = if (cycle == l) r else l
        assertTrue(
            !(p.cycleResult(other, u) == f && p.cycleResult(other, f) == e &&
                p.cycleResult(other, e) == u)
        )
        for (state in PatternCellState.values()) {
            assertEquals(
                state,
                p.cycleResult(cycle, p.cycleResult(cycle, p.cycleResult(cycle, state)))
            )
        }
    }

    @Test
    fun panHoldCycleButtonDirection() {
        // Modebar3 (Fernando's correction): the HOLD runs the
        // cycle in reverse — blank -> white, black -> blank,
        // white -> black. pattern.c's RIGHT press sets
        // (currstate + 1) % 3 — exactly this direction; the tap
        // keeps the forward LEFT cycle.
        val holdCycle = p.panHoldCycleButton()
        assertEquals(r, holdCycle)
        assertEquals(e, p.cycleResult(holdCycle, u))
        assertEquals(f, p.cycleResult(holdCycle, e))
        assertEquals(u, p.cycleResult(holdCycle, f))
        assertTrue(holdCycle != p.panCycleButton())
        val other = if (holdCycle == l) r else l
        assertTrue(
            !(p.cycleResult(other, u) == e && p.cycleResult(other, e) == f &&
                p.cycleResult(other, f) == u)
        )
        for (state in PatternCellState.values()) {
            assertEquals(
                state,
                p.cycleResult(
                    holdCycle,
                    p.cycleResult(holdCycle, p.cycleResult(holdCycle, state))
                )
            )
        }
    }

    @Test
    fun dragLineSnap() {
        // pattern.c's snap for FULL/EMPTY drags: |dx| > |dy|
        // keeps the start row, otherwise the start column; the
        // end is then clamped into the grid.
        assertEquals(Pair(7, 3), p.snapDragEnd(2, 3, 7, 4, 10, 10))
        assertEquals(Pair(2, 8), p.snapDragEnd(2, 3, 3, 8, 10, 10))
        assertEquals(Pair(2, 6), p.snapDragEnd(2, 3, 5, 6, 10, 10))
        assertEquals(Pair(2, 3), p.snapDragEnd(2, 3, 2, 3, 10, 10))
        assertEquals(Pair(9, 3), p.snapDragEnd(2, 3, 25, 4, 10, 10))
        assertEquals(Pair(2, 9), p.snapDragEnd(2, 3, 3, 25, 10, 10))
        assertEquals(Pair(0, 3), p.snapDragEnd(2, 3, -4, 3, 10, 10))
        assertEquals(Pair(1, 3), p.snapDragEnd(6, 3, 1, 4, 10, 10))
    }

    @Test
    fun swapLREncoding() {
        assertEquals(l, p.swapForSwapLR(l, false))
        assertEquals(r, p.swapForSwapLR(r, false))
        assertEquals(r, p.swapForSwapLR(l, true))
        assertEquals(l, p.swapForSwapLR(r, true))
        assertEquals(m, p.swapForSwapLR(m, true))
    }

    @Test
    fun cellStateExtraction() {
        val geom = PatternClueGeometry.compute(15, 15, 24, 528, 528)!!
        val originX = 30f
        val originY = 40f
        val ts = geom.tileSize
        fun stateRect(col: Int, row: Int, colour: Int): DescribeRecorder.DrawnRect {
            val xl = if (col % 5 == 0) 1 else 0
            val xr = if (col % 5 == 4 || col == geom.w - 1) 1 else 0
            val yt = if (row % 5 == 0) 1 else 0
            val yb = if (row % 5 == 4 || row == geom.h - 1) 1 else 0
            return DescribeRecorder.DrawnRect(
                (originX + geom.gridOriginX + col * ts + 1 + xl).toInt(),
                (originY + geom.gridOriginY + row * ts + 1 + yt).toInt(),
                ts - xl - xr - 1,
                ts - yt - yb - 1,
                colour,
            )
        }
        val rects = listOf(
            stateRect(0, 0, 2),
            stateRect(1, 0, 1),
            stateRect(2, 0, 4),
            stateRect(5, 3, 2),
            stateRect(14, 14, 1),
            DescribeRecorder.DrawnRect(
                (originX + geom.gridOriginX + 3 * ts).toInt(),
                (originY + geom.gridOriginY).toInt(), ts, ts, 5,
            ),
            DescribeRecorder.DrawnRect(
                (originX + geom.gridOriginX + 4 * ts + 2).toInt(),
                (originY + geom.gridOriginY + 2).toInt(), ts - 4, ts - 4, 0,
            ),
            DescribeRecorder.DrawnRect(
                (originX + geom.gridOriginX / 2 - 5).toInt(),
                (originY + geom.gridOriginY + 5).toInt(), 10, 10, 2,
            ),
            DescribeRecorder.DrawnRect(
                (originX + geom.gridOriginX + geom.w * ts + 20).toInt(),
                (originY + geom.gridOriginY + 5).toInt(), 10, 10, 2,
            ),
        )
        val states = p.cellStates(rects, geom, originX, originY)!!
        assertEquals(f, states[0])
        assertEquals(e, states[1])
        assertEquals(u, states[2])
        assertEquals(f, states[3 * geom.w + 5])
        assertEquals(e, states[14 * geom.w + 14])
        assertEquals(u, states[3])
        assertEquals(u, states[4])
        assertEquals(u, states[7 * geom.w + 7])
        assertNull(p.cellStates(emptyList(), geom, originX, originY))
        assertNull(p.cellStates(listOf(rects[5], rects[6]), geom, originX, originY))

        val clues = PatternClues.fromCapture(emptyList(), geom, originX, originY, rects)
        assertEquals(f, clues.cellStates!![3 * geom.w + 5])
        assertNull(
            PatternClues.fromCapture(emptyList(), geom, originX, originY).cellStates
        )
    }
}
