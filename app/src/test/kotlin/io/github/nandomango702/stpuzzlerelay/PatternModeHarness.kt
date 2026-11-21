package io.github.nandomango702.stpuzzlerelay

import kotlin.system.exitProcess

/**
 * Standalone assertion harness for the Pattern mode bar's pure
 * logic ([PatternModePlanner]): the STYLUS_BASED cycle table,
 * the tap/drag gesture plans that turn set-state modes into
 * engine buttons, the swap-LR encoding, the cell-state
 * extraction from a capture's recorded fills, and (modebar2/3)
 * Pan mode's cycle-button selections — tap forward, hold
 * reverse — and drag line snap. JUnit is
 * unavailable in the offline build environment, so — like the
 * pinned-clues harness — the assertions run as a plain main()
 * compiled with the cached Kotlin compiler against the real
 * PatternMode.kt / PatternClues.kt / DescribeRecorder.kt. The
 * same cases are mirrored as JUnit tests in
 * PatternModePlannerTest for networked environments.
 *
 * The cycle expectations are pattern.c's own arithmetic, read
 * from interpret_move: LEFT sets (currstate + 2) % 3 and RIGHT
 * (currstate + 1) % 3 in GRID_ numbering (EMPTY 0, FULL 1,
 * UNKNOWN 2); MIDDLE always yields GRID_UNKNOWN.
 */
private var failures = 0
private var checks = 0

private fun checkTrue(name: String, cond: Boolean) {
    checks++
    if (!cond) {
        failures++
        println("FAIL $name")
    } else {
        println("ok   $name")
    }
}

private fun checkState(name: String, actual: PatternCellState, expected: PatternCellState) {
    checkTrue("$name (expected $expected, got $actual)", actual == expected)
}

private fun checkButton(name: String, actual: Int?, expected: Int?) {
    checkTrue("$name (expected $expected, got $actual)", actual == expected)
}

fun main() {
    val p = PatternModePlanner
    val l = PatternModePlanner.BUTTON_LEFT
    val m = PatternModePlanner.BUTTON_MIDDLE
    val r = PatternModePlanner.BUTTON_RIGHT
    val e = PatternCellState.EMPTY
    val f = PatternCellState.FULL
    val u = PatternCellState.UNKNOWN

    // --- The STYLUS_BASED cycle table (pattern.c interpret_move) ---
    checkState("LEFT on FULL", p.cycleResult(l, f), e)
    checkState("LEFT on EMPTY", p.cycleResult(l, e), u)
    checkState("LEFT on UNKNOWN", p.cycleResult(l, u), f)
    checkState("RIGHT on EMPTY", p.cycleResult(r, e), f)
    checkState("RIGHT on FULL", p.cycleResult(r, f), u)
    checkState("RIGHT on UNKNOWN", p.cycleResult(r, u), e)
    checkState("MIDDLE on EMPTY", p.cycleResult(m, e), u)
    checkState("MIDDLE on FULL", p.cycleResult(m, f), u)
    checkState("MIDDLE on UNKNOWN", p.cycleResult(m, u), u)

    // --- Tap plans: set-state in one press+release, or a no-op ---
    checkButton("tap FULL on FULL is a no-op", p.tapButton(f, f), null)
    checkButton("tap EMPTY on FULL", p.tapButton(f, e), l)
    checkButton("tap UNKNOWN on FULL", p.tapButton(f, u), m)
    checkButton("tap FULL on EMPTY", p.tapButton(e, f), r)
    checkButton("tap EMPTY on EMPTY is a no-op", p.tapButton(e, e), null)
    checkButton("tap UNKNOWN on EMPTY", p.tapButton(e, u), m)
    checkButton("tap FULL on UNKNOWN", p.tapButton(u, f), l)
    checkButton("tap EMPTY on UNKNOWN", p.tapButton(u, e), r)
    checkButton("tap UNKNOWN on UNKNOWN is a no-op", p.tapButton(u, u), null)
    // Soundness: every non-null tap plan lands exactly on target.
    for (state in PatternCellState.values()) {
        for (target in PatternCellState.values()) {
            val btn = p.tapButton(state, target)
            checkTrue(
                "tap plan $state -> $target is sound",
                if (btn == null) state == target
                else p.cycleResult(btn, state) == target
            )
        }
    }

    // --- Drag plans ---
    // Erase is always a direct MIDDLE press, from any state.
    for (state in PatternCellState.values()) {
        val plan = p.dragPlan(state, u)
        checkTrue(
            "drag ERASE from $state is Direct(MIDDLE)",
            plan is PatternModePlanner.DragPlan.Direct && plan.button == m
        )
    }
    // Fill/Mark from a differing start cell: the tap button,
    // directly (its cycle result becomes the drag state).
    checkTrue(
        "drag FILL from EMPTY is Direct(RIGHT)",
        p.dragPlan(e, f) == PatternModePlanner.DragPlan.Direct(r)
    )
    checkTrue(
        "drag FILL from UNKNOWN is Direct(LEFT)",
        p.dragPlan(u, f) == PatternModePlanner.DragPlan.Direct(l)
    )
    checkTrue(
        "drag MARK from FULL is Direct(LEFT)",
        p.dragPlan(f, e) == PatternModePlanner.DragPlan.Direct(l)
    )
    checkTrue(
        "drag MARK from UNKNOWN is Direct(RIGHT)",
        p.dragPlan(u, e) == PatternModePlanner.DragPlan.Direct(r)
    )
    // Fill/Mark starting on a cell already holding the target:
    // reset to UNKNOWN with MIDDLE, then press from UNKNOWN.
    checkTrue(
        "drag FILL from FULL is ResetThenPress(MIDDLE, LEFT)",
        p.dragPlan(f, f) == PatternModePlanner.DragPlan.ResetThenPress(m, l)
    )
    checkTrue(
        "drag MARK from EMPTY is ResetThenPress(MIDDLE, RIGHT)",
        p.dragPlan(e, e) == PatternModePlanner.DragPlan.ResetThenPress(m, r)
    )
    // Soundness: simulate every plan against the cycle table —
    // a Direct press must set up the target as the drag state; a
    // ResetThenPress must pass through UNKNOWN to the target.
    for (state in PatternCellState.values()) {
        for (target in PatternCellState.values()) {
            val sound = when (val plan = p.dragPlan(state, target)) {
                is PatternModePlanner.DragPlan.Direct ->
                    p.cycleResult(plan.button, state) == target
                is PatternModePlanner.DragPlan.ResetThenPress ->
                    p.cycleResult(plan.resetButton, state) == u &&
                        p.cycleResult(plan.button, u) == target
            }
            checkTrue("drag plan $state -> $target is sound", sound)
        }
    }

    // --- Swap-LR encoding ---
    checkButton("swap off keeps LEFT", p.swapForSwapLR(l, false), l)
    checkButton("swap off keeps RIGHT", p.swapForSwapLR(r, false), r)
    checkButton("swap on turns LEFT into RIGHT", p.swapForSwapLR(l, true), r)
    checkButton("swap on turns RIGHT into LEFT", p.swapForSwapLR(r, true), l)
    checkButton("swap on keeps MIDDLE", p.swapForSwapLR(m, true), m)

    // --- Pan mode's cycle button (modebar2) ---
    // Fernando's direction: uncolored -> black -> white ->
    // uncolored. pattern.c: LEFT sets (currstate + 2) % 3 in
    // GRID_ numbering (EMPTY 0, FULL 1, UNKNOWN 2), which is
    // exactly that direction; RIGHT runs the reverse cycle.
    val cycle = p.panCycleButton()
    checkButton("pan cycle button is LEFT", cycle, l)
    checkState("pan cycle steps UNKNOWN to FULL", p.cycleResult(cycle, u), f)
    checkState("pan cycle steps FULL to EMPTY", p.cycleResult(cycle, f), e)
    checkState("pan cycle steps EMPTY to UNKNOWN", p.cycleResult(cycle, e), u)
    // The other button must NOT satisfy the direction — the
    // selection is a real choice, not a quirk of the table.
    val other = if (cycle == l) r else l
    checkTrue(
        "pan cycle direction excludes the other button",
        !(p.cycleResult(other, u) == f && p.cycleResult(other, f) == e &&
            p.cycleResult(other, e) == u)
    )
    // Three steps return to the start state, from every state.
    for (state in PatternCellState.values()) {
        val stepped =
            p.cycleResult(cycle, p.cycleResult(cycle, p.cycleResult(cycle, state)))
        checkState("pan cycle cubed is identity on $state", stepped, state)
    }

    // --- Pan mode's HOLD cycle button (modebar3) ---
    // Fernando's correction: the hold runs the cycle in
    // REVERSE — holding a blank cell makes it white, a black
    // cell blank, a white cell black. pattern.c: RIGHT sets
    // (currstate + 1) % 3, exactly the reverse direction; the
    // tap keeps the forward (LEFT) cycle above.
    val holdCycle = p.panHoldCycleButton()
    checkButton("pan hold cycle button is RIGHT", holdCycle, r)
    checkState("pan hold steps UNKNOWN to EMPTY", p.cycleResult(holdCycle, u), e)
    checkState("pan hold steps EMPTY to FULL", p.cycleResult(holdCycle, e), f)
    checkState("pan hold steps FULL to UNKNOWN", p.cycleResult(holdCycle, f), u)
    // The hold button is NOT the tap button — the split is the
    // whole point of the correction.
    checkTrue("pan hold button differs from the tap button", holdCycle != cycle)
    val holdOther = if (holdCycle == l) r else l
    checkTrue(
        "pan hold direction excludes the other button",
        !(p.cycleResult(holdOther, u) == e && p.cycleResult(holdOther, e) == f &&
            p.cycleResult(holdOther, f) == u)
    )
    // Three reverse steps also return to the start state.
    for (state in PatternCellState.values()) {
        val stepped = p.cycleResult(
            holdCycle, p.cycleResult(holdCycle, p.cycleResult(holdCycle, state))
        )
        checkState("pan hold cycle cubed is identity on $state", stepped, state)
    }

    // --- Drag line snap (modebar2) ---
    // pattern.c's own rule for FULL/EMPTY drags, applied by the
    // frontend to UNKNOWN drags (which the engine leaves as a
    // rectangle): |dx| > |dy| keeps the start row (horizontal
    // line), otherwise keeps the start column (vertical line);
    // the end is then clamped into the grid.
    fun checkSnap(name: String, actual: Pair<Int, Int>, expected: Pair<Int, Int>) {
        checkTrue("$name (expected $expected, got $actual)", actual == expected)
    }
    checkSnap("snap horizontal keeps the row", p.snapDragEnd(2, 3, 7, 4, 10, 10), Pair(7, 3))
    checkSnap("snap vertical keeps the column", p.snapDragEnd(2, 3, 3, 8, 10, 10), Pair(2, 8))
    checkSnap(
        "snap tie goes vertical (the engine's else branch)",
        p.snapDragEnd(2, 3, 5, 6, 10, 10), Pair(2, 6)
    )
    checkSnap("snap in place is the start cell", p.snapDragEnd(2, 3, 2, 3, 10, 10), Pair(2, 3))
    checkSnap("snap clamps past the right edge", p.snapDragEnd(2, 3, 25, 4, 10, 10), Pair(9, 3))
    checkSnap("snap clamps past the bottom edge", p.snapDragEnd(2, 3, 3, 25, 10, 10), Pair(2, 9))
    checkSnap("snap clamps a negative end", p.snapDragEnd(2, 3, -4, 3, 10, 10), Pair(0, 3))
    checkSnap("snap horizontal toward the left", p.snapDragEnd(6, 3, 1, 4, 10, 10), Pair(1, 3))

    // --- Cell-state extraction from recorded fills ---
    // 15x15 at tile size 24 (golden geometry): grid origin
    // (150,150) in game coords; the capture origin is separate.
    val geom = PatternClueGeometry.compute(15, 15, 24, 528, 528)!!
    val originX = 30f
    val originY = 40f
    val ts = geom.tileSize

    // A state rect the way grid_square draws it: TOCOORD + 1 +
    // the five-line inset (xl/yt on the leading edge when the
    // index is a multiple of 5, xr/yb on the trailing edge at
    // index % 5 == 4 or the board edge).
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
        stateRect(0, 0, 2),   // FULL
        stateRect(1, 0, 1),   // EMPTY
        stateRect(2, 0, 4),   // UNKNOWN
        stateRect(5, 3, 2),   // FULL, past a five-line boundary
        stateRect(14, 14, 1), // EMPTY, the board's far corner
        // Decoys: the COL_GRID cell backing and a COL_BACKGROUND
        // erase inside the grid must not be read as states...
        DescribeRecorder.DrawnRect(
            (originX + geom.gridOriginX + 3 * ts).toInt(),
            (originY + geom.gridOriginY).toInt(), ts, ts, 5,
        ),
        DescribeRecorder.DrawnRect(
            (originX + geom.gridOriginX + 4 * ts + 2).toInt(),
            (originY + geom.gridOriginY + 2).toInt(), ts - 4, ts - 4, 0,
        ),
        // ...and neither must state-coloured rects centred
        // outside the grid (here: in the left clue strip, and
        // past the grid's right edge).
        DescribeRecorder.DrawnRect(
            (originX + geom.gridOriginX / 2 - 5).toInt(),
            (originY + geom.gridOriginY + 5).toInt(), 10, 10, 2,
        ),
        DescribeRecorder.DrawnRect(
            (originX + geom.gridOriginX + geom.w * ts + 20).toInt(),
            (originY + geom.gridOriginY + 5).toInt(), 10, 10, 2,
        ),
    )
    val states = p.cellStates(rects, geom, originX, originY)
    checkTrue("extraction finds states", states != null)
    states!!
    checkState("cell (0,0) is FULL", states[0], f)
    checkState("cell (1,0) is EMPTY", states[1], e)
    checkState("cell (2,0) is UNKNOWN", states[2], u)
    checkState("cell (5,3) is FULL", states[3 * geom.w + 5], f)
    checkState("cell (14,14) is EMPTY", states[14 * geom.w + 14], e)
    checkState("decoy COL_GRID cell (3,0) stays UNKNOWN", states[3], u)
    checkState("decoy COL_BACKGROUND cell (4,0) stays UNKNOWN", states[4], u)
    checkState("untouched cell (7,7) defaults UNKNOWN", states[7 * geom.w + 7], u)
    checkTrue(
        "extraction with no rects is null",
        p.cellStates(emptyList(), geom, originX, originY) == null
    )
    checkTrue(
        "extraction with only decoy colours is null",
        p.cellStates(listOf(rects[5], rects[6]), geom, originX, originY) == null
    )

    // --- The same states through PatternClues.fromCapture ---
    val clues = PatternClues.fromCapture(emptyList(), geom, originX, originY, rects)
    checkTrue("fromCapture carries the states", clues.cellStates != null)
    checkState(
        "fromCapture cell (5,3) is FULL",
        clues.cellStates!![3 * geom.w + 5], f
    )
    checkTrue(
        "fromCapture without rects has no states",
        PatternClues.fromCapture(emptyList(), geom, originX, originY).cellStates == null
    )

    println("---")
    println("$checks checks, $failures failures")
    if (failures > 0) exitProcess(1)
    println("ALL PASSED")
}
