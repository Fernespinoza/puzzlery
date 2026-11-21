package io.github.nandomango702.stpuzzlerelay

import kotlin.math.abs
import kotlin.math.floor

/**
 * Pattern's mode bar (v0.6.0): Fill / Mark / Erase / Pan.
 *
 * The Fill/Mark/Erase modes SET cell state; they never cycle.
 * Pan is the deliberate exception (modebar2, Fernando's
 * revision): its tap and its hold-paint stroke step cells
 * through the engine's own press cycle — the classic Pattern
 * touch behaviour, let through inside Pan mode only. Tap and
 * hold run the cycle in OPPOSITE directions (modebar3,
 * Fernando's correction): the tap steps forward (uncolored ->
 * black -> white -> uncolored) and the hold steps in reverse
 * (blank -> white, black -> blank, white -> black) — the
 * engine's classic LEFT/RIGHT pair, one button each. That is
 * not what
 * the engine's buttons natively do on Android: the Android build
 * defines STYLUS_BASED (jni/cmake/platforms/android.cmake), under
 * which pattern.c's interpret_move derives a press's drag state
 * by CYCLING the pressed cell — LEFT_BUTTON sends FULL -> EMPTY
 * -> UNKNOWN -> FULL, RIGHT_BUTTON the reverse cycle, and only
 * MIDDLE_BUTTON names an absolute state (GRID_UNKNOWN). The
 * state a press produces is then applied absolutely to the whole
 * drag region on release (one F/E/U rectangle move), so set-state
 * IS expressible through the public input surface — provided the
 * frontend knows each cell's current state and picks the button
 * whose cycle lands on the target. The states come from the draw
 * stream: pattern.c's grid_square fills every cell with
 * COL_FULL / COL_EMPTY / COL_UNKNOWN, and the recorder capture
 * the pinned clues already force on every move records those
 * fills (see [cellStates]).
 *
 * One gap remains, and it is the engine's, not the planner's: a
 * Fill or Mark drag that STARTS on a cell already holding the
 * target state cannot be set up by any single press (both cycles
 * move away from the current state, MIDDLE goes to UNKNOWN).
 * [PatternModePlanner.dragPlan] covers it with a reset tap —
 * MIDDLE press+release on the start cell (setting it UNKNOWN),
 * then the press whose cycle from UNKNOWN reaches the target.
 * That stroke costs two engine moves instead of one; undo
 * behaves accordingly (see GameView's stroke handling).
 */

/** A Pattern cell's state; ordinals mirror pattern.c's GRID_ values. */
enum class PatternCellState {
    EMPTY, FULL, UNKNOWN;

    companion object {
        /** pattern.c colour index -> state (COL_EMPTY 1, COL_FULL 2, COL_UNKNOWN 4). */
        fun fromColour(colour: Int): PatternCellState? = when (colour) {
            1 -> EMPTY
            2 -> FULL
            4 -> UNKNOWN
            else -> null
        }
    }
}

/** The mode bar's modes. [target] is the state a tap sets. */
enum class PatternMode(val target: PatternCellState) {
    /** Tap/drag paints cells filled (GRID_FULL). */
    FILL(PatternCellState.FULL),

    /** Tap/drag paints cells marked off as known-empty (GRID_EMPTY). */
    MARK(PatternCellState.EMPTY),

    /** Tap/drag returns cells to undecided (GRID_UNKNOWN). */
    ERASE(PatternCellState.UNKNOWN),

    /**
     * The navigation mode (modebar2 semantics, modebar3
     * directions): a quick one-finger drag pans the board, a
     * tap CYCLES the cell one step forward (uncolored -> black
     * -> white -> uncolored), and a hold-then-drag paints a
     * line stepping in the REVERSE direction from the start
     * cell's state (blank -> white, black -> blank, white ->
     * black). None of that consults [target] — GameView
     * branches on PAN before any set-state planning — so the
     * FULL here is a placeholder, kept only because every mode
     * carries one.
     */
    PAN(PatternCellState.FULL),
}

object PatternModePlanner {

    // The engine's button protocol (puzzles.h values; GameView's
    // companion carries the same constants for its own sends).
    const val BUTTON_LEFT = 0x0200
    const val BUTTON_MIDDLE = 0x0201
    const val BUTTON_RIGHT = 0x0202

    /**
     * The state a press of [button] on a cell in [state] sets up,
     * under pattern.c's STYLUS_BASED press handling: LEFT cycles
     * (currstate + 2) % 3 in GRID_ numbering (FULL -> EMPTY ->
     * UNKNOWN -> FULL), RIGHT cycles (currstate + 1) % 3 (the
     * reverse), MIDDLE always yields UNKNOWN.
     */
    fun cycleResult(button: Int, state: PatternCellState): PatternCellState =
        when (button) {
            BUTTON_LEFT -> when (state) {
                PatternCellState.FULL -> PatternCellState.EMPTY
                PatternCellState.EMPTY -> PatternCellState.UNKNOWN
                PatternCellState.UNKNOWN -> PatternCellState.FULL
            }
            BUTTON_RIGHT -> when (state) {
                PatternCellState.EMPTY -> PatternCellState.FULL
                PatternCellState.FULL -> PatternCellState.UNKNOWN
                PatternCellState.UNKNOWN -> PatternCellState.EMPTY
            }
            else -> PatternCellState.UNKNOWN // MIDDLE, from any state
        }

    /**
     * The button whose press+release on a cell in [state] leaves
     * it in [target], or null when the cell already holds the
     * target (a genuine no-op: the spec's "tapping an
     * already-filled cell leaves it filled"). For a FULL or EMPTY
     * target exactly one of LEFT/RIGHT qualifies; UNKNOWN is
     * always MIDDLE's direct set.
     */
    fun tapButton(state: PatternCellState, target: PatternCellState): Int? {
        if (state == target) return null
        if (target == PatternCellState.UNKNOWN) return BUTTON_MIDDLE
        return if (cycleResult(BUTTON_LEFT, state) == target) {
            BUTTON_LEFT
        } else {
            BUTTON_RIGHT
        }
    }

    /** How to begin a paint stroke whose start cell is in [state]. */
    sealed interface DragPlan {
        /** Press [button] at the start cell and drag: the press sets up the target as the engine's drag state. */
        data class Direct(val button: Int) : DragPlan

        /**
         * No single press from the start cell's state sets up
         * the target (the cell already holds it, and both cycles
         * move away). First tap [resetButton] on the start cell
         * — press+release, one engine move setting it UNKNOWN —
         * then press [button] and drag. Two engine moves for the
         * stroke; the first undo reverts the painted region, the
         * second restores the start cell.
         */
        data class ResetThenPress(val resetButton: Int, val button: Int) : DragPlan
    }

    /**
     * The plan for a drag starting on a cell in [state] painting
     * [target]. Erase is always a direct MIDDLE press (its state
     * is absolute, not cycled). Fill/Mark are direct whenever the
     * start cell differs from the target — the same button a tap
     * would use, since the press's cycle result becomes the drag
     * state — and fall back to [DragPlan.ResetThenPress] when the
     * start cell already holds the target.
     */
    fun dragPlan(state: PatternCellState, target: PatternCellState): DragPlan {
        if (target == PatternCellState.UNKNOWN) {
            return DragPlan.Direct(BUTTON_MIDDLE)
        }
        tapButton(state, target)?.let { return DragPlan.Direct(it) }
        // state == target (FULL or EMPTY): reset the start cell
        // to UNKNOWN, then press the button whose cycle from
        // UNKNOWN reaches the target (LEFT for FULL, RIGHT for
        // EMPTY — tapButton(UNKNOWN, target) by construction).
        return DragPlan.ResetThenPress(
            BUTTON_MIDDLE,
            tapButton(PatternCellState.UNKNOWN, target)!!,
        )
    }

    /**
     * The button Pan mode's TAP steps a cell with (modebar2):
     * the one whose press cycle runs UNKNOWN -> FULL -> EMPTY ->
     * UNKNOWN — uncolored, then black, then white, then
     * uncolored again. pattern.c evidence (interpret_move, under
     * STYLUS_BASED): a LEFT press sets
     * `ui->state = (currstate + 2) % 3` in GRID_ numbering
     * (EMPTY 0, FULL 1, UNKNOWN 2), which steps UNKNOWN(2) ->
     * FULL(1) -> EMPTY(0) -> UNKNOWN(2); a RIGHT press sets
     * `(currstate + 1) % 3`, the reverse cycle. Rather than
     * hard-coding LEFT, the selection is derived from the
     * [cycleResult] table above, so a wrong table cannot pick a
     * wrong button silently. The button is state-independent —
     * a Pan tap needs no cell-state read — and one press+release
     * is one engine move. The HOLD uses the reverse direction —
     * see [panHoldCycleButton].
     */
    fun panCycleButton(): Int = listOf(BUTTON_LEFT, BUTTON_RIGHT).first { b ->
        cycleResult(b, PatternCellState.UNKNOWN) == PatternCellState.FULL &&
            cycleResult(b, PatternCellState.FULL) == PatternCellState.EMPTY &&
            cycleResult(b, PatternCellState.EMPTY) == PatternCellState.UNKNOWN
    }

    /**
     * The button Pan mode's HOLD steps a cell with (modebar3,
     * Fernando's correction): the REVERSE of [panCycleButton] —
     * holding on a blank square changes it to white, holding on
     * a black square makes it blank, holding on a white square
     * makes it black, i.e. UNKNOWN -> EMPTY -> FULL -> UNKNOWN.
     * pattern.c evidence: a RIGHT press sets
     * `ui->state = (currstate + 1) % 3` in GRID_ numbering,
     * which steps UNKNOWN(2) -> EMPTY(0) -> FULL(1) ->
     * UNKNOWN(2). Tap forward + hold reverse is the engine's
     * classic LEFT/RIGHT pair; like the tap button, the
     * selection is derived from the [cycleResult] table, so a
     * wrong table cannot pick a wrong button silently.
     */
    fun panHoldCycleButton(): Int = listOf(BUTTON_LEFT, BUTTON_RIGHT).first { b ->
        cycleResult(b, PatternCellState.UNKNOWN) == PatternCellState.EMPTY &&
            cycleResult(b, PatternCellState.EMPTY) == PatternCellState.FULL &&
            cycleResult(b, PatternCellState.FULL) == PatternCellState.UNKNOWN
    }

    /**
     * The end cell of a straight-line drag from ([startCol],
     * [startRow]) toward the raw cell ([col], [row]), clamped
     * into the [w] x [h] grid. This mirrors pattern.c's own
     * drag handling in interpret_move, which snaps a
     * FULL/EMPTY-state drag to a line:
     *
     *     if (abs(x - drag_start_x) > abs(y - drag_start_y))
     *         y = drag_start_y;   // horizontal line
     *     else
     *         x = drag_start_x;   // vertical line
     *
     * and afterwards clamps the drag end into the grid
     * (`if (x < 0) x = 0;` and friends). A GRID_UNKNOWN drag
     * state gets NO snap in the engine — its release fills the
     * whole rectangle between start and end — so when Pan
     * mode's hold-paint stroke paints UNKNOWN (its start cell
     * was black: the hold's reverse cycle steps FULL ->
     * UNKNOWN), GameView clamps the forwarded drag
     * points through this function: the snapped end keeps one
     * coordinate on the start cell, the rectangle degenerates
     * to the 1-wide line, and the stroke paints the straight
     * line Fernando specified for all three states.
     */
    fun snapDragEnd(
        startCol: Int,
        startRow: Int,
        col: Int,
        row: Int,
        w: Int,
        h: Int,
    ): Pair<Int, Int> {
        var endCol = col
        var endRow = row
        if (abs(col - startCol) > abs(row - startRow)) {
            endRow = startRow
        } else {
            endCol = startCol
        }
        return Pair(endCol.coerceIn(0, w - 1), endRow.coerceIn(0, h - 1))
    }

    /**
     * Encodes an engine-intended [button] through GamePlay's
     * swap-LR layer: when the user has left/right swapping on,
     * GamePlay inverts LEFT and RIGHT on every key it forwards,
     * so the planner pre-inverts to land the intended button.
     * MIDDLE is swap-invariant.
     */
    fun swapForSwapLR(button: Int, swapOn: Boolean): Int {
        if (!swapOn) return button
        return when (button) {
            BUTTON_LEFT -> BUTTON_RIGHT
            BUTTON_RIGHT -> BUTTON_LEFT
            else -> button
        }
    }

    /**
     * The board's cell states from a capture's recorded fills,
     * row-major (w*h entries). pattern.c's grid_square draws each
     * cell's state as a filled rect strictly inside the cell
     * (TOCOORD + 1 + a 0/1 five-line inset), in COL_FULL /
     * COL_EMPTY / COL_UNKNOWN, so a state rect's centre names its
     * cell. Rects of any other colour (the COL_GRID cell backing,
     * COL_BACKGROUND erases) and rects centred outside the grid
     * are ignored. Returns null when the capture recorded no
     * state rects at all — the caller then has no ground truth
     * and must not plan (a wrong guess would paint wrong states).
     *
     * One transient: during the engine's completion flash the
     * redraw swaps FULL and EMPTY (pattern.c's flashtime
     * inversion), so a capture landing inside the flash's
     * half-second could read those two swapped. Captures refresh
     * on every input and settle, so the next capture corrects it;
     * the clue capture the states ride along with has the same
     * property for its colours.
     */
    fun cellStates(
        rects: List<DescribeRecorder.DrawnRect>,
        geometry: PatternClueGeometry.Geometry,
        originX: Float,
        originY: Float,
    ): Array<PatternCellState>? {
        val states = Array(geometry.w * geometry.h) { PatternCellState.UNKNOWN }
        var found = false
        val ts = geometry.tileSize
        for (rect in rects) {
            val state = PatternCellState.fromColour(rect.colour) ?: continue
            val cx = rect.x + rect.w / 2f - originX - geometry.gridOriginX
            val cy = rect.y + rect.h / 2f - originY - geometry.gridOriginY
            val col = floor(cx / ts).toInt()
            val row = floor(cy / ts).toInt()
            if (col in 0 until geometry.w && row in 0 until geometry.h) {
                states[row * geometry.w + col] = state
                found = true
            }
        }
        return if (found) states else null
    }
}
