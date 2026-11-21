package io.github.nandomango702.stpuzzlerelay

import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Standalone assertion harness for the flush-band geometry in
 * [PatternClueGeometry] (the v0.6.0 pinned-clues rework). JUnit is
 * unavailable in the offline build environment, so — like the
 * previous pinned-clues tranche — the assertions run as a plain
 * main() compiled with the cached Kotlin compiler against the real
 * PatternClues.kt. The same cases are mirrored as JUnit tests in
 * PatternClueGeometryTest for networked environments.
 */
private var failures = 0
private var checks = 0

private fun check(name: String, actual: Float, expected: Float) {
    checks++
    if (abs(actual - expected) > 0.001f) {
        failures++
        println("FAIL $name: expected $expected, got $actual")
    } else {
        println("ok   $name = $actual")
    }
}

private fun checkTrue(name: String, cond: Boolean) {
    checks++
    if (!cond) {
        failures++
        println("FAIL $name")
    } else {
        println("ok   $name")
    }
}

private fun checkRect(
    name: String,
    actual: PatternClueGeometry.FloatRect?,
    l: Float, t: Float, r: Float, b: Float,
) {
    checks++
    if (actual == null ||
        abs(actual.left - l) > 0.001f || abs(actual.top - t) > 0.001f ||
        abs(actual.right - r) > 0.001f || abs(actual.bottom - b) > 0.001f
    ) {
        failures++
        println("FAIL $name: expected ($l, $t, $r, $b), got $actual")
    } else {
        println("ok   $name = $actual")
    }
}

fun main() {
    val g = PatternClueGeometry

    // Golden geometries (SIZE extents from PatternClueGeometryTest,
    // produced by pattern.c's own macros): w, h, ts, sizeW, sizeH.
    val goldens = listOf(
        intArrayOf(5, 5, 24, 240, 240),    // strips 90, border 18
        intArrayOf(15, 15, 24, 528, 528),  // strips 138, border 18
        intArrayOf(30, 40, 16, 640, 832),  // strips 140/172, border 12
        intArrayOf(20, 20, 40, 1120, 1120),// strips 270, border 30
    )
    for ((w, h, ts, sw, sh) in goldens) {
        val geom = g.compute(w, h, ts, sw, sh)
        checkTrue("compute $w x $h ts=$ts non-null", geom != null)
        geom!!
        // Content is exactly TLBORDER*TS: the natural strip minus
        // the BORDER dead band.
        check("leftContent $w ts=$ts", g.leftContentWidth(geom),
            (g.tlBorder(w) * ts).toFloat())
        check("topContent $h ts=$ts", g.topContentHeight(geom),
            (g.tlBorder(h) * ts).toFloat())
        check("content = strip - border $w ts=$ts",
            g.leftContentWidth(geom),
            (geom.leftStripWidth - g.border(ts)).toFloat())
        // Scale 1.0: band thickness equals the content size.
        check("band@1.0 $w ts=$ts",
            g.bandThickness(g.leftContentWidth(geom), 1.0f),
            g.leftContentWidth(geom))
        // Scale 0.5: band thickness halves.
        check("band@0.5 $w ts=$ts",
            g.bandThickness(g.leftContentWidth(geom), 0.5f),
            g.leftContentWidth(geom) / 2f)
    }

    // Top-strip anchor compression, on the 15x15 ts=24 geometry
    // (content height 120, strip height 138).
    val geom = g.compute(15, 15, 24, 528, 528)!!
    val originY = 30f
    val bandTop = 1000f
    val stripBottom = (originY + geom.topStripHeight).toInt() // 168
    // A clue on the strip's natural bottom edge maps to the band's
    // inner edge at scale 1.0 (content height = the natural one)...
    check("bottom clue -> inner edge @1.0",
        g.topClueAnchorY(
            stripBottom, originY, bandTop, geom, g.topContentHeight(geom), 1.0f
        ),
        bandTop + g.topContentHeight(geom))
    // ...and to the (compressed) inner edge at scale 0.5.
    check("bottom clue -> inner edge @0.5",
        g.topClueAnchorY(
            stripBottom, originY, bandTop, geom, g.topContentHeight(geom), 0.5f
        ),
        bandTop + g.topContentHeight(geom) * 0.5f)
    // A clue 60 units above the strip bottom: its distance from the
    // band's inner edge halves between scale 1.0 and scale 0.5.
    val mid = stripBottom - 60
    val innerAt1 = g.topClueAnchorY(
        stripBottom, originY, bandTop, geom, g.topContentHeight(geom), 1.0f
    )
    val innerAt05 = g.topClueAnchorY(
        stripBottom, originY, bandTop, geom, g.topContentHeight(geom), 0.5f
    )
    check("mid clue offset @1.0",
        innerAt1 - g.topClueAnchorY(
            mid, originY, bandTop, geom, g.topContentHeight(geom), 1.0f
        ), 60f)
    check("mid clue offset @0.5 (halved)",
        innerAt05 - g.topClueAnchorY(
            mid, originY, bandTop, geom, g.topContentHeight(geom), 0.5f
        ), 30f)

    // Row anchor line (the expression GameView uses): at scale 1.0
    // it sits BORDER + pad inside the natural strip's right edge.
    val topLeftX = 500f
    val anchorX = topLeftX + g.bandThickness(g.leftContentWidth(geom), 1.0f) -
        g.rowAnchorPad(geom.tileSize)
    check("row anchor line @1.0", anchorX,
        topLeftX + geom.leftStripWidth - g.border(24) - 24f / 6f)
    check("rowAnchorPad(24)", g.rowAnchorPad(24), 4f)
    check("rowAnchorPad(30)", g.rowAnchorPad(30), 5f)

    // Scale clamping.
    check("clamp below", g.clampClueScale(0.2f), PatternClueGeometry.MIN_CLUE_SCALE)
    check("clamp above", g.clampClueScale(1.7f), PatternClueGeometry.MAX_CLUE_SCALE)
    check("clamp inside", g.clampClueScale(0.7f), 0.7f)
    check("clamp at min", g.clampClueScale(0.45f), 0.45f)
    check("clamp at max", g.clampClueScale(1.0f), 1.0f)

    // --- Used content + text-derived fill extents ---
    //
    // Synthetic captures on the 15x15 ts=24 geometry above (strips
    // 138, content 120, grid origins 150), capture origin (10, 30).
    // Anchors follow pattern.c's draw_numbers: a column stack's
    // bottom clue is centred half a tile above the strip bottom
    // (rel y 126), each clue above it one tile higher; row anchors
    // sit at the strip's right edge (rel x 138).
    val ox = 10f
    fun txt(x: Int, y: Int, size: Int, s: String) =
        DescribeRecorder.DrawnText(ox.toInt() + x, originY.toInt() + y, 0, size, 2, s)

    // Short stacks: column 2 has two clues (offsets 36 and 12 from
    // the strip bottom), column 5 one; two row clues present.
    val short = PatternClues.fromCapture(
        listOf(
            txt(210, 102, 20, "1"),
            txt(210, 126, 20, "3"),
            txt(282, 126, 20, "7"),
            txt(138, 186, 20, "2 1"),
            txt(138, 258, 20, "4"),
        ),
        geom, ox, originY,
    )
    // Used extent: max offset 36 + half the largest size (20/2).
    check("usedTopContent: short stacks", short.usedTopContent, 46f)
    checkTrue(
        "usedTopContent < natural content",
        short.usedTopContent < g.topContentHeight(geom),
    )
    // Fill extents: row anchor 10+138, + gutter 12, - 24/8 = 157
    // (the grid's left edge sits at 10+150 = 160). Bottom column
    // glyph: anchor 30+126, + 0.6*20, + 12, - 3 = 177 (grid top
    // edge at 30+150 = 180).
    check("naturalLeftFillRight", short.naturalLeftFillRight ?: -1f, 157f)
    check("naturalTopFillBottom", short.naturalTopFillBottom ?: -1f, 177f)
    // With the used content height, the topmost clue's glyph top
    // lands exactly on the band's top edge — at scale 1.0, and at
    // 0.5 where the glyph compresses with the band.
    val topmostY = originY.toInt() + 102
    check(
        "topmost glyph top @1.0",
        g.topClueAnchorY(topmostY, originY, bandTop, geom, short.usedTopContent, 1.0f) -
            20f / 2,
        bandTop,
    )
    check(
        "topmost glyph top @0.5",
        g.topClueAnchorY(topmostY, originY, bandTop, geom, short.usedTopContent, 0.5f) -
            20f * 0.5f / 2,
        bandTop,
    )
    // A full TLBORDER stack of tile-tall glyphs saturates the
    // clamp: used == the natural content height exactly.
    val full = PatternClues.fromCapture(
        listOf(126, 102, 78, 54, 30).map { txt(162, it, 24, "9") },
        geom, ox, originY,
    )
    check("usedTopContent: full stack", full.usedTopContent, 120f)
    // Glyphs larger than a tile would push past the natural
    // content; the clamp holds the used extent at the natural
    // height.
    val over = PatternClues.fromCapture(
        listOf(126, 102, 78, 54, 30).map { txt(162, it, 30, "9") },
        geom, ox, originY,
    )
    check("usedTopContent: clamped", over.usedTopContent, 120f)
    // One-sided captures: no column clues -> no used top content
    // and no top fill extent; no row clues -> no left fill extent.
    val rowsOnly = PatternClues.fromCapture(
        listOf(txt(138, 186, 20, "2 1")), geom, ox, originY,
    )
    check("usedTopContent: no columns", rowsOnly.usedTopContent, 0f)
    checkTrue("naturalTopFillBottom absent", rowsOnly.naturalTopFillBottom == null)
    checkTrue("naturalLeftFillRight present", rowsOnly.naturalLeftFillRight != null)
    val colsOnly = PatternClues.fromCapture(
        listOf(txt(210, 126, 20, "3")), geom, ox, originY,
    )
    checkTrue("naturalLeftFillRight absent", colsOnly.naturalLeftFillRight == null)
    checkTrue("naturalTopFillBottom present", colsOnly.naturalTopFillBottom != null)

    // --- Visible-window fitting (round 5) ---
    //
    // Per-column extents on the "short" capture above: column 2's
    // stack tops out at offset 36 (extent 36 + 20/2 = 46), column
    // 5's single clue sits at offset 12 (extent 22); every other
    // column drew nothing.
    check("columnExtents[2]", short.columnExtents[2], 46f)
    check("columnExtents[5]", short.columnExtents[5], 22f)
    check("columnExtents[0] empty", short.columnExtents[0], 0f)
    // Window fits over column ranges: the full range reproduces
    // the board-wide used content; a range excluding the tallest
    // stack falls to the next; clueless and empty ranges are 0.
    check("window cols full == usedTopContent",
        short.usedTopContentForColumns(0, 14), short.usedTopContent)
    check("window cols single tallest",
        short.usedTopContentForColumns(2, 2), 46f)
    check("window cols excl tallest",
        short.usedTopContentForColumns(3, 14), 22f)
    check("window cols clueless",
        short.usedTopContentForColumns(0, 1), 0f)
    check("window cols empty range",
        short.usedTopContentForColumns(5, 3), 0f)
    // The clamp still applies over a window: the oversize stack
    // saturates at the natural content height.
    check("window cols clamped",
        over.usedTopContentForColumns(0, 14), 120f)

    // Row widths are GameView-measured (Paint); the range max
    // over them is pure.
    val widths = floatArrayOf(10f, 40f, 25f, 0f, 55f)
    check("maxWidth full", g.maxWidthForRows(widths, 0, 4), 55f)
    check("maxWidth sub-range", g.maxWidthForRows(widths, 0, 2), 40f)
    check("maxWidth zero row", g.maxWidthForRows(widths, 3, 3), 0f)
    check("maxWidth empty range", g.maxWidthForRows(widths, 4, 2), 0f)
    check("maxWidth indices clamped", g.maxWidthForRows(widths, -5, 99), 55f)

    // Visible line ranges on the 15x15 ts=24 geometry: the grid
    // starts at 150 (capture-relative), cells are 24 wide, the
    // last cell ends at 150 + 15*24 = 510.
    checkTrue("visibleRange full",
        g.visibleLineRange(0f, 528f, 150f, 24, 15) == (0..14))
    checkTrue("visibleRange mid window",
        g.visibleLineRange(227f, 289f, 150f, 24, 15) == (3..5))
    // A window ending exactly on a cell boundary still counts
    // the cell starting there (the draw culling's test).
    checkTrue("visibleRange touching counts",
        g.visibleLineRange(150f, 174f, 150f, 24, 15) == (0..1))
    checkTrue("visibleRange before grid",
        g.visibleLineRange(0f, 100f, 150f, 24, 15) == null)
    checkTrue("visibleRange after grid",
        g.visibleLineRange(600f, 700f, 150f, 24, 15) == null)

    // --- Corner minimap (round 6) ---
    //
    // Fit: the grid's aspect preserved, centred in the bounds.
    checkRect("minimap fit square in wide bounds",
        g.minimapFitRect(100f, 100f,
            PatternClueGeometry.FloatRect(0f, 0f, 200f, 100f)),
        50f, 0f, 150f, 100f)
    checkRect("minimap fit wide in square bounds",
        g.minimapFitRect(200f, 100f,
            PatternClueGeometry.FloatRect(0f, 0f, 100f, 100f)),
        0f, 25f, 100f, 75f)
    checkTrue("minimap fit degenerate grid",
        g.minimapFitRect(0f, 100f,
            PatternClueGeometry.FloatRect(0f, 0f, 100f, 100f)) == null)
    // A 400x400 grid fitted into a 120x80 corner: scale 0.2,
    // letterboxed left/right.
    val mmGrid = PatternClueGeometry.FloatRect(0f, 0f, 400f, 400f)
    val mmMap = g.minimapFitRect(400f, 400f,
        PatternClueGeometry.FloatRect(0f, 0f, 120f, 80f))!!
    checkRect("minimap fit 400 grid in 120x80 corner", mmMap,
        20f, 0f, 100f, 80f)
    // The whole grid visible: the indicator is the fitted rect.
    checkRect("minimap indicator full grid",
        g.minimapIndicatorRect(mmGrid,
            PatternClueGeometry.FloatRect(0f, 0f, 400f, 400f), mmMap),
        20f, 0f, 100f, 80f)
    // A quarter window at the grid's bottom-right maps to the
    // map's bottom-right quarter.
    checkRect("minimap indicator quarter bottom-right",
        g.minimapIndicatorRect(mmGrid,
            PatternClueGeometry.FloatRect(200f, 200f, 400f, 400f), mmMap),
        60f, 40f, 100f, 80f)
    // A window extending past the grid's bottom-right is clamped.
    checkRect("minimap indicator clamped bottom-right",
        g.minimapIndicatorRect(mmGrid,
            PatternClueGeometry.FloatRect(300f, 300f, 600f, 600f), mmMap),
        80f, 60f, 100f, 80f)
    // ...and past its top-left.
    checkRect("minimap indicator clamped top-left",
        g.minimapIndicatorRect(mmGrid,
            PatternClueGeometry.FloatRect(-100f, -100f, 100f, 100f), mmMap),
        20f, 0f, 40f, 20f)
    // A window clear of the grid has no indicator.
    checkTrue("minimap indicator empty intersection",
        g.minimapIndicatorRect(mmGrid,
            PatternClueGeometry.FloatRect(500f, 500f, 600f, 600f),
            mmMap) == null)

    // --- Minimap indicator band inset (round 7): worked example ---
    //
    // Hand-computed. A 400x400 grid sits at game-space origin
    // (100, 100): grid rect (100,100)-(500,500). The view is
    // 1000x1000 px; the pinned bands end at view x=200 (the left
    // band's right edge) and view y=200 (the top band's bottom
    // edge). For this example the view-to-game transform is the
    // pure translation game = view - 60 on both axes (a real
    // transform also scales; the inset arithmetic under test is
    // transform-independent — GameView maps the inset rect's
    // corners through viewToGame exactly as done by hand below).
    //
    // Step 1, the pure inset: (200,200)-(1000,1000).
    // Step 2, by hand through the translation: the window in
    // game space is (140,140)-(940,940) — its left edge lands
    // 40 units inside the grid (140 - 100 = 40).
    // Step 3: the corner fit of a 400x400 grid into a 120x120
    // corner is the whole corner, scale 120/400 = 0.3, so the
    // indicator's left edge sits at 40 * 0.3 = 12 — 10% into the
    // fitted rect, NOT at its left edge. Right and bottom clamp
    // to the grid's far edge: (500 - 100) * 0.3 = 120.
    val inset = g.bandInsetViewRect(1000f, 1000f, 200f, 200f)
    checkRect("minimap inset view rect", inset,
        200f, 200f, 1000f, 1000f)
    val wGrid = PatternClueGeometry.FloatRect(100f, 100f, 500f, 500f)
    val wMap = g.minimapFitRect(400f, 400f,
        PatternClueGeometry.FloatRect(0f, 0f, 120f, 120f))!!
    checkRect("minimap worked-example fit", wMap,
        0f, 0f, 120f, 120f)
    val wVisible = PatternClueGeometry.FloatRect(
        inset.left - 60f, inset.top - 60f,
        inset.right - 60f, inset.bottom - 60f)
    checkRect("minimap indicator inset window",
        g.minimapIndicatorRect(wGrid, wVisible, wMap),
        12f, 12f, 120f, 120f)
    // Contrast — the round-6 bug: the full view rect through the
    // same translation is game (-60,-60)-(940,940), which clamps
    // to the grid's own edges, so the indicator starts at the
    // map's left edge and claims the band-covered strip of board
    // is visible.
    checkRect("minimap indicator full-view window (the old bug)",
        g.minimapIndicatorRect(wGrid,
            PatternClueGeometry.FloatRect(-60f, -60f, 940f, 940f),
            wMap),
        0f, 0f, 120f, 120f)

    // --- Minimap source pairing (round 8) ---
    //
    // Round 6 sampled the VIEW bitmap for the thumbnail: view-
    // sized and rendered through the settled zoom, past fit zoom
    // it physically holds only the visible window, so the
    // clamped source stretched window content across the whole
    // map while the indicator marked the true window fraction.
    // Round 8 renders the whole game offscreen instead. The
    // pairing rests on ONE grid rect both paths derive from —
    // gridRectInGameCoords: the render's source is that rect at
    // the render scale; the indicator's grid is that rect offset
    // by the capture origin into dip space.
    //
    // On the 15x15 ts=24 geometry above (gridOrigin 150, tile
    // 24): the grid spans (150,150)-(150+360, 150+360).
    val gridGame = g.gridRectInGameCoords(geom)
    checkRect("gridRectInGameCoords 15x15 ts=24", gridGame,
        150f, 150f, 510f, 510f)
    // At render scale 1 the source rect IS the game-coords grid
    // rect; at 0.5 it is exactly that rect halved.
    checkRect("minimapSrcRect scale 1",
        g.minimapSrcRect(geom, 1f), 150f, 150f, 510f, 510f)
    checkRect("minimapSrcRect scale 0.5",
        g.minimapSrcRect(geom, 0.5f), 75f, 75f, 255f, 255f)
    // The indicator's dip-space grid is the same rect shifted by
    // the capture origin (10, 30): (160,180)-(520,540). Together
    // with the checks above — src/scale == gridGame and
    // gridDip - origin == gridGame — thumbnail and indicator
    // provably refer to the same board region.
    val gridDip8 = PatternClueGeometry.FloatRect(
        ox + gridGame.left, originY + gridGame.top,
        ox + gridGame.right, originY + gridGame.bottom)
    checkRect("minimap dip grid = game grid + origin", gridDip8,
        160f, 180f, 520f, 540f)
    // Render scale: full dip resolution up to the 1024 px cap
    // on the longest side, proportionally below it past the cap.
    check("minimapRenderScale small board",
        g.minimapRenderScale(528, 528), 1f)
    check("minimapRenderScale at cap",
        g.minimapRenderScale(1024, 300), 1f)
    check("minimapRenderScale over cap",
        g.minimapRenderScale(2048, 1024), 0.5f)
    check("minimapRenderScale just over cap",
        g.minimapRenderScale(1025, 10), 1024f / 1025f)

    // The panned configuration that actually failed on device:
    // the window strictly inside the grid on all four sides.
    // Same grid as the round-7 worked example — 400x400 at game
    // origin (100,100), fitted into the whole 120x120 corner
    // (wGrid / wMap above, fit scale 0.3) — but now the
    // band-inset window in game space is (140,140)-(340,340):
    // it starts 40 units into the grid and ends 240 units in,
    // so the indicator must span exactly 40/400 .. 240/400 of
    // the fitted rect: (12,12)-(72,72). And because the
    // thumbnail now shows the whole board (round 8), the board
    // content under those fractions is the content the player
    // sees in the main view — the pairing this section pins.
    val pannedWindow = PatternClueGeometry.FloatRect(140f, 140f, 340f, 340f)
    checkRect("minimap indicator panned window",
        g.minimapIndicatorRect(wGrid, pannedWindow, wMap),
        12f, 12f, 72f, 72f)
    val panned = g.minimapIndicatorRect(wGrid, pannedWindow, wMap)!!
    check("panned indicator left fraction",
        (panned.left - wMap.left) / wMap.width, 40f / 400f)
    check("panned indicator right fraction",
        (panned.right - wMap.left) / wMap.width, 240f / 400f)

    println("---")
    println("$checks checks, $failures failures")
    if (failures > 0) exitProcess(1)
    println("ALL PASSED")
}
