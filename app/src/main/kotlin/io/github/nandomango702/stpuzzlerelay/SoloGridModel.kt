package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max

/**
 * A cell-by-cell model of a Solo board, built from a
 * [DescribeRecorder] capture — the data layer of the TalkBack
 * virtual grid (Phase 2, tranche 1; design note 2026-11-06).
 *
 * Two authoritative sources, no guessing:
 *
 * - GEOMETRY comes from the engine, not from the texts. cr is
 *   parsed from the running game's parameters, the tile size is
 *   solved from the engine's own game size (Solo's board extent
 *   is exactly cr·tile + 2·(tile/2) + 1), and the board origin is
 *   the frontend's centring offset — the same (view − game)/2
 *   math android.c applies when it draws. The lattice is
 *   therefore complete by construction: rows and columns with no
 *   digit in them still exist as cells. (The first build inferred
 *   the lattice by clustering digit positions; on sparse small
 *   boards — 4×4, 6×6 — whole rows vanished and the model
 *   refused to build. Fernando caught it on-device, 2026-11-06.)
 *
 * - MEANING comes from colour. The recorder captures each drawn
 *   text's colour index, and in solo.c the colour is the meaning:
 *   COL_CLUE (4) is a printed given, COL_USER (5) is the player's
 *   own entry, COL_ERROR (7) an entry the engine has flagged,
 *   COL_PENCIL (8) a pencil mark, COL_KILLER (9) a Killer cage
 *   total. So a cell can be labelled "given 8" versus "entered 5"
 *   versus "pencil marks 2, 5" — the distinction a blind player
 *   actually plays by, which Phase 1's Describe board could not
 *   make.
 *
 * - KILLER CAGES are reconstructed from the capture's lines: the
 *   cage segments are the only lines solo.c draws, and flood-
 *   filling cells across unwalled sides reproduces the engine's
 *   cage partition exactly (spike 2026-11-08, harness-proven).
 *   Membership attaches to the model as a cage id per cell —
 *   never inside [Cell], whose equality drives move-diffing.
 */
class SoloGridModel private constructor(
    /** Cells per side (Solo boards are square: cr = block cols × block rows). */
    val cr: Int,
    private val cells: Array<Cell>,
    /** Capture-coordinate centres of each column / row, ascending. */
    val colCenters: FloatArray,
    val rowCenters: FloatArray,
    /** Tile size in capture coordinates. */
    val tileSize: Float,
    /** Block width/height in cells for standard variants; null for Jigsaw. */
    val blockCols: Int?,
    val blockRows: Int?,
    /** Killer cage id per cell (flat index), or null when no membership was reconstructed. */
    private val cageIds: IntArray? = null,
) {

    /** A row, column, or box of the board, for completion announcements. */
    sealed interface Unit {
        data class Row(val index: Int) : Unit
        data class Column(val index: Int) : Unit
        data class Box(val index: Int) : Unit
    }
    data class Cell(
        /** Spoken value of the placed digit ("7", "10"), or null. */
        val digit: String?,
        val given: Boolean,
        val error: Boolean,
        /** Spoken pencil-mark values in this cell, ascending. */
        val pencils: List<String>,
        /** Killer cage total printed in this cell, if any. */
        val cageTotal: String?,
    )

    fun cellAt(row: Int, col: Int): Cell = cells[row * cr + col]

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
        return row * cr + col
    }

    /** The TalkBack label for one cell, e.g. "Row 3, column 5: given 8". */
    fun label(context: Context, row: Int, col: Int): String {
        val cell = cellAt(row, col)
        val state = when {
            cell.digit != null && cell.given ->
                context.getString(R.string.tb_grid_given, cell.digit)
            cell.digit != null && cell.error ->
                context.getString(R.string.tb_grid_error, cell.digit)
            cell.digit != null ->
                context.getString(R.string.tb_grid_entered, cell.digit)
            cell.pencils.isNotEmpty() ->
                context.getString(R.string.tb_grid_pencil, cell.pencils.joinToString(" "))
            else -> context.getString(R.string.tb_grid_empty)
        }
        return context.getString(R.string.tb_grid_row_col, row + 1, col + 1, state) +
            cageSuffix(context, row, col)
    }

    /**
     * The cells forming the Killer cage that contains (row, col),
     * as flat cell indices in reading order — or null when this
     * capture carried no usable cage lines (classic Solo, or a
     * capture the reconstruction didn't trust; see fromCapture).
     * Membership lives here, on the model, never inside [Cell]:
     * Cell equality drives move-diffing, and a cell's cage doesn't
     * change when its contents do.
     */
    fun cageMembers(row: Int, col: Int): List<Int>? {
        val ids = cageIds ?: return null
        val id = ids[row * cr + col]
        if (id < 0) return null
        return (0 until cr * cr).filter { ids[it] == id }
    }

    /**
     * The ", cage total …" suffix of a cell's label. With cage
     * membership reconstructed, every cell of the cage — not just
     * the anchor, the only cell that carries the printed total —
     * speaks the total and which cells form the cage (Fernando's
     * parity ask, 2026-11-06: the sum rule applies to the whole
     * cage). Without membership, the anchor keeps the total-only
     * suffix and other cells say nothing about cages.
     */
    private fun cageSuffix(context: Context, row: Int, col: Int): String {
        val members = cageMembers(row, col)
        val total = if (members != null) {
            members.firstNotNullOfOrNull { cells[it].cageTotal }
        } else {
            cellAt(row, col).cageTotal
        } ?: return ""
        return if (members != null && members.size > 1) {
            context.getString(
                R.string.tb_grid_cage_cells, total, describeCageMembers(context, members),
            )
        } else {
            context.getString(R.string.tb_grid_cage, total)
        }
    }

    /** "row 1, columns 2 to 4; row 2, column 3" — members grouped by row, columns as ranges. */
    private fun describeCageMembers(context: Context, members: List<Int>): String {
        val byRow = members.groupBy { it / cr }.toSortedMap()
        return byRow.entries.joinToString("; ") { (r, idxs) ->
            val cols = idxs.map { it % cr }.sorted()
            val ranges = mutableListOf<Pair<Int, Int>>()
            var start = cols.first()
            var prev = cols.first()
            for (c in cols.drop(1)) {
                if (c == prev + 1) {
                    prev = c
                } else {
                    ranges += start to prev
                    start = c
                    prev = c
                }
            }
            ranges += start to prev
            if (ranges.size == 1 && ranges[0].first == ranges[0].second) {
                context.getString(R.string.tb_grid_cage_row_col, r + 1, ranges[0].first + 1)
            } else {
                val colsText = ranges.joinToString(" and ") { (a, b) ->
                    if (a == b) (a + 1).toString()
                    else context.getString(R.string.tb_grid_cage_col_range, a + 1, b + 1)
                }
                context.getString(R.string.tb_grid_cage_row_cols, r + 1, colsText)
            }
        }
    }

    /** True when every cell of [unit] holds a digit and no digit repeats. */
    fun isComplete(unit: Unit): Boolean {
        val digits = unitCells(unit).map { cellAt(it / cr, it % cr).digit }
        return digits.all { it != null } && digits.distinct().size == digits.size
    }

    private fun unitCells(unit: Unit): List<Int> = when (unit) {
        is Unit.Row -> (0 until cr).map { unit.index * cr + it }
        is Unit.Column -> (0 until cr).map { it * cr + unit.index }
        is Unit.Box -> {
            val bc = blockCols ?: return emptyList()
            val br = blockRows ?: return emptyList()
            val across = cr / bc
            val topRow = (unit.index / across) * br
            val leftCol = (unit.index % across) * bc
            (0 until br).flatMap { dr ->
                (0 until bc).map { dc -> (topRow + dr) * cr + (leftCol + dc) }
            }
        }
    }

    /** The box (0-based, reading order) containing a cell, or null without blocks. */
    fun boxIndexOf(row: Int, col: Int): Int? {
        val bc = blockCols ?: return null
        val br = blockRows ?: return null
        return (row / br) * (cr / bc) + (col / bc)
    }

    /**
     * The units through (row, col) that are complete in this model
     * but were not complete in [before] — the completion events a
     * move just earned (Fernando's suggestion, 2026-11-06: spoken
     * once, attached to the move, never baked into cell labels).
     */
    fun newlyCompletedUnits(before: SoloGridModel, row: Int, col: Int): List<Unit> {
        if (before.cr != cr) return emptyList()
        val candidates = mutableListOf<Unit>(Unit.Row(row), Unit.Column(col))
        boxIndexOf(row, col)?.let { candidates += Unit.Box(it) }
        return candidates.filter { isComplete(it) && !before.isComplete(it) }
    }

    override fun equals(other: Any?): Boolean =
        other is SoloGridModel && cr == other.cr && cells.contentEquals(other.cells)

    override fun hashCode(): Int = 31 * cr + cells.contentHashCode()

    companion object {
        // Solo's colour indices (the enum in solo.c). The recorder
        // passes the engine's colour index through untouched
        // (GameView indexes its colour table with it).
        private const val COL_CLUE = 4
        private const val COL_USER = 5
        private const val COL_ERROR = 7
        private const val COL_PENCIL = 8
        private const val COL_KILLER = 9

        /**
         * Builds the model from one capture. [boardExtent] is the
         * engine's game size along one axis (Solo is square) and
         * [originX]/[originY] the board's offset within the capture
         * space (the frontend's centring offset). Returns null when
         * the numbers don't reconcile — the grid stays silent
         * rather than risk a wrong lattice mislabelling cells.
         */
        fun fromCapture(
            texts: List<DescribeRecorder.DrawnText>,
            cr: Int,
            boardExtent: Int,
            originX: Float,
            originY: Float,
            blockCols: Int? = null,
            blockRows: Int? = null,
            lines: List<DescribeRecorder.DrawnLine> = emptyList(),
        ): SoloGridModel? {
            if (cr < 2 || cr > 16) return null
            // Solve the tile size from Solo's exact extent formula
            // (SIZE = cr·tile + 2·(tile/2) + 1, integer divisions as
            // in solo.c); the extent determines the tile uniquely.
            val tile = listOf((boardExtent - 1) / (cr + 1), boardExtent / (cr + 1))
                .firstOrNull { it > 0 && cr * it + 2 * (it / 2) + 1 == boardExtent }
                ?: return null
            val border = tile / 2f
            val colCenters = FloatArray(cr) { originX + border + it * tile + tile / 2f }
            val rowCenters = FloatArray(cr) { originY + border + it * tile + tile / 2f }

            val digits = arrayOfNulls<String>(cr * cr)
            val givens = BooleanArray(cr * cr)
            val errors = BooleanArray(cr * cr)
            val pencils = Array(cr * cr) { mutableListOf<String>() }
            val cages = arrayOfNulls<String>(cr * cr)
            fun indexOf(t: DescribeRecorder.DrawnText): Int? {
                val r = nearest(rowCenters, t.y.toFloat()) ?: return null
                val c = nearest(colCenters, t.x.toFloat()) ?: return null
                // Reject texts that land nowhere near a cell centre
                // line (strays from other drawing) rather than
                // snapping them to a wrong cell.
                if (abs(rowCenters[r] - t.y) > tile || abs(colCenters[c] - t.x) > tile) {
                    return null
                }
                return r * cr + c
            }
            for (t in texts) {
                val i = indexOf(t) ?: continue
                when (t.colour) {
                    COL_CLUE -> {
                        digits[i] = spokenValue(t.text); givens[i] = true
                    }
                    COL_USER -> {
                        digits[i] = spokenValue(t.text)
                    }
                    COL_ERROR -> {
                        if (isCageTotalText(t, i % cr, tile, colCenters)) {
                            // A completely filled cage whose sum is
                            // wrong draws its total in COL_ERROR too
                            // (solo.c highlight bit 32). Size and
                            // position tell it from a real error
                            // digit (tile/4 at the top-left versus
                            // tile/2 centred); without this, the
                            // total is misread as an entered digit
                            // and no cell reports the cage's sum.
                            cages[i] = t.text
                        } else {
                            digits[i] = spokenValue(t.text); errors[i] = true
                        }
                    }
                    COL_PENCIL -> if (digits[i] == null) pencils[i] += spokenValue(t.text)
                    // A cage total sits near the top-left of its
                    // cage's first cell; nearest-centre assignment
                    // holds while the offset stays within a tile,
                    // which indexOf's tolerance enforces.
                    COL_KILLER -> cages[i] = t.text
                }
            }
            val cells = Array(cr * cr) { i ->
                Cell(
                    digit = digits[i],
                    given = givens[i],
                    error = errors[i],
                    pencils = pencils[i].distinct()
                        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE },
                    cageTotal = cages[i],
                )
            }
            val cageIds = reconstructCages(
                lines, cr, tile, colCenters, rowCenters,
                anyTotalCaptured = cages.any { it != null },
            )
            return SoloGridModel(
                cr, cells, colCenters, rowCenters, tile.toFloat(),
                blockCols, blockRows, cageIds,
            )
        }

        /**
         * True when a COL_ERROR text is a Killer cage total rather
         * than an error digit: solo.c draws totals at TILE/4 in the
         * cell's top-left and digits at TILE/2 centred, so a small
         * size plus an anchor clearly left of the cell's centre
         * separates the two with a wide margin at any tile size.
         */
        private fun isCageTotalText(
            t: DescribeRecorder.DrawnText,
            col: Int,
            tile: Int,
            colCenters: FloatArray,
        ): Boolean =
            t.size in 1..(tile / 3) && t.x.toFloat() < colCenters[col] - tile / 6f

        /**
         * Reconstructs Killer cage membership from the captured
         * cage segments, following the spike of 2026-11-08
         * (harness-proven exact on 355/355 generated boards and
         * 44/44 full-but-wrong boards): keep segments in COL_KILLER
         * or COL_ERROR (a full-but-wrong cage draws in COL_ERROR,
         * and recording colour 9 only merges two adjacent wrong
         * cages), drop segments shorter than tile/4 (the diagonal
         * corner stubs are exactly 3·GRIDEXTRA long and would fake
         * walls), attribute each segment to its owner cell by
         * midpoint and to a side by nearest cell edge — segments
         * are drawn inset inside their owner cell, never on the
         * lattice — then flood-fill cells across unwalled sides.
         *
         * Returns a cage id per cell, or null to fall back to
         * total-only labels whenever the data looks untrustworthy:
         * degenerate tile, no segments, no cage total captured, a
         * non-axis-aligned segment, a midpoint off the board, or a
         * fill that yields a single board-wide cage. Labels must
         * never state membership the capture didn't prove.
         */
        private fun reconstructCages(
            lines: List<DescribeRecorder.DrawnLine>,
            cr: Int,
            tile: Int,
            colCenters: FloatArray,
            rowCenters: FloatArray,
            anyTotalCaptured: Boolean,
        ): IntArray? {
            if (tile < 16 || !anyTotalCaptured) return null
            // Wall set, keyed (cell * 4 + side); sides 0=L, 1=R, 2=T, 3=B.
            val walls = HashSet<Int>()
            var kept = 0
            for (l in lines) {
                if (l.colour != COL_KILLER && l.colour != COL_ERROR) continue
                val dx = abs(l.x2 - l.x1)
                val dy = abs(l.y2 - l.y1)
                if (dx != 0f && dy != 0f) return null
                if (max(dx, dy) < tile / 4f) continue
                val mx = (l.x1 + l.x2) / 2f
                val my = (l.y1 + l.y2) / 2f
                val c = nearest(colCenters, mx) ?: return null
                val r = nearest(rowCenters, my) ?: return null
                if (abs(colCenters[c] - mx) > tile / 2f ||
                    abs(rowCenters[r] - my) > tile / 2f
                ) {
                    return null
                }
                val side = if (dy == 0f) {
                    if (my < rowCenters[r]) 2 else 3
                } else {
                    if (mx < colCenters[c]) 0 else 1
                }
                walls += (r * cr + c) * 4 + side
                kept++
            }
            if (kept == 0 || walls.isEmpty()) return null
            // (dx, dy, wall side on this cell, matching side on the neighbour)
            val steps = arrayOf(
                intArrayOf(-1, 0, 0, 1),
                intArrayOf(1, 0, 1, 0),
                intArrayOf(0, -1, 2, 3),
                intArrayOf(0, 1, 3, 2),
            )
            val ids = IntArray(cr * cr) { -1 }
            var nextId = 0
            val stack = ArrayDeque<Int>()
            for (start in 0 until cr * cr) {
                if (ids[start] >= 0) continue
                ids[start] = nextId
                stack.addLast(start)
                while (stack.isNotEmpty()) {
                    val cur = stack.removeLast()
                    val cx = cur % cr
                    val cy = cur / cr
                    for ((ox, oy, sideHere, sideThere) in steps) {
                        val nx = cx + ox
                        val ny = cy + oy
                        if (nx !in 0 until cr || ny !in 0 until cr) continue
                        val n = ny * cr + nx
                        if (ids[n] >= 0) continue
                        if ((cur * 4 + sideHere) in walls ||
                            (n * 4 + sideThere) in walls
                        ) {
                            continue
                        }
                        ids[n] = nextId
                        stack.addLast(n)
                    }
                }
                nextId++
            }
            if (nextId <= 1) return null
            return ids
        }

        /** Solo draws values past 9 as letters (a = 10, b = 11…); speak the number. */
        private fun spokenValue(text: String): String {
            if (text.length != 1) return text
            val ch = text[0]
            return when (ch) {
                in 'a'..'z' -> (ch - 'a' + 10).toString()
                in 'A'..'Z' -> (ch - 'A' + 10).toString()
                else -> text
            }
        }

        /** Index of the centre nearest [v], or null for an empty array. */
        private fun nearest(centers: FloatArray, v: Float): Int? =
            centers.indices.minByOrNull { abs(centers[it] - v) }
    }
}
