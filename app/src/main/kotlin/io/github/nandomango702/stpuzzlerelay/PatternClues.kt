package io.github.nandomango702.stpuzzlerelay

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Pinned clues for Pattern (v0.6.0 groundwork): when the board is
 * zoomed or panned, GameView re-draws the clue texts pinned to the
 * top/left edges of the game view, so the clues for the rows and
 * columns currently in view stay visible (Katana-style). The strips
 * are drawn inside the game view only — the bottom control bar is a
 * separate view and is never covered.
 *
 * Geometry: pattern.c sizes its clue margins purely from the board
 * dimensions and the tile size (macros at the top of pattern.c):
 *
 *   TLBORDER(d)  = d / 5 + 2
 *   BORDER       = 3 * TILE_SIZE / 4,  GUTTER = TILE_SIZE / 2
 *   TOCOORD(d,x) = BORDER + GUTTER + TILE_SIZE * (TLBORDER(d) + x)
 *   SIZE(d)      = 2*BORDER + GUTTER + TILE_SIZE * (TLBORDER(d) + d)
 *
 * (all C integer arithmetic). In draw_numbers, a row's clues are one
 * right-aligned text ending at x = BORDER + TLBORDER(w)*TILE_SIZE,
 * and a column's clues are texts centred over the column inside a
 * strip of height BORDER + TLBORDER(h)*TILE_SIZE. So every margin is
 * derivable on this side from (w, h, tilesize); the one engine fact
 * that is NOT derivable is the live tilesize, and that is what
 * crosses JNI (GameEngine.engineTilesize → midend_tilesize).
 *
 * [PatternClueGeometry.compute] refuses to build geometry whose
 * SIZE does not reproduce the engine's own reported extent exactly:
 * a mismatch fails safe to the current rendering (no pinning)
 * rather than misplacing clues.
 */
object PatternClueGeometry {

    data class Geometry(
        val w: Int,
        val h: Int,
        val tileSize: Int,
        /** Game-coordinate x of the grid's left edge: TOCOORD(w, 0). */
        val gridOriginX: Int,
        /** Game-coordinate y of the grid's top edge: TOCOORD(h, 0). */
        val gridOriginY: Int,
        /** Width of the row-clue strip: BORDER + TLBORDER(w)*TS. */
        val leftStripWidth: Int,
        /** Height of the column-clue strip: BORDER + TLBORDER(h)*TS. */
        val topStripHeight: Int,
    )

    fun tlBorder(d: Int): Int = d / 5 + 2

    fun border(tileSize: Int): Int = 3 * tileSize / 4

    fun gutter(tileSize: Int): Int = tileSize / 2

    fun size(d: Int, tileSize: Int): Int =
        2 * border(tileSize) + gutter(tileSize) + tileSize * (tlBorder(d) + d)

    fun gridOrigin(d: Int, tileSize: Int): Int =
        border(tileSize) + gutter(tileSize) + tileSize * tlBorder(d)

    fun leftStripWidth(w: Int, tileSize: Int): Int =
        border(tileSize) + tileSize * tlBorder(w)

    fun topStripHeight(h: Int, tileSize: Int): Int =
        border(tileSize) + tileSize * tlBorder(h)

    // --- Flush-band geometry (v0.6.0 pinned-clues rework) ---
    //
    // A pinned strip reproduces the board's natural strip, which
    // carries BORDER of dead space on its outer (screen-edge) side:
    // the row/column clue texts live in the TLBORDER*TS content at
    // the strip's inner side, and the BORDER band between the texts
    // and the screen edge is what the user sees as padding. A
    // pinned band therefore covers only the content, not the whole
    // natural strip, and each strip has a user scale in [0.45, 1.0]
    // that compresses the content further toward the edge.

    /** Lower bound on a strip's user scale (a 0.45 band still reads). */
    const val MIN_CLUE_SCALE = 0.45f

    /** Upper bound on a strip's user scale: the full content width. */
    const val MAX_CLUE_SCALE = 1.0f

    /** A stored or computed scale, clamped into the legal range. */
    fun clampClueScale(scale: Float): Float =
        scale.coerceIn(MIN_CLUE_SCALE, MAX_CLUE_SCALE)

    /**
     * Width of the clue content inside the left strip, in game
     * units: the strip minus its BORDER dead band. Equals
     * TLBORDER(w)*TS exactly.
     */
    fun leftContentWidth(geometry: Geometry): Float =
        (geometry.leftStripWidth - border(geometry.tileSize)).toFloat()

    /**
     * Height of the clue content inside the top strip, in game
     * units: TLBORDER(h)*TS exactly.
     */
    fun topContentHeight(geometry: Geometry): Float =
        (geometry.topStripHeight - border(geometry.tileSize)).toFloat()

    /**
     * Thickness of a pinned band, in game units, for [content] game
     * units of clue content shown at [scale]. Pure multiplication —
     * clamping belongs to the caller.
     */
    fun bandThickness(content: Float, scale: Float): Float = content * scale

    /**
     * Inset of a row clue's right-aligned anchor from the left
     * band's inner edge, in game units: keeps the glyphs a hair off
     * the grip.
     */
    fun rowAnchorPad(tileSize: Int): Float = tileSize / 6f

    /**
     * A column clue's distance from the bottom edge of the top
     * strip, in game units (its offset from the strip's natural
     * bottom edge, in the strip's own coordinates).
     */
    fun topClueOffsetFromBottom(
        clueY: Int,
        originY: Float,
        geometry: Geometry,
    ): Float = (originY + geometry.topStripHeight) - clueY

    /**
     * Game-y of a column clue's anchor when the top strip is pinned
     * at [bandTopY] (the game-y of the view's top edge) at [scale]:
     * the band's inner edge (bandTopY + [contentHeight]·scale)
     * minus the clue's offset from the strip bottom, compressed by
     * the scale. [contentHeight] is the content height the band is
     * sized to — the captured stacks' used extent
     * ([PatternClues.usedTopContent]), not necessarily the natural
     * TLBORDER height. A clue sitting on the strip's natural bottom
     * edge (offset 0) lands exactly on the band's inner edge.
     */
    fun topClueAnchorY(
        clueY: Int,
        originY: Float,
        bandTopY: Float,
        geometry: Geometry,
        contentHeight: Float,
        scale: Float,
    ): Float =
        bandTopY + bandThickness(contentHeight, scale) -
            topClueOffsetFromBottom(clueY, originY, geometry) * scale

    // --- Visible-window fitting (pinned-clues round 5) ---
    //
    // Katana's pinned strips fit the longest clue stack among the
    // lines currently in view, not the whole board's: pan to a
    // stretch of short stacks and the strip physically shrinks.
    // The helpers below are the pure arithmetic of that fit; the
    // per-line extents themselves are cached at capture time
    // (PatternClues.columnExtents for columns, Paint-measured
    // widths in GameView for rows).

    /**
     * The board lines (rows or columns) whose cell spans
     * intersect the visible window, as an inclusive index range,
     * or null when no line is visible. Line i spans
     * [gridStart] + i·[tileSize], +[tileSize] in the same
     * coordinates as [visibleStart]/[visibleEnd] (the capture's
     * dip space); a line merely touching the window's edge counts
     * as visible — the same test GameView's draw culling applies
     * per line.
     */
    fun visibleLineRange(
        visibleStart: Float,
        visibleEnd: Float,
        gridStart: Float,
        tileSize: Int,
        count: Int,
    ): IntRange? {
        if (count <= 0 || tileSize <= 0) return null
        val ts = tileSize.toFloat()
        val first = ceil((visibleStart - gridStart) / ts).toInt() - 1
        val last = floor((visibleEnd - gridStart) / ts).toInt()
        if (last < 0 || first > count - 1) return null
        val clampedFirst = first.coerceAtLeast(0)
        val clampedLast = last.coerceAtMost(count - 1)
        return if (clampedFirst <= clampedLast) {
            clampedFirst..clampedLast
        } else {
            null
        }
    }

    /**
     * The widest of [widths] over the inclusive index range
     * [first]..[last] — a row clue's measured width, cached per
     * row at capture time (widths need a Paint, so they cannot be
     * derived in this file). Indices are clamped to the array;
     * an empty range or array yields 0.
     */
    fun maxWidthForRows(widths: FloatArray, first: Int, last: Int): Float {
        val from = maxOf(first, 0)
        val to = minOf(last, widths.size - 1)
        if (from > to) return 0f
        var max = 0f
        for (i in from..to) {
            if (widths[i] > max) max = widths[i]
        }
        return max
    }

    // --- Corner minimap (pinned-clues round 6) ---
    //
    // When both strips are pinned, their overlap — the corner
    // square at the view's top-left, as wide as the left band and
    // as tall as the top band — shows a minimap of the whole
    // puzzle with a viewport indicator. The mapping arithmetic is
    // pure and lives here, over plain floats: this file stays free
    // of android.graphics so the standalone harness can compile
    // it. GameView converts its RectFs at the boundary.

    /** A plain-float rect, for the pure minimap maths. */
    data class FloatRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * The largest rect with the grid's aspect ratio
     * ([gridWidth] × [gridHeight]) that fits inside [bounds],
     * centred in the leftover space (letterboxed). Null when the
     * grid or the bounds have no positive extent.
     */
    fun minimapFitRect(gridWidth: Float, gridHeight: Float, bounds: FloatRect): FloatRect? {
        if (gridWidth <= 0f || gridHeight <= 0f) return null
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        val scale = minOf(bounds.width / gridWidth, bounds.height / gridHeight)
        val w = gridWidth * scale
        val h = gridHeight * scale
        val cx = (bounds.left + bounds.right) / 2f
        val cy = (bounds.top + bounds.bottom) / 2f
        return FloatRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /**
     * The view-space rect that actually shows board when both
     * strips are pinned (round 7): the full view inset by the
     * bands' inner edges — the left band's right edge and the
     * top band's bottom edge. The minimap's viewport indicator
     * must be computed from THIS window, not the full view: the
     * board area hidden beneath the bands is covered, not
     * visible, and the round-6 indicator — built from the full
     * view rect — overstated the visible grid region by exactly
     * the bands' thickness on the left and the top. The caller
     * maps the returned rect's corners through its view-to-game
     * transform to get the window in game coordinates.
     */
    fun bandInsetViewRect(
        viewWidth: Float,
        viewHeight: Float,
        leftBandRight: Float,
        topBandBottom: Float,
    ): FloatRect = FloatRect(leftBandRight, topBandBottom, viewWidth, viewHeight)

    /**
     * The viewport indicator inside a fitted minimap: [visible]
     * (the window currently on screen, in the same coordinates
     * as [grid]) intersected with [grid], mapped proportionally
     * into [map] (the fitted minimap rect, in view coordinates).
     * A window extending past the grid's edge is clamped to it;
     * null when the window does not intersect the grid at all,
     * or when the grid or the map has no positive extent.
     */
    fun minimapIndicatorRect(grid: FloatRect, visible: FloatRect, map: FloatRect): FloatRect? {
        if (grid.width <= 0f || grid.height <= 0f) return null
        if (map.width <= 0f || map.height <= 0f) return null
        val l = maxOf(grid.left, visible.left)
        val t = maxOf(grid.top, visible.top)
        val r = minOf(grid.right, visible.right)
        val b = minOf(grid.bottom, visible.bottom)
        if (r <= l || b <= t) return null
        val sx = map.width / grid.width
        val sy = map.height / grid.height
        return FloatRect(
            map.left + (l - grid.left) * sx,
            map.top + (t - grid.top) * sy,
            map.left + (r - grid.left) * sx,
            map.top + (b - grid.top) * sy,
        )
    }

    // --- Corner minimap source (pinned-clues round 8) ---
    //
    // Round 6 sampled the VIEW bitmap for the thumbnail. That
    // bitmap is view-sized and rendered through the settled
    // zoom, so past fit zoom it physically holds only the
    // visible window; the source rect clamped to it and
    // drawBitmap stretched window content across the whole map
    // while the indicator marked the true window fraction.
    // Round 8 renders the whole game into a dedicated offscreen
    // bitmap instead (GameView.renderPatternMinimap), whose
    // space is game coordinates at [minimapRenderScale]: the
    // game's extent in frame, its origin at the bitmap's. The
    // helpers below are the pairing that keeps the thumbnail
    // and the indicator referring to the same board region:
    // both derive from the ONE grid rect, [gridRectInGameCoords]
    // — the render's source is that rect at the render scale,
    // the indicator's grid is that rect offset by the capture's
    // centring origin into dip space.

    /** Longest side of the offscreen minimap render, in pixels. */
    const val MINIMAP_MAX_DIMENSION = 1024

    /**
     * The grid's rect in game coordinates: its top-left is
     * (gridOriginX, gridOriginY) and it spans w×h tiles. See the
     * round-8 note above for why this helper is shared.
     */
    fun gridRectInGameCoords(geometry: Geometry): FloatRect = FloatRect(
        geometry.gridOriginX.toFloat(),
        geometry.gridOriginY.toFloat(),
        (geometry.gridOriginX + geometry.w * geometry.tileSize).toFloat(),
        (geometry.gridOriginY + geometry.h * geometry.tileSize).toFloat(),
    )

    /**
     * The offscreen minimap render's source rect, in offscreen
     * bitmap pixels: [gridRectInGameCoords] at [scale] (the
     * render maps game coordinates onto the bitmap with exactly
     * this scale — see [minimapRenderScale]).
     */
    fun minimapSrcRect(geometry: Geometry, scale: Float): FloatRect {
        val grid = gridRectInGameCoords(geometry)
        return FloatRect(
            grid.left * scale, grid.top * scale,
            grid.right * scale, grid.bottom * scale,
        )
    }

    /**
     * The offscreen minimap render's scale, in offscreen pixels
     * per game unit: 1 (full dip resolution) unless the game's
     * extent ([extentW] × [extentH], the SIZE values [compute]
     * validated against the engine's reported extent) exceeds
     * [MINIMAP_MAX_DIMENSION] on its longest side, in which case
     * the render scales down proportionally to fit the cap.
     */
    fun minimapRenderScale(extentW: Int, extentH: Int): Float {
        val maxDim = maxOf(extentW, extentH)
        if (maxDim <= MINIMAP_MAX_DIMENSION) return 1f
        return MINIMAP_MAX_DIMENSION.toFloat() / maxDim
    }

    /**
     * Geometry for a board, or null when the derived extent does not
     * match the engine's reported extent ([extentW] × [extentH], from
     * GameEngine.gameSizeInGameCoords) — the fail-safe.
     */
    fun compute(w: Int, h: Int, tileSize: Int, extentW: Int, extentH: Int): Geometry? {
        if (w <= 0 || h <= 0 || tileSize <= 0) return null
        if (size(w, tileSize) != extentW || size(h, tileSize) != extentH) return null
        return Geometry(
            w = w,
            h = h,
            tileSize = tileSize,
            gridOriginX = gridOrigin(w, tileSize),
            gridOriginY = gridOrigin(h, tileSize),
            leftStripWidth = leftStripWidth(w, tileSize),
            topStripHeight = topStripHeight(h, tileSize),
        )
    }
}

/**
 * One board's clue texts, captured through a DescribeRecorder pass
 * and classified by anchor position against [geometry]. Captured
 * coordinates are the draw callbacks' own: game coordinates plus the
 * frontend's centring origin ([originX], [originY]) — the same space
 * GamePlay.captureBoardTexts reports for the TalkBack grid.
 */
class PatternClues private constructor(
    val geometry: PatternClueGeometry.Geometry,
    val originX: Float,
    val originY: Float,
    /** The single right-aligned clue text per board row; null if a row drew none. */
    val rowClues: List<DescribeRecorder.DrawnText?>,
    /** The clue texts per board column (a bottom-aligned stack, in draw order). */
    val colClues: List<List<DescribeRecorder.DrawnText>>,
    /**
     * The board's cell states, row-major, read from the same
     * capture's recorded fills by [PatternModePlanner.cellStates]
     * — the mode bar's ground truth for planning set-state
     * gestures. Null when the capture recorded no cell fills, in
     * which case the mode bar must not plan (see PatternMode.kt).
     */
    val cellStates: Array<PatternCellState>? = null,
) {
    /**
     * The column stacks' actual extent in game units: the largest
     * [PatternClueGeometry.topClueOffsetFromBottom] over all column
     * clues, plus half the largest column clue's size — so when the
     * band is sized to this, the topmost glyph's top lands exactly
     * on the band's top edge. Clamped to the strip's natural
     * content height; 0 when the board drew no column clues. The
     * natural strip reserves TLBORDER rows whatever stacks a board
     * actually has; sizing the pinned band to the used extent
     * instead is what removes the dead space at the screen edge.
     */
    val usedTopContent: Float = run {
        var maxOffset = 0f
        var maxSize = 0
        var any = false
        for (col in colClues) {
            for (clue in col) {
                any = true
                val offset = PatternClueGeometry.topClueOffsetFromBottom(
                    clue.y, originY, geometry
                )
                if (offset > maxOffset) maxOffset = offset
                if (clue.size > maxSize) maxSize = clue.size
            }
        }
        if (!any) {
            0f
        } else {
            (maxOffset + maxSize / 2f).coerceAtMost(
                PatternClueGeometry.topContentHeight(geometry)
            )
        }
    }

    /**
     * Each column's own stack extent in game units: the topmost
     * clue's [PatternClueGeometry.topClueOffsetFromBottom] plus
     * half that clue's size, so a band sized to the extent puts
     * that glyph's top exactly on the band's top edge (the same
     * convention as [usedTopContent], per column). 0 for a column
     * that drew no clues. Cached at capture time so the visible-
     * window fit (see [usedTopContentForColumns]) is a range scan,
     * never a re-measure. Not clamped per column — clamping to
     * the natural content height happens in
     * [usedTopContentForColumns].
     */
    val columnExtents: FloatArray = run {
        val extents = FloatArray(geometry.w)
        for (col in colClues.indices) {
            var topmost: DescribeRecorder.DrawnText? = null
            var topmostOffset = 0f
            for (clue in colClues[col]) {
                val offset = PatternClueGeometry.topClueOffsetFromBottom(
                    clue.y, originY, geometry
                )
                if (topmost == null || offset > topmostOffset) {
                    topmost = clue
                    topmostOffset = offset
                }
            }
            if (topmost != null) {
                extents[col] = topmostOffset + topmost.size / 2f
            }
        }
        extents
    }

    /**
     * The used top content over the inclusive column range
     * [first]..[last] — the visible-window fit for the top band:
     * the largest [columnExtents] in the range, clamped to the
     * strip's natural content height. 0 when the range is empty
     * or every column in it drew no clues. Over the full range
     * this is [usedTopContent].
     */
    fun usedTopContentForColumns(first: Int, last: Int): Float {
        val from = maxOf(first, 0)
        val to = minOf(last, columnExtents.size - 1)
        if (from > to) return 0f
        var max = 0f
        for (i in from..to) {
            if (columnExtents[i] > max) max = columnExtents[i]
        }
        return max.coerceAtMost(
            PatternClueGeometry.topContentHeight(geometry)
        )
    }

    /**
     * The dip-space x the left band's background fill must reach
     * to hide the natural row strip: the rightmost captured row
     * anchor (all row anchors share one x in practice; the max is
     * belt-and-braces), plus the gutter, minus a hair
     * (tileSize/8) — the natural strip's own extent, stopping
     * just short of the grid's left edge. Null when the board
     * drew no row clues. Derived from the captured texts
     * themselves: their positions demonstrably map correctly
     * through GameView's dipToView transform, where the
     * origin-derived grid edge did not reliably cover the strip
     * (its last digit leaked past the fill at shallow pans).
     */
    val naturalLeftFillRight: Float? = run {
        var maxAnchor: Int? = null
        for (clue in rowClues) {
            if (clue != null && (maxAnchor == null || clue.x > maxAnchor!!)) {
                maxAnchor = clue.x
            }
        }
        maxAnchor?.let {
            it.toFloat() + PatternClueGeometry.gutter(geometry.tileSize) -
                geometry.tileSize / 8f
        }
    }

    /**
     * The dip-space y the top band's background fill must reach
     * to hide the natural column strip: the bottommost captured
     * column glyph extent (anchor + 0.6·size — a vertically
     * centred glyph's bottom edge sits about that far below its
     * anchor), plus the gutter, minus a hair (tileSize/8),
     * stopping just short of the grid's top edge. Null when the
     * board drew no column clues. Text-derived for the same
     * reason as [naturalLeftFillRight]: the natural strip's
     * bottom glyph row leaked past the origin-derived fill.
     */
    val naturalTopFillBottom: Float? = run {
        var maxBottom: Float? = null
        for (col in colClues) {
            for (clue in col) {
                val bottom = clue.y + clue.size * 0.6f
                if (maxBottom == null || bottom > maxBottom!!) {
                    maxBottom = bottom
                }
            }
        }
        maxBottom?.let {
            it + PatternClueGeometry.gutter(geometry.tileSize) -
                geometry.tileSize / 8f
        }
    }

    companion object {
        fun fromCapture(
            texts: List<DescribeRecorder.DrawnText>,
            geometry: PatternClueGeometry.Geometry,
            originX: Float,
            originY: Float,
            rects: List<DescribeRecorder.DrawnRect> = emptyList(),
        ): PatternClues {
            val rows = arrayOfNulls<DescribeRecorder.DrawnText>(geometry.h)
            val cols = Array(geometry.w) { mutableListOf<DescribeRecorder.DrawnText>() }
            val ts = geometry.tileSize
            for (t in texts) {
                val relX = t.x - originX
                val relY = t.y - originY
                if (relY < geometry.gridOriginY && relX >= geometry.gridOriginX) {
                    // Column clue: anchor is centred over its column
                    // inside the top strip.
                    val col = ((relX - geometry.gridOriginX) / ts).toInt()
                    if (col in 0 until geometry.w) cols[col] += t
                } else if (relX < geometry.gridOriginX && relY >= geometry.gridOriginY) {
                    // Row clue: anchor is right-aligned at the strip's
                    // right edge, centred over its row.
                    val row = ((relY - geometry.gridOriginY) / ts).toInt()
                    if (row in 0 until geometry.h) rows[row] = t
                }
                // Any other text would sit in the grid area, where
                // Pattern draws no text at all; ignore it.
            }
            return PatternClues(
                geometry, originX, originY,
                rows.toList(), cols.map { it.toList() },
                PatternModePlanner.cellStates(rects, geometry, originX, originY),
            )
        }
    }
}
