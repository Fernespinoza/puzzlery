package io.github.nandomango702.stpuzzlerelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-value tests for Pattern's clue geometry. The expected
 * numbers were produced by a C harness running pattern.c's own
 * macro block (TLBORDER / BORDER / GUTTER / SIZE / TOCOORD and the
 * draw_numbers strip extents) — not by re-deriving them here — so
 * this pins the Kotlin mirror to the engine's arithmetic.
 */
class PatternClueGeometryTest {

    // w, h, tilesize, SIZE(w), SIZE(h), originX, originY, leftStrip, topStrip
    private val goldens = listOf(
        intArrayOf(5, 5, 16, 160, 160, 68, 68, 60, 60),
        intArrayOf(5, 5, 24, 240, 240, 102, 102, 90, 90),
        intArrayOf(5, 5, 31, 309, 309, 131, 131, 116, 116),
        intArrayOf(5, 5, 40, 400, 400, 170, 170, 150, 150),
        intArrayOf(10, 10, 24, 384, 384, 126, 126, 114, 114),
        intArrayOf(15, 15, 16, 352, 352, 100, 100, 92, 92),
        intArrayOf(15, 15, 24, 528, 528, 150, 150, 138, 138),
        intArrayOf(15, 15, 31, 681, 681, 193, 193, 178, 178),
        intArrayOf(20, 20, 40, 1120, 1120, 290, 290, 270, 270),
        intArrayOf(25, 25, 24, 816, 816, 198, 198, 186, 186),
        intArrayOf(30, 40, 16, 640, 832, 148, 180, 140, 172),
        intArrayOf(30, 40, 31, 1239, 1611, 286, 348, 271, 333),
        intArrayOf(7, 11, 24, 288, 408, 102, 126, 90, 114),
        intArrayOf(7, 11, 40, 480, 680, 170, 210, 150, 190),
        intArrayOf(1, 1, 24, 120, 120, 78, 78, 66, 66),
        intArrayOf(40, 30, 24, 1248, 960, 270, 222, 258, 210),
    )

    @Test
    fun geometryMatchesPatternCMacros() {
        for (g in goldens) {
            val (w, h, ts) = Triple(g[0], g[1], g[2])
            assertEquals("SIZE(w) w=$w ts=$ts", g[3], PatternClueGeometry.size(w, ts))
            assertEquals("SIZE(h) h=$h ts=$ts", g[4], PatternClueGeometry.size(h, ts))
            assertEquals("originX w=$w ts=$ts", g[5], PatternClueGeometry.gridOrigin(w, ts))
            assertEquals("originY h=$h ts=$ts", g[6], PatternClueGeometry.gridOrigin(h, ts))
            assertEquals("leftStrip w=$w ts=$ts", g[7], PatternClueGeometry.leftStripWidth(w, ts))
            assertEquals("topStrip h=$h ts=$ts", g[8], PatternClueGeometry.topStripHeight(h, ts))
            val geom = PatternClueGeometry.compute(w, h, ts, g[3], g[4])
            assertNotNull("compute w=$w h=$h ts=$ts", geom)
            assertEquals(g[5], geom!!.gridOriginX)
            assertEquals(g[8], geom.topStripHeight)
        }
    }

    @Test
    fun flushBandGeometry() {
        // 15x15 at tilesize 24: strips 138, BORDER 18, content 120.
        val geom = PatternClueGeometry.compute(15, 15, 24, 528, 528)!!
        assertEquals(120f, PatternClueGeometry.leftContentWidth(geom), 0.001f)
        assertEquals(120f, PatternClueGeometry.topContentHeight(geom), 0.001f)
        // Content is exactly TLBORDER*TS — the strip minus BORDER.
        assertEquals(
            (PatternClueGeometry.tlBorder(15) * 24).toFloat(),
            PatternClueGeometry.leftContentWidth(geom), 0.001f,
        )
        // Scale 1.0: band thickness equals the content size.
        assertEquals(120f, PatternClueGeometry.bandThickness(120f, 1.0f), 0.001f)
        // Scale 0.5: band and offsets halve.
        assertEquals(60f, PatternClueGeometry.bandThickness(120f, 0.5f), 0.001f)
        val originY = 30f
        val bandTop = 1000f
        val stripBottom = (originY + geom.topStripHeight).toInt()
        // The natural strip-bottom clue maps to the band's inner edge
        // (content height = the natural one here).
        val content = PatternClueGeometry.topContentHeight(geom)
        assertEquals(
            bandTop + 120f,
            PatternClueGeometry.topClueAnchorY(stripBottom, originY, bandTop, geom, content, 1.0f),
            0.001f,
        )
        assertEquals(
            bandTop + 60f,
            PatternClueGeometry.topClueAnchorY(stripBottom, originY, bandTop, geom, content, 0.5f),
            0.001f,
        )
        // A clue 60 above the strip bottom sits 60 (resp. 30) inside
        // the inner edge at scale 1.0 (resp. 0.5).
        val mid = stripBottom - 60
        assertEquals(
            60f,
            PatternClueGeometry.topClueAnchorY(stripBottom, originY, bandTop, geom, content, 1.0f) -
                PatternClueGeometry.topClueAnchorY(mid, originY, bandTop, geom, content, 1.0f),
            0.001f,
        )
        assertEquals(
            30f,
            PatternClueGeometry.topClueAnchorY(stripBottom, originY, bandTop, geom, content, 0.5f) -
                PatternClueGeometry.topClueAnchorY(mid, originY, bandTop, geom, content, 0.5f),
            0.001f,
        )
        // Row anchor pad and scale clamping.
        assertEquals(4f, PatternClueGeometry.rowAnchorPad(24), 0.001f)
        assertEquals(0.45f, PatternClueGeometry.clampClueScale(0.2f), 0.001f)
        assertEquals(1.0f, PatternClueGeometry.clampClueScale(1.7f), 0.001f)
        assertEquals(0.7f, PatternClueGeometry.clampClueScale(0.7f), 0.001f)
    }

    @Test
    fun usedContentAndFillExtents() {
        // Synthetic captures on the 15x15 ts=24 geometry (strips
        // 138, content 120, grid origins 150), capture origin
        // (10, 30). Anchors follow pattern.c's draw_numbers: a
        // column stack's bottom clue is centred half a tile above
        // the strip bottom (rel y 126), each clue above it one
        // tile higher; row anchors sit at the strip's right edge
        // (rel x 138).
        val geom = PatternClueGeometry.compute(15, 15, 24, 528, 528)!!
        val ox = 10f
        val oy = 30f
        fun txt(x: Int, y: Int, size: Int, s: String) =
            DescribeRecorder.DrawnText(ox.toInt() + x, oy.toInt() + y, 0, size, 2, s)

        // Short stacks: column 2 has two clues (offsets 36 and 12
        // from the strip bottom), column 5 one; two row clues.
        val short = PatternClues.fromCapture(
            listOf(
                txt(210, 102, 20, "1"),
                txt(210, 126, 20, "3"),
                txt(282, 126, 20, "7"),
                txt(138, 186, 20, "2 1"),
                txt(138, 258, 20, "4"),
            ),
            geom, ox, oy,
        )
        // Used extent: max offset 36 + half the largest size (20/2).
        assertEquals(46f, short.usedTopContent, 0.001f)
        assertTrue(short.usedTopContent < PatternClueGeometry.topContentHeight(geom))
        // Fill extents stop a hair short of the grid edges (160 /
        // 180): row anchor 10+138 + gutter 12 - 24/8 = 157; bottom
        // column glyph 30+126 + 0.6*20 + 12 - 3 = 177.
        assertEquals(157f, short.naturalLeftFillRight!!, 0.001f)
        assertEquals(177f, short.naturalTopFillBottom!!, 0.001f)
        // With the used content height, the topmost clue's glyph
        // top lands exactly on the band's top edge, at scale 1.0
        // and at 0.5 (the glyph compresses with the band).
        val bandTop = 1000f
        val topmostY = oy.toInt() + 102
        assertEquals(
            bandTop,
            PatternClueGeometry.topClueAnchorY(
                topmostY, oy, bandTop, geom, short.usedTopContent, 1.0f
            ) - 20f / 2,
            0.001f,
        )
        assertEquals(
            bandTop,
            PatternClueGeometry.topClueAnchorY(
                topmostY, oy, bandTop, geom, short.usedTopContent, 0.5f
            ) - 20f * 0.5f / 2,
            0.001f,
        )
        // A full TLBORDER stack of tile-tall glyphs saturates the
        // clamp: used == the natural content height exactly.
        val full = PatternClues.fromCapture(
            listOf(126, 102, 78, 54, 30).map { txt(162, it, 24, "9") },
            geom, ox, oy,
        )
        assertEquals(120f, full.usedTopContent, 0.001f)
        // Glyphs larger than a tile would push past the natural
        // content; the clamp holds the used extent at it.
        val over = PatternClues.fromCapture(
            listOf(126, 102, 78, 54, 30).map { txt(162, it, 30, "9") },
            geom, ox, oy,
        )
        assertEquals(120f, over.usedTopContent, 0.001f)
        // One-sided captures: no column clues -> no used top
        // content and no top fill extent; no row clues -> no left
        // fill extent.
        val rowsOnly = PatternClues.fromCapture(
            listOf(txt(138, 186, 20, "2 1")), geom, ox, oy,
        )
        assertEquals(0f, rowsOnly.usedTopContent, 0.001f)
        assertNull(rowsOnly.naturalTopFillBottom)
        assertNotNull(rowsOnly.naturalLeftFillRight)
        val colsOnly = PatternClues.fromCapture(
            listOf(txt(210, 126, 20, "3")), geom, ox, oy,
        )
        assertNull(colsOnly.naturalLeftFillRight)
        assertNotNull(colsOnly.naturalTopFillBottom)
    }

    @Test
    fun visibleWindowFitting() {
        // Same synthetic captures as usedContentAndFillExtents:
        // 15x15 ts=24 (content 120, grid origin 150), origin
        // (10, 30). Column 2's stack tops out at offset 36,
        // column 5's single clue sits at offset 12; glyph size 20.
        val geom = PatternClueGeometry.compute(15, 15, 24, 528, 528)!!
        val ox = 10f
        val oy = 30f
        fun txt(x: Int, y: Int, size: Int, s: String) =
            DescribeRecorder.DrawnText(ox.toInt() + x, oy.toInt() + y, 0, size, 2, s)
        val short = PatternClues.fromCapture(
            listOf(
                txt(210, 102, 20, "1"),
                txt(210, 126, 20, "3"),
                txt(282, 126, 20, "7"),
                txt(138, 186, 20, "2 1"),
                txt(138, 258, 20, "4"),
            ),
            geom, ox, oy,
        )
        // Per-column extents: topmost offset + half that clue's
        // size; 0 for a column that drew nothing.
        assertEquals(46f, short.columnExtents[2], 0.001f)
        assertEquals(22f, short.columnExtents[5], 0.001f)
        assertEquals(0f, short.columnExtents[0], 0.001f)
        // Window fits over column ranges.
        assertEquals(
            short.usedTopContent, short.usedTopContentForColumns(0, 14), 0.001f
        )
        assertEquals(46f, short.usedTopContentForColumns(2, 2), 0.001f)
        assertEquals(22f, short.usedTopContentForColumns(3, 14), 0.001f)
        assertEquals(0f, short.usedTopContentForColumns(0, 1), 0.001f)
        assertEquals(0f, short.usedTopContentForColumns(5, 3), 0.001f)
        // The natural-content clamp still applies over a window.
        val over = PatternClues.fromCapture(
            listOf(126, 102, 78, 54, 30).map { txt(162, it, 30, "9") },
            geom, ox, oy,
        )
        assertEquals(120f, over.usedTopContentForColumns(0, 14), 0.001f)
        // Row widths are GameView-measured (Paint); the range max
        // over them is pure.
        val widths = floatArrayOf(10f, 40f, 25f, 0f, 55f)
        assertEquals(55f, PatternClueGeometry.maxWidthForRows(widths, 0, 4), 0.001f)
        assertEquals(40f, PatternClueGeometry.maxWidthForRows(widths, 0, 2), 0.001f)
        assertEquals(0f, PatternClueGeometry.maxWidthForRows(widths, 3, 3), 0.001f)
        assertEquals(0f, PatternClueGeometry.maxWidthForRows(widths, 4, 2), 0.001f)
        assertEquals(55f, PatternClueGeometry.maxWidthForRows(widths, -5, 99), 0.001f)
        // Visible line ranges: grid starts at 150, cells 24 wide.
        assertEquals(
            0..14, PatternClueGeometry.visibleLineRange(0f, 528f, 150f, 24, 15)
        )
        assertEquals(
            3..5, PatternClueGeometry.visibleLineRange(227f, 289f, 150f, 24, 15)
        )
        // A window ending exactly on a cell boundary still counts
        // the cell starting there (the draw culling's test).
        assertEquals(
            0..1, PatternClueGeometry.visibleLineRange(150f, 174f, 150f, 24, 15)
        )
        assertNull(PatternClueGeometry.visibleLineRange(0f, 100f, 150f, 24, 15))
        assertNull(PatternClueGeometry.visibleLineRange(600f, 700f, 150f, 24, 15))
    }

    @Test
    fun cornerMinimapMapping() {
        val g = PatternClueGeometry
        fun rect(l: Float, t: Float, r: Float, b: Float) =
            PatternClueGeometry.FloatRect(l, t, r, b)
        fun assertRect(
            expected: PatternClueGeometry.FloatRect,
            actual: PatternClueGeometry.FloatRect?,
        ) {
            assertNotNull(actual)
            assertEquals(expected.left, actual!!.left, 0.001f)
            assertEquals(expected.top, actual.top, 0.001f)
            assertEquals(expected.right, actual.right, 0.001f)
            assertEquals(expected.bottom, actual.bottom, 0.001f)
        }
        // Fit: the grid's aspect preserved, centred in the bounds.
        assertRect(
            rect(50f, 0f, 150f, 100f),
            g.minimapFitRect(100f, 100f, rect(0f, 0f, 200f, 100f)),
        )
        assertRect(
            rect(0f, 25f, 100f, 75f),
            g.minimapFitRect(200f, 100f, rect(0f, 0f, 100f, 100f)),
        )
        assertNull(g.minimapFitRect(0f, 100f, rect(0f, 0f, 100f, 100f)))
        // A 400x400 grid fitted into a 120x80 corner: scale 0.2,
        // letterboxed left/right.
        val grid = rect(0f, 0f, 400f, 400f)
        val map = g.minimapFitRect(400f, 400f, rect(0f, 0f, 120f, 80f))!!
        assertRect(rect(20f, 0f, 100f, 80f), map)
        // The whole grid visible: the indicator is the fitted rect.
        assertRect(map, g.minimapIndicatorRect(grid, rect(0f, 0f, 400f, 400f), map))
        // A quarter window at the grid's bottom-right maps to the
        // map's bottom-right quarter.
        assertRect(
            rect(60f, 40f, 100f, 80f),
            g.minimapIndicatorRect(grid, rect(200f, 200f, 400f, 400f), map),
        )
        // A window extending past the grid's edge is clamped to it.
        assertRect(
            rect(80f, 60f, 100f, 80f),
            g.minimapIndicatorRect(grid, rect(300f, 300f, 600f, 600f), map),
        )
        assertRect(
            rect(20f, 0f, 40f, 20f),
            g.minimapIndicatorRect(grid, rect(-100f, -100f, 100f, 100f), map),
        )
        // A window clear of the grid has no indicator.
        assertNull(
            g.minimapIndicatorRect(grid, rect(500f, 500f, 600f, 600f), map)
        )
    }

    @Test
    fun minimapIndicatorBandInset() {
        // Round 7 worked example, hand-computed (mirrors the
        // standalone harness). A 400x400 grid at game-space
        // origin (100, 100); a 1000x1000 view whose pinned bands
        // end at view x=200 / y=200; the example view-to-game
        // transform is the pure translation game = view - 60.
        // The inset window in game space is (140,140)-(940,940):
        // its left edge lands 40 units inside the grid, so with
        // the corner fit at scale 120/400 = 0.3 the indicator's
        // left edge sits at 40 * 0.3 = 12 — 10% into the fitted
        // rect, not at its left edge.
        val g = PatternClueGeometry
        fun rect(l: Float, t: Float, r: Float, b: Float) =
            PatternClueGeometry.FloatRect(l, t, r, b)
        fun assertRect(
            expected: PatternClueGeometry.FloatRect,
            actual: PatternClueGeometry.FloatRect?,
        ) {
            assertNotNull(actual)
            assertEquals(expected.left, actual!!.left, 0.001f)
            assertEquals(expected.top, actual.top, 0.001f)
            assertEquals(expected.right, actual.right, 0.001f)
            assertEquals(expected.bottom, actual.bottom, 0.001f)
        }
        val inset = g.bandInsetViewRect(1000f, 1000f, 200f, 200f)
        assertRect(rect(200f, 200f, 1000f, 1000f), inset)
        val grid = rect(100f, 100f, 500f, 500f)
        val map = g.minimapFitRect(400f, 400f, rect(0f, 0f, 120f, 120f))!!
        assertRect(rect(0f, 0f, 120f, 120f), map)
        val visible = rect(
            inset.left - 60f, inset.top - 60f,
            inset.right - 60f, inset.bottom - 60f,
        )
        assertRect(
            rect(12f, 12f, 120f, 120f),
            g.minimapIndicatorRect(grid, visible, map),
        )
        // The pre-fix full-view window clamps to the grid's own
        // edges: the indicator claims the whole map width.
        assertRect(
            rect(0f, 0f, 120f, 120f),
            g.minimapIndicatorRect(grid, rect(-60f, -60f, 940f, 940f), map),
        )
    }

    @Test
    fun minimapSourcePairing() {
        // Round 8: the minimap's thumbnail source and its
        // viewport indicator both derive from the ONE grid rect,
        // gridRectInGameCoords — the offscreen render's source
        // is that rect at the render scale; the indicator's grid
        // is that rect offset by the capture origin. (Round 6
        // sampled the view bitmap, which past fit zoom holds
        // only the visible window, and the two drifted apart.)
        val g = PatternClueGeometry
        fun rect(l: Float, t: Float, r: Float, b: Float) =
            PatternClueGeometry.FloatRect(l, t, r, b)
        fun assertRect(
            expected: PatternClueGeometry.FloatRect,
            actual: PatternClueGeometry.FloatRect?,
        ) {
            assertNotNull(actual)
            assertEquals(expected.left, actual!!.left, 0.001f)
            assertEquals(expected.top, actual.top, 0.001f)
            assertEquals(expected.right, actual.right, 0.001f)
            assertEquals(expected.bottom, actual.bottom, 0.001f)
        }
        // 15x15 ts=24: gridOrigin 150, tile 24, grid spans
        // (150,150)-(510,510) in game coordinates.
        val geom = g.compute(15, 15, 24, 528, 528)!!
        val gridGame = g.gridRectInGameCoords(geom)
        assertRect(rect(150f, 150f, 510f, 510f), gridGame)
        assertRect(rect(150f, 150f, 510f, 510f), g.minimapSrcRect(geom, 1f))
        assertRect(rect(75f, 75f, 255f, 255f), g.minimapSrcRect(geom, 0.5f))
        // The indicator's dip-space grid: the same rect shifted
        // by a capture origin of (10, 30).
        assertRect(
            rect(160f, 180f, 520f, 540f),
            rect(
                10f + gridGame.left, 30f + gridGame.top,
                10f + gridGame.right, 30f + gridGame.bottom,
            ),
        )
        // Render scale: full resolution up to the 1024 cap.
        assertEquals(1f, g.minimapRenderScale(528, 528), 0.001f)
        assertEquals(1f, g.minimapRenderScale(1024, 300), 0.001f)
        assertEquals(0.5f, g.minimapRenderScale(2048, 1024), 0.001f)
        assertEquals(1024f / 1025f, g.minimapRenderScale(1025, 10), 0.001f)
        // The panned configuration that failed on device: grid
        // 400x400 at origin (100,100), fitted into a 120x120
        // corner (scale 0.3); the band-inset window in game
        // space is (140,140)-(340,340) — 40 units into the grid
        // to 240 units in — so the indicator spans exactly
        // 40/400 .. 240/400 of the fitted rect: (12,12)-(72,72).
        val grid = rect(100f, 100f, 500f, 500f)
        val map = g.minimapFitRect(400f, 400f, rect(0f, 0f, 120f, 120f))!!
        val panned = g.minimapIndicatorRect(
            grid, rect(140f, 140f, 340f, 340f), map
        )
        assertRect(rect(12f, 12f, 72f, 72f), panned)
        assertEquals(
            40f / 400f, (panned!!.left - map.left) / map.width, 0.001f
        )
        assertEquals(
            240f / 400f, (panned.right - map.left) / map.width, 0.001f
        )
    }

    @Test
    fun computeRejectsMismatchedExtent() {
        // One pixel off in either axis must fail safe to no pinning.
        assertNull(PatternClueGeometry.compute(15, 15, 24, 529, 528))
        assertNull(PatternClueGeometry.compute(15, 15, 24, 528, 527))
        assertNull(PatternClueGeometry.compute(15, 15, 0, 528, 528))
        assertNull(PatternClueGeometry.compute(0, 15, 24, 528, 528))
    }

    @Test
    fun classificationSplitsRowsAndColumns() {
        // 5x5 at tilesize 24: gridOrigin 102, strips 90 (see goldens).
        val geom = PatternClueGeometry.compute(5, 5, 24, 240, 240)!!
        val ox = 10f
        val oy = 20f
        fun text(x: Int, y: Int, s: String) =
            DescribeRecorder.DrawnText(x, y, 0, 20, 2, s)
        val texts = listOf(
            // Column clue for column 2: centred over the column
            // (102 + 2*24 + 12 = 162 rel), inside the top strip.
            text(ox.toInt() + 162, oy.toInt() + 30, "3"),
            // Another for column 2, higher in the stack.
            text(ox.toInt() + 162, oy.toInt() + 60, "1"),
            // Column clue for column 0.
            text(ox.toInt() + 114, oy.toInt() + 30, "5"),
            // Row clue for row 1: right-aligned at the strip edge
            // (90 rel), centred over the row (102 + 24 + 12 = 138 rel).
            text(ox.toInt() + 90, oy.toInt() + 138, "2 1"),
            // Row clue for row 4.
            text(ox.toInt() + 90, oy.toInt() + 210, "4"),
            // A text in the grid area: Pattern never draws one; the
            // classifier must drop it, not misfile it.
            text(ox.toInt() + 150, oy.toInt() + 150, "X"),
        )
        val clues = PatternClues.fromCapture(texts, geom, ox, oy)
        assertEquals(2, clues.colClues[2].size)
        assertEquals("3", clues.colClues[2][0].text)
        assertEquals(1, clues.colClues[0].size)
        assertTrue(clues.colClues[1].isEmpty())
        assertEquals("2 1", clues.rowClues[1]?.text)
        assertEquals("4", clues.rowClues[4]?.text)
        assertNull(clues.rowClues[0])
        val all = clues.rowClues.filterNotNull().size +
            clues.colClues.sumOf { it.size }
        assertEquals("grid-area text must be dropped", 5, all)
    }
}
