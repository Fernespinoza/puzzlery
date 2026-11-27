package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import android.graphics.RectF
import kotlin.math.abs

/**
 * A cell-by-cell model of a Pattern board, built from a
 * [PatternClues] capture — the data layer of Pattern's TalkBack
 * virtual grid (Phase 2, tranche 2; the Solo grid of v0.6.0 is
 * the template, see [SoloGridModel]).
 *
 * Everything comes from the one capture the pinned clues already
 * force on every move, so the spoken board can never drift from
 * the drawn one:
 *
 * - GEOMETRY is [PatternClueGeometry]'s validated lattice (the
 *   capture is only built at all when the derived margins
 *   reproduce the engine's reported extent exactly).
 * - STATE is the capture's recorded cell fills, read by
 *   [PatternModePlanner.cellStates] — the mode bar's ground
 *   truth, reused here rather than re-derived.
 * - CLUES are the captured clue texts: each row's single
 *   right-aligned string ("3  1", pattern.c's draw_numbers joins
 *   the runs with spaces) split back into runs, and each column's
 *   stack of single numbers in draw order — which is run order,
 *   first run at the top (pattern.c draws j ascending while its
 *   y formula walks the stack downward).
 * - ERRORS ride on the clue texts' colours: pattern.c draws a
 *   row's or column's numbers in COL_ERROR when its own
 *   check_errors flags the line, so a line's error state is read
 *   from its clue colour, never inferred from the cells here.
 */
class PatternGridModel private constructor(
    /** Board width / height in cells (Pattern boards need not be square). */
    val w: Int,
    val h: Int,
    private val states: Array<PatternCellState>,
    /** The clue runs per row / column, as spoken numbers ("3", "1"). */
    private val rowRuns: List<List<String>>,
    private val colRuns: List<List<String>>,
    /** True when the engine draws that row's / column's clues in its error colour. */
    private val rowError: BooleanArray,
    private val colError: BooleanArray,
    /** Capture-coordinate centres of each column / row, ascending. */
    val colCenters: FloatArray,
    val rowCenters: FloatArray,
    /** Tile size in capture coordinates. */
    val tileSize: Float,
) {

    fun stateAt(row: Int, col: Int): PatternCellState = states[row * w + col]

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
     * The TalkBack label for one cell: its state, then the clue
     * runs of its row and column — the numbers the pinned strips
     * show a sighted player — then the engine's error flags for
     * those two lines when it raises them. E.g. "Row 3, column 5:
     * blank. Row clues 3 1. Column clue 2."
     */
    fun label(context: Context, row: Int, col: Int): String {
        val stateWord = context.getString(
            when (stateAt(row, col)) {
                PatternCellState.FULL -> R.string.tb_pattern_filled
                PatternCellState.EMPTY -> R.string.tb_pattern_marked
                PatternCellState.UNKNOWN -> R.string.tb_pattern_blank
            }
        )
        val sb = StringBuilder(
            context.getString(R.string.tb_grid_row_col, row + 1, col + 1, stateWord)
        )
        rowRuns.getOrNull(row)?.takeIf { it.isNotEmpty() }?.let { runs ->
            sb.append(". ")
            sb.append(
                context.getString(
                    if (runs.size == 1) R.string.tb_pattern_row_clue
                    else R.string.tb_pattern_row_clues,
                    runs.joinToString(" "),
                )
            )
            sb.append('.')
        }
        colRuns.getOrNull(col)?.takeIf { it.isNotEmpty() }?.let { runs ->
            sb.append(". ")
            sb.append(
                context.getString(
                    if (runs.size == 1) R.string.tb_pattern_col_clue
                    else R.string.tb_pattern_col_clues,
                    runs.joinToString(" "),
                )
            )
            sb.append('.')
        }
        if (rowError.getOrNull(row) == true) {
            sb.append(' ').append(context.getString(R.string.tb_pattern_row_error)).append('.')
        }
        if (colError.getOrNull(col) == true) {
            sb.append(' ').append(context.getString(R.string.tb_pattern_col_error)).append('.')
        }
        return sb.toString()
    }

    companion object {
        // pattern.c's colour index for error-flagged clue numbers
        // (its COL_ERROR; the same index Solo's model reads on
        // digits, passed through untouched by the recorder).
        private const val COL_ERROR = 7

        /**
         * Builds the model from one clue capture. Returns null
         * when the capture carries no cell states — without that
         * ground truth the grid stays silent rather than risk
         * labelling (or setting) a state the capture didn't prove,
         * the mode bar's no-guess principle applied to the grid.
         */
        fun fromClues(clues: PatternClues): PatternGridModel? {
            val states = clues.cellStates ?: return null
            val g = clues.geometry
            val ts = g.tileSize.toFloat()
            val colCenters = FloatArray(g.w) {
                clues.originX + g.gridOriginX + (it + 0.5f) * ts
            }
            val rowCenters = FloatArray(g.h) {
                clues.originY + g.gridOriginY + (it + 0.5f) * ts
            }
            val rowRuns = clues.rowClues.map { text ->
                text?.text?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }
                    ?: emptyList()
            }
            val colRuns = clues.colClues.map { stack -> stack.map { it.text } }
            val rowError = BooleanArray(g.h) { r ->
                clues.rowClues.getOrNull(r)?.colour == COL_ERROR
            }
            val colError = BooleanArray(g.w) { c ->
                clues.colClues.getOrNull(c)?.any { it.colour == COL_ERROR } == true
            }
            return PatternGridModel(
                g.w, g.h, states, rowRuns, colRuns, rowError, colError,
                colCenters, rowCenters, ts,
            )
        }

        /** Index of the centre nearest [v], or null for an empty array. */
        private fun nearest(centers: FloatArray, v: Float): Int? =
            centers.indices.minByOrNull { abs(centers[it] - v) }
    }
}
