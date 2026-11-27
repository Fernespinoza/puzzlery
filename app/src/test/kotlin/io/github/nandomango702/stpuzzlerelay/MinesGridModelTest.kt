package io.github.nandomango702.stpuzzlerelay

import io.github.nandomango702.stpuzzlerelay.DescribeRecorder.DrawnLine
import io.github.nandomango702.stpuzzlerelay.DescribeRecorder.DrawnPoly
import io.github.nandomango702.stpuzzlerelay.DescribeRecorder.DrawnRect
import io.github.nandomango702.stpuzzlerelay.DescribeRecorder.DrawnText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mines TalkBack grid model (v0.7.0, Phase 2 tranche 3):
 * board state read from the primitives mines.c draws, never
 * inferred. Mirrors the standalone harness checks (which also
 * cover the spoken labels; those need a Context and so live
 * only in the harness). The synthetic boards below emit exactly
 * what draw_tile emits per square, at tile size 20.
 */
class MinesGridModelTest {

    // mines.c colour indices (game_colours enum order).
    private companion object {
        const val BG = 0
        const val BG2 = 1
        const val MINE = 10
        const val BANG = 11
        const val CROSS = 12
        const val FLAG = 13
        const val FLAGBASE = 14
        const val QUERY = 15
        const val HIGHLIGHT = 16
        const val LOWLIGHT = 17
        const val WRONGNUMBER = 18
    }

    private class Board(val w: Int, val h: Int, val border: Int = 30) {
        val ts = 20
        val ox = 5
        val oy = 7
        val texts = mutableListOf<DrawnText>()
        val lines = mutableListOf<DrawnLine>()
        val rects = mutableListOf<DrawnRect>()
        val polys = mutableListOf<DrawnPoly>()

        fun x(col: Int) = ox + border + col * ts
        fun y(row: Int) = oy + border + row * ts
        fun cx(col: Int) = x(col) + ts / 2
        fun cy(row: Int) = y(row) + ts / 2

        fun coveredBase(row: Int, col: Int) {
            val x = x(col)
            val y = y(row)
            rects += DrawnRect(x + 2, y + 2, ts - 4, ts - 4, BG)
            polys += DrawnPoly(
                listOf(x + ts - 1, y + ts - 1, x + ts - 1, y, x, y + ts - 1),
                LOWLIGHT, LOWLIGHT,
            )
            polys += DrawnPoly(
                listOf(x, y, x + ts - 1, y, x, y + ts - 1), HIGHLIGHT, HIGHLIGHT,
            )
        }

        fun covered(row: Int, col: Int) = coveredBase(row, col)

        fun flagged(row: Int, col: Int) {
            coveredBase(row, col)
            val x = x(col)
            val y = y(row)
            fun px(dx: Float, dy: Float) =
                listOf((x + ts * dx).toInt(), (y + ts * dy).toInt())
            polys += DrawnPoly(
                px(0.6f, 0.35f) + px(0.6f, 0.7f) + px(0.8f, 0.8f) +
                    px(0.25f, 0.8f) + px(0.55f, 0.7f) + px(0.55f, 0.35f),
                FLAGBASE, FLAGBASE,
            )
            polys += DrawnPoly(
                px(0.6f, 0.2f) + px(0.6f, 0.5f) + px(0.2f, 0.35f), FLAG, FLAG,
            )
        }

        fun question(row: Int, col: Int) {
            coveredBase(row, col)
            texts += DrawnText(cx(col), cy(row), 0, 15, QUERY, "?")
        }

        fun openBase(row: Int, col: Int, colour: Int) {
            val x = x(col)
            val y = y(row)
            rects += DrawnRect(x, y, ts, ts, colour)
            lines += DrawnLine(x.toFloat(), y.toFloat(), (x + ts - 1).toFloat(), y.toFloat(), LOWLIGHT)
            lines += DrawnLine(x.toFloat(), y.toFloat(), x.toFloat(), (y + ts - 1).toFloat(), LOWLIGHT)
        }

        fun blank(row: Int, col: Int) = openBase(row, col, BG2)

        fun number(row: Int, col: Int, n: Int, wrong: Boolean = false) {
            openBase(row, col, if (wrong) WRONGNUMBER else BG2)
            texts += DrawnText(cx(col), cy(row), 0, 17, 1 + n, n.toString())
        }

        fun minePrims(row: Int, col: Int, bang: Boolean) {
            openBase(row, col, if (bang) BANG else BG2)
            val cx = cx(col)
            val cy = cy(row)
            val r = ts / 2 - 3
            rects += DrawnRect(cx - r / 6, cy - r, 2 * (r / 6) + 1, 2 * r + 1, MINE)
            rects += DrawnRect(cx - r, cy - r / 6, 2 * r + 1, 2 * (r / 6) + 1, MINE)
            rects += DrawnRect(cx - r / 3, cy - r / 3, r / 3, r / 4, HIGHLIGHT)
        }

        fun wrongFlag(row: Int, col: Int) {
            minePrims(row, col, bang = false)
            val x = x(col)
            val y = y(row)
            for (dx in -1..1) {
                lines += DrawnLine(
                    (x + 3 + dx).toFloat(), (y + 2).toFloat(),
                    (x + ts - 3 + dx).toFloat(), (y + ts - 2).toFloat(), CROSS,
                )
                lines += DrawnLine(
                    (x + ts - 3 + dx).toFloat(), (y + 2).toFloat(),
                    (x + 3 + dx).toFloat(), (y + ts - 2).toFloat(), CROSS,
                )
            }
        }

        fun capture(totalMines: Int, sizeX: Int? = null) = MinesCapture(
            texts, lines, rects, polys,
            w, h, totalMines,
            sizeX ?: (2 * border + w * ts), 2 * border + h * ts,
            ts, ox.toFloat(), oy.toFloat(),
        )
    }

    private fun coveredBoard(w: Int, h: Int, border: Int = 30): Board {
        val b = Board(w, h, border)
        for (r in 0 until h) for (c in 0 until w) b.covered(r, c)
        return b
    }

    @Test
    fun freshBoardIsAllCovered() {
        val m = MinesGridModel.fromCapture(coveredBoard(4, 3).capture(totalMines = 3))!!
        assertTrue(
            (0 until 12).all { m.stateAt(it / 4, it % 4) == MinesCellState.COVERED }
        )
        assertEquals(0, m.flagCount)
        assertEquals(3, m.totalMines)
        assertFalse(m.gameOver)
        assertEquals(
            setOf(MinesGridAction.UNCOVER, MinesGridAction.FLAG),
            m.offeredActions(0, 1),
        )
    }

    @Test
    fun midGameStatesAndActions() {
        val b = Board(4, 3)
        b.blank(0, 0)
        b.number(0, 1, 1)
        b.flagged(0, 2)
        b.flagged(0, 3)
        b.number(1, 0, 3)
        b.number(1, 1, 2, wrong = true)
        b.question(1, 2)
        b.covered(1, 3)
        for (c in 0 until 4) b.covered(2, c)
        val m = MinesGridModel.fromCapture(b.capture(totalMines = 5))!!
        assertEquals(MinesCellState.BLANK, m.stateAt(0, 0))
        assertEquals(emptySet<MinesGridAction>(), m.offeredActions(0, 0))
        assertEquals(MinesCellState.NUMBER, m.stateAt(1, 0))
        assertEquals(setOf(MinesGridAction.CHORD), m.offeredActions(1, 0))
        assertEquals(MinesCellState.FLAGGED, m.stateAt(0, 2))
        assertEquals(setOf(MinesGridAction.FLAG), m.offeredActions(0, 2))
        assertEquals(2, m.flagCount)
        assertEquals(MinesCellState.QUESTION, m.stateAt(1, 2))
        assertEquals(setOf(MinesGridAction.UNCOVER), m.offeredActions(1, 2))
        assertFalse(m.gameOver)
    }

    @Test
    fun deathBoardExposesOnlyTheKillerMine() {
        val b = Board(4, 3)
        b.number(0, 0, 2)
        b.blank(0, 1)
        b.covered(0, 2)
        b.flagged(0, 3)
        b.minePrims(1, 0, bang = true)
        for (r in 1 until 3) for (c in 1 until 4) b.covered(r, c)
        b.covered(2, 0)
        val m = MinesGridModel.fromCapture(b.capture(totalMines = 5))!!
        assertEquals(MinesCellState.BANG, m.stateAt(1, 0))
        assertTrue(m.gameOver)
        assertTrue((0 until 12).all { m.offeredActions(it / 4, it % 4).isEmpty() })
        assertEquals(MinesCellState.FLAGGED, m.stateAt(0, 3))
    }

    @Test
    fun correctionsGridStates() {
        val b = Board(4, 3)
        b.number(0, 0, 2)
        b.blank(0, 1)
        b.minePrims(0, 2, bang = false)
        b.flagged(0, 3)
        b.wrongFlag(1, 0)
        b.minePrims(1, 1, bang = true)
        for (r in 1 until 3) for (c in 2 until 4) b.covered(r, c)
        b.covered(2, 0)
        b.covered(2, 1)
        val m = MinesGridModel.fromCapture(b.capture(totalMines = 5))!!
        assertEquals(MinesCellState.MINE, m.stateAt(0, 2))
        assertEquals(MinesCellState.WRONG_FLAG, m.stateAt(1, 0))
        assertEquals(MinesCellState.FLAGGED, m.stateAt(0, 3))
        assertTrue(m.gameOver)
    }

    @Test
    fun geometryAndCompletenessValidation() {
        val b = coveredBoard(4, 3)
        assertNull(MinesGridModel.fromCapture(b.capture(totalMines = 3, sizeX = 141)))
        val b8 = coveredBoard(4, 3, border = 8)
        assertTrue(MinesGridModel.fromCapture(b8.capture(totalMines = 3)) != null)
        val hole = Board(4, 3)
        for (r in 0 until 3) for (c in 0 until 4) {
            if (r != 2 || c != 3) hole.covered(r, c)
        }
        assertNull(MinesGridModel.fromCapture(hole.capture(totalMines = 3)))
    }

    @Test
    fun virtualIdAtCentres() {
        val m = MinesGridModel.fromCapture(coveredBoard(4, 3).capture(totalMines = 3))!!
        assertEquals(2 * 4 + 3, m.virtualIdAt(m.colCenters[3], m.rowCenters[2]))
        assertNull(m.virtualIdAt(-50f, -50f))
    }
}
