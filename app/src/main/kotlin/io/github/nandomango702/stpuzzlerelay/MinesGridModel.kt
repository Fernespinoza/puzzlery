package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.floor

/**
 * What a Mines square currently is, as read from one capture.
 * The order of the classification in [MinesGridModel.fromCapture]
 * follows the engine's own drawing: each state is identified by
 * the primitives mines.c actually emits for it, never inferred
 * from neighbours or from the player's history.
 */
enum class MinesCellState {
    /** Covered, unmarked (engine value -2). */
    COVERED,

    /** Covered and flagged as a mine (engine value -1). */
    FLAGGED,

    /** Covered with a question mark (engine value -3). */
    QUESTION,

    /** Uncovered with no neighbouring mines (engine value 0). */
    BLANK,

    /** Uncovered number, 1-8 (the digit rides in the model). */
    NUMBER,

    /** A mine, revealed by Solve's corrections grid (engine value 64). */
    MINE,

    /** The mine that killed the player (engine value 65). */
    BANG,

    /** A flag on a square with no mine, crossed out by the corrections grid (66). */
    WRONG_FLAG,
}

/** The moves the TalkBack grid can send for one Mines square. */
enum class MinesGridAction { UNCOVER, FLAG, CHORD }

/**
 * One board capture, shaped for [MinesGridModel]: the recorder's
 * primitives plus the board's parameters and the geometry facts
 * the model validates against (GamePlay.captureMinesBoard builds
 * this from the same forced-redraw capture Describe board uses).
 */
class MinesCapture(
    val texts: List<DescribeRecorder.DrawnText>,
    val lines: List<DescribeRecorder.DrawnLine>,
    val rects: List<DescribeRecorder.DrawnRect>,
    val polys: List<DescribeRecorder.DrawnPoly>,
    /** Board width / height in cells, and the mine total, from the game params. */
    val w: Int,
    val h: Int,
    val totalMines: Int,
    /** The engine's reported game size, in game coordinates. */
    val gameSizeX: Int,
    val gameSizeY: Int,
    /** The engine's live tile size. */
    val tileSize: Int,
    /** The board's offset within the capture space (the frontend's centring). */
    val originX: Float,
    val originY: Float,
)

/**
 * A cell-by-cell model of a Mines board, built from a
 * [MinesCapture] — the data layer of Mines' TalkBack virtual
 * grid (Phase 2, tranche 3; Solo's and Pattern's grids are the
 * templates, see [SoloGridModel] and [PatternGridModel]).
 *
 * State is read from what mines.c draws, primitive by primitive:
 *
 * - GEOMETRY is validated, not assumed: the border is solved
 *   from the engine's reported extent and tile size and must
 *   come out identical on both axes and equal to one of
 *   mines.c's two BORDER definitions (TILE_SIZE*3/2, or 8 on
 *   SMALL_SCREEN builds). Any mismatch — like a capture taken
 *   mid-resize — and the model refuses to build, so the grid
 *   stays silent rather than mislabel squares.
 * - COMPLETENESS is proven per cell: draw_tile emits at least
 *   one rect for every square, covered or not, so a capture
 *   in which any cell received no rect is not a full redraw
 *   and is rejected whole.
 * - STATE comes from the drawn primitives in the cell: a digit
 *   text is an uncovered number (its tile rect carries
 *   COL_WRONGNUMBER when the engine flags the number as
 *   contradicted by surrounding flags); a flag is the
 *   COL_FLAG / COL_FLAGBASE polygons (flags are polygons and
 *   nothing else in this game, which is why the recorder
 *   records polygons for Mines captures); a mine is the
 *   COL_MINE spike rects, on a COL_BANG tile for the one that
 *   killed the player; COL_CROSS diagonals over a mine are
 *   the corrections grid's wrong flag; COL_MINE diagonals on
 *   an otherwise covered square are the engine's safe-start
 *   cross; a full-tile rect with nothing on it is an
 *   uncovered empty square; and a square with only its inner
 *   covered rect is covered.
 */
class MinesGridModel private constructor(
    val w: Int,
    val h: Int,
    /** The mine total for this board, from its parameters. */
    val totalMines: Int,
    private val states: Array<MinesCellState>,
    /** The digit for NUMBER cells (0 elsewhere). */
    private val digits: IntArray,
    /** True for NUMBER cells the engine draws on its wrong-number colour. */
    private val tooManyFlags: BooleanArray,
    /** True for the covered square the engine marks with its safe-start cross. */
    private val safeStart: BooleanArray,
    /** Capture-coordinate centres of each column / row, ascending. */
    val colCenters: FloatArray,
    val rowCenters: FloatArray,
    /** Tile size in capture coordinates. */
    val tileSize: Float,
) {

    /** How many squares are currently flagged, counted from this capture. */
    val flagCount: Int = states.count { it == MinesCellState.FLAGGED }

    /**
     * True once the game has ended in death or a corrections
     * reveal: a BANG, MINE, or WRONG_FLAG square exists only
     * after the engine has shown a mine, which it never does
     * while play is live. (A win reveals nothing, so it is not
     * detectable here — the shared completion announcement
     * covers that moment, and the engine ignores further moves
     * on a won board, which the grid then truthfully reports
     * back as unchanged labels.)
     */
    val gameOver: Boolean = states.any {
        it == MinesCellState.BANG || it == MinesCellState.MINE ||
            it == MinesCellState.WRONG_FLAG
    }

    fun stateAt(row: Int, col: Int): MinesCellState = states[row * w + col]

    /** The cell's rectangle in game coordinates. */
    fun gameRect(row: Int, col: Int): RectF {
        val half = tileSize / 2f
        return RectF(
            colCenters[col] - half, rowCenters[row] - half,
            colCenters[col] + half, rowCenters[row] + half,
        )
    }

    /** The virtual-view id of the cell nearest a game-coordinate point, or null off-board. */
    fun virtualIdAt(gameX: Float, gameY: Float): Int? {
        val col = nearest(colCenters, gameX) ?: return null
        val row = nearest(rowCenters, gameY) ?: return null
        if (abs(colCenters[col] - gameX) > tileSize / 2f) return null
        if (abs(rowCenters[row] - gameY) > tileSize / 2f) return null
        return row * w + col
    }

    /**
     * The custom actions one cell offers. State-appropriate on
     * purpose: Flag is never offered on an uncovered square
     * (a right press there is the engine's clear-around, not a
     * flag, and offering it as "Flag" could kill the player),
     * and nothing is offered once the game is over.
     */
    fun offeredActions(row: Int, col: Int): Set<MinesGridAction> {
        if (gameOver) return emptySet()
        return when (stateAt(row, col)) {
            MinesCellState.COVERED ->
                setOf(MinesGridAction.UNCOVER, MinesGridAction.FLAG)
            // A right press on a question-marked square is an
            // engine no-op in this engine (flags toggle only
            // between -1 and -2), so no Flag is offered here.
            MinesCellState.QUESTION -> setOf(MinesGridAction.UNCOVER)
            // FLAG on a flagged square means unflag; the grid
            // labels the action from the state.
            MinesCellState.FLAGGED -> setOf(MinesGridAction.FLAG)
            MinesCellState.NUMBER -> setOf(MinesGridAction.CHORD)
            else -> emptySet()
        }
    }

    /**
     * The TalkBack label for one cell: "Row 3, column 5: " plus
     * the square's state — covered, flagged, its number, or 0
     * for an uncovered square with no mines around it (the
     * literal value, so it can never be confused with Pattern's
     * "blank", which means untouched there) — then the engine's
     * own extra signals for that square when it raises them:
     * the safe-start cross, the mine the player hit, and a
     * number its surrounding flags contradict.
     */
    fun label(context: Context, row: Int, col: Int): String {
        val i = row * w + col
        val stateWord = when (states[i]) {
            MinesCellState.COVERED -> context.getString(R.string.tb_mines_covered)
            MinesCellState.FLAGGED -> context.getString(R.string.tb_mines_flagged)
            MinesCellState.QUESTION -> context.getString(R.string.tb_mines_question)
            MinesCellState.BLANK -> context.getString(R.string.tb_mines_blank)
            MinesCellState.NUMBER -> digits[i].toString()
            MinesCellState.MINE, MinesCellState.BANG ->
                context.getString(R.string.tb_mines_mine)
            MinesCellState.WRONG_FLAG -> context.getString(R.string.tb_mines_wrong_flag)
        }
        val sb = StringBuilder(
            context.getString(R.string.tb_grid_row_col, row + 1, col + 1, stateWord)
        )
        // At most one suffix can apply (each belongs to a
        // different state); it joins as its own sentence.
        val suffix = when {
            states[i] == MinesCellState.COVERED && safeStart[i] ->
                context.getString(R.string.tb_mines_safe_start)
            states[i] == MinesCellState.BANG ->
                context.getString(R.string.tb_mines_hit)
            states[i] == MinesCellState.NUMBER && tooManyFlags[i] ->
                context.getString(R.string.tb_mines_too_many_flags)
            else -> null
        }
        if (suffix != null) {
            sb.append(". ").append(suffix).append('.')
        }
        return sb.toString()
    }

    companion object {
        // mines.c's colour indices (its game_colours enum order),
        // passed through untouched by the recorder.
        private const val COL_MINE = 10
        private const val COL_BANG = 11
        private const val COL_CROSS = 12
        private const val COL_FLAG = 13
        private const val COL_FLAGBASE = 14
        private const val COL_WRONGNUMBER = 18

        /**
         * Builds the model from one capture, or returns null
         * when the capture cannot be proven to be this board:
         * implausible dimensions, a border that does not solve
         * consistently on both axes to one of mines.c's BORDER
         * values, or a cell that received no rect (not a full
         * redraw). Null keeps the grid silent — the no-guess
         * principle both earlier grids apply.
         */
        fun fromCapture(cap: MinesCapture): MinesGridModel? {
            val w = cap.w
            val h = cap.h
            val ts = cap.tileSize
            if (w !in 1..100 || h !in 1..100 || ts <= 0) return null
            val borderTimes2X = cap.gameSizeX - ts * w
            val borderTimes2Y = cap.gameSizeY - ts * h
            if (borderTimes2X != borderTimes2Y || borderTimes2X < 0 ||
                borderTimes2X % 2 != 0
            ) {
                return null
            }
            val border = borderTimes2X / 2
            // mines.c: BORDER is TILE_SIZE*3/2, or 8 under SMALL_SCREEN.
            if (border != ts * 3 / 2 && border != 8) return null

            val colCenters = FloatArray(w) {
                cap.originX + border + (it + 0.5f) * ts
            }
            val rowCenters = FloatArray(h) {
                cap.originY + border + (it + 0.5f) * ts
            }

            fun cellOf(x: Float, y: Float): Int? {
                val col = floor((x - cap.originX - border) / ts).toInt()
                val row = floor((y - cap.originY - border) / ts).toInt()
                return if (col in 0 until w && row in 0 until h) row * w + col else null
            }

            val n = w * h
            val rectCount = IntArray(n)
            val fullRectColour = arrayOfNulls<Int>(n)
            val hasMineRects = BooleanArray(n)
            val hasCross = BooleanArray(n)
            val hasStartCross = BooleanArray(n)
            val hasFlag = BooleanArray(n)
            val hasQuery = BooleanArray(n)
            val digit = IntArray(n)

            for (rect in cap.rects) {
                val cell = cellOf(rect.x + rect.w / 2f, rect.y + rect.h / 2f)
                    ?: continue
                rectCount[cell]++
                if (rect.colour == COL_MINE) hasMineRects[cell] = true
                if (rect.w == ts && rect.h == ts) fullRectColour[cell] = rect.colour
            }
            for (poly in cap.polys) {
                if (poly.fillColour != COL_FLAG && poly.fillColour != COL_FLAGBASE) {
                    continue
                }
                if (poly.points.size < 2) continue
                val cell = cellOf(
                    poly.points[0].toFloat(), poly.points[1].toFloat(),
                ) ?: continue
                hasFlag[cell] = true
            }
            for (line in cap.lines) {
                val cell = cellOf((line.x1 + line.x2) / 2f, (line.y1 + line.y2) / 2f)
                    ?: continue
                when (line.colour) {
                    COL_CROSS -> hasCross[cell] = true
                    COL_MINE -> hasStartCross[cell] = true
                }
            }
            for (text in cap.texts) {
                val cell = cellOf(text.x.toFloat(), text.y.toFloat()) ?: continue
                when {
                    text.text == "?" -> hasQuery[cell] = true
                    text.text.length == 1 && text.text[0] in '1'..'8' ->
                        digit[cell] = text.text[0] - '0'
                }
            }

            // draw_tile emits at least one rect per square; a
            // cell with none means this was not a full redraw.
            if (rectCount.any { it == 0 }) return null

            val states = Array(n) { MinesCellState.COVERED }
            val tooManyFlags = BooleanArray(n)
            val safeStart = BooleanArray(n)
            for (i in 0 until n) {
                states[i] = when {
                    hasCross[i] -> MinesCellState.WRONG_FLAG
                    hasMineRects[i] ->
                        if (fullRectColour[i] == COL_BANG) MinesCellState.BANG
                        else MinesCellState.MINE
                    digit[i] != 0 -> {
                        tooManyFlags[i] = fullRectColour[i] == COL_WRONGNUMBER
                        MinesCellState.NUMBER
                    }
                    hasQuery[i] -> MinesCellState.QUESTION
                    hasFlag[i] -> MinesCellState.FLAGGED
                    fullRectColour[i] != null -> MinesCellState.BLANK
                    else -> {
                        safeStart[i] = hasStartCross[i]
                        MinesCellState.COVERED
                    }
                }
            }
            return MinesGridModel(
                w, h, cap.totalMines, states, digit, tooManyFlags, safeStart,
                colCenters, rowCenters, ts.toFloat(),
            )
        }

        /** Index of the centre nearest [v], or null for an empty array. */
        private fun nearest(centers: FloatArray, v: Float): Int? =
            centers.indices.minByOrNull { abs(centers[it] - v) }
    }
}
