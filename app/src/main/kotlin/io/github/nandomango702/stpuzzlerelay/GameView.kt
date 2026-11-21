package io.github.nandomango702.stpuzzlerelay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_STYLUS
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_X
import android.view.KeyEvent.KEYCODE_DEL
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_FOCUS
import android.view.KeyEvent.KEYCODE_MENU
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.META_ALT_ON
import android.view.KeyEvent.META_SHIFT_ON
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent.ACTION_HOVER_MOVE
import android.view.MotionEvent.BUTTON_SECONDARY
import android.view.MotionEvent.BUTTON_STYLUS_PRIMARY
import android.view.MotionEvent.BUTTON_TERTIARY
import android.view.MotionEvent.TOOL_TYPE_STYLUS
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.ViewConfiguration
import android.widget.EdgeEffect
import android.widget.OverScroller
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withRotation
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import io.github.nandomango702.stpuzzlerelay.GameView.LimitDPIMode.LIMIT_AUTO
import io.github.nandomango702.stpuzzlerelay.GameView.LimitDPIMode.LIMIT_OFF
import io.github.nandomango702.stpuzzlerelay.GameView.LimitDPIMode.LIMIT_ON
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.PATTERN
import io.github.nandomango702.stpuzzlerelay.backend.SOLO
import io.github.nandomango702.stpuzzlerelay.backend.GameEngine.ViewCallbacks
import io.github.nandomango702.stpuzzlerelay.backend.UsedByJNI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class GameView(context: Context, attrs: AttributeSet?) : View(context, attrs), ViewCallbacks {
    private lateinit var parent: GamePlay
    private var bitmap: Bitmap = createBitmap(100, 100, BITMAP_CONFIG) // for safety
    private var canvas: Canvas = Canvas(bitmap)
    private var canvasRestoreJustAfterCreation: Int
    private val paint = Paint().apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.SQUARE
        strokeWidth = 1f // will be scaled with everything else as long as it's non-zero
    }
    private val checkerboardPaint = Paint()
    private val blitters: Array<Bitmap?> = arrayOfNulls(512)

    @ColorInt
    private var colours = IntArray(0)
    private var density = 1f

    enum class LimitDPIMode {
        LIMIT_OFF, LIMIT_AUTO, LIMIT_ON
    }

    var limitDpi = LIMIT_AUTO
    var w = 0
    var h = 0
    var wDip = 0
    var hDip = 0
    var longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private var hardwareKeys = ""
    var night = false
    var hasRightMouse = false
    var alwaysLongPress = false
    var mouseBackSupport = true

    private enum class TouchState {
        IDLE, WAITING_LONG_PRESS, DRAGGING, PINCH, PANNING
    }

    private var lastDrag: PointF? = null
    private var lastTouch = PointF(0f, 0f)
    private var touchState = TouchState.IDLE
    private var button = 0

    @ColorInt
    private var backgroundColour = defaultBackgroundColour
    private var waitingSpace = false
    private var rightMouseHeld = false
    private var touchStart: PointF? = null
    private var mousePos: PointF? = null
    private val maxDistSq = ViewConfiguration.get(context).scaledTouchSlop.toDouble().pow(2.0)
    var keysHandled = 0 // debug
    private var scaleDetector: ScaleGestureDetector? = null
    private val gestureDetector: GestureDetector
    private var overdrawX = 0
    private var overdrawY = 0
    private val zoomMatrix = Matrix()
    private val zoomInProgressMatrix = Matrix()
    private val inverseZoomMatrix = Matrix()
    private val tempDrawMatrix = Matrix()

    enum class DragMode {
        UNMODIFIED, REVERT_OFF_SCREEN, REVERT_TO_START, PREVENT
    }

    private var dragMode = DragMode.UNMODIFIED
    private val mScroller = OverScroller(context)
    private val edges = Array(4) { EdgeEffect(context) }
    private val currentScroll: PointF
        get() = viewToGame(PointF(w.toFloat() / 2, h.toFloat() / 2))
    private fun animateScroll() {
        mScroller.computeScrollOffset()
        val currentScroll: PointF = currentScroll
        scrollBy(mScroller.currX - currentScroll.x, mScroller.currY - currentScroll.y)
        if (mScroller.isFinished) {
            postOnAnimation {
                redrawForInitOrZoomChange()
                for (edge in edges) edge.onRelease()
            }
        } else {
            postOnAnimation(::animateScroll)
        }
    }

    fun setDragModeFor(whichBackend: BackendName?) {
        dragMode = whichBackend?.dragMode ?: DragMode.UNMODIFIED
        updateBoardLabel(whichBackend)
        refreshTalkBackGrid()
        stripGesture = StripGesture.NONE
        patternClues = null
        patternUsedLeftContent = 0f
        patternRowWidths = FloatArray(0)
        clearPatternMinimap()
        // The mode bar's selection is per game session: every
        // launch (new game, preset switch, game switch, restore)
        // comes through here and starts back at Fill.
        patternMode = PatternMode.FILL
        patternPanLast = null
        // No Pan hold-paint stroke or timer may leak across a
        // game switch into the new board.
        if (::parent.isInitialized) parent.handler.removeCallbacks(armPanPaint)
        panPaintStartCell = null
        panPaintTarget = null
        refreshPatternClues()
    }

    // --- Pattern mode bar (v0.6.0) ---
    //
    // The mode lives here because touch interpretation does: the
    // deferred touch press (sent when a drag starts or a tap
    // lands) is planned from the mode and the start cell's
    // captured state — see PatternModePlanner and
    // beginPatternStroke. GamePlay's bar only reports the user's
    // selection and reads the mode back to sync its highlight.

    /** The mode bar's current mode; Fill at every game launch. */
    var patternMode: PatternMode = PatternMode.FILL
        private set

    fun setPatternMode(mode: PatternMode) {
        patternMode = mode
    }

    /** Last finger position (view coords) of an in-progress Pan-mode pan. */
    private var patternPanLast: PointF? = null

    /**
     * Start cell (grid coords) of an armed Pan-mode hold-paint
     * stroke (see [armPanPaint]), or null when no such stroke is
     * in progress. Together with [panPaintTarget] it tells
     * [clampPanPaintGamePoint] whether forwarded drag points
     * need line-clamping.
     */
    private var panPaintStartCell: Pair<Int, Int>? = null

    /**
     * The drag state an armed Pan-mode hold-paint press set up —
     * the REVERSE-cycle step from the start cell's state
     * (modebar3: the hold runs the cycle backwards, so an
     * UNKNOWN target now comes from a black start cell). UNKNOWN
     * is the one case needing the frontend line clamp: the
     * engine snaps FULL/EMPTY drags to a line itself but fills
     * the whole rectangle for an UNKNOWN drag.
     */
    private var panPaintTarget: PatternCellState? = null

    /**
     * The grid cell (column, row) under a game-space point, or
     * null when there is no capture or the point falls outside
     * the grid.
     */
    private fun patternCellCoords(gamePoint: PointF): Pair<Int, Int>? {
        val clues = patternClues ?: return null
        val g = clues.geometry
        val col = floor((gamePoint.x - clues.originX - g.gridOriginX) / g.tileSize).toInt()
        val row = floor((gamePoint.y - clues.originY - g.gridOriginY) / g.tileSize).toInt()
        if (col !in 0 until g.w || row !in 0 until g.h) return null
        return Pair(col, row)
    }

    /**
     * The captured state of the cell under a game-space point, or
     * null when there is no capture, the capture holds no cell
     * states, or the point falls outside the grid — in every
     * case the caller falls back to the legacy gesture rather
     * than guess a state and paint the wrong thing.
     */
    private fun patternCellStateAt(gamePoint: PointF): PatternCellState? {
        val clues = patternClues ?: return null
        val states = clues.cellStates ?: return null
        val (col, row) = patternCellCoords(gamePoint) ?: return null
        return states[row * clues.geometry.w + col]
    }

    /**
     * Takes over a touch gesture that has just crossed the drag
     * threshold under the Pattern mode bar. In Pan mode no engine
     * press is sent at all — the gesture pans (the caller enters
     * PANNING and feeds moves to scrollBy); a Pan hold that arms
     * paint never reaches here, it is [armPanPaint]'s path. In a paint mode the
     * engine press the legacy path would have sent (LEFT_BUTTON)
     * is replaced by the planner's: the button whose press sets
     * the start cell's drag state to the mode's target, so the
     * engine's own drag — line-snapped for Fill/Mark, a rectangle
     * for Erase, previewed live by the redraw and committed as a
     * single move on release — paints the mode's state. The
     * planned button is left in [button], so the caller's
     * existing DRAG/RELEASE sends (and drag reverts) stay
     * consistent with the press the engine actually saw.
     *
     * Returns the state the gesture continues in, or null when
     * the mode bar doesn't take this gesture (not Pattern, or no
     * captured state for the start cell — e.g. it is off the
     * grid) and the caller should send the legacy press instead.
     */
    private fun beginPatternStroke(): TouchState? {
        if (parent.currentBackend != PATTERN) return null
        if (patternMode == PatternMode.PAN) {
            patternPanLast = touchStart
            return TouchState.PANNING
        }
        val start = touchStart ?: return null
        val gamePoint = viewToGame(start)
        val state = patternCellStateAt(gamePoint) ?: return null
        val swap = parent.isSwapLROn()
        when (val plan = PatternModePlanner.dragPlan(state, patternMode.target)) {
            is PatternModePlanner.DragPlan.Direct -> {
                button = PatternModePlanner.swapForSwapLR(plan.button, swap)
                parent.sendKey(gamePoint, button)
            }
            is PatternModePlanner.DragPlan.ResetThenPress -> {
                // The start cell already holds the target, so no
                // press can set up the drag from it. Tap it to
                // UNKNOWN first (a real engine move — this stroke
                // costs two undo steps, the planner's documented
                // price), then press for the drag proper. The
                // reset's capture refresh lands between the two
                // sends; the plan was computed from the pre-reset
                // state and stays correct.
                val reset = PatternModePlanner.swapForSwapLR(plan.resetButton, swap)
                parent.sendKey(gamePoint, reset)
                parent.sendKey(gamePoint, reset + RELEASE)
                button = PatternModePlanner.swapForSwapLR(plan.button, swap)
                parent.sendKey(gamePoint, button)
            }
        }
        return TouchState.DRAGGING
    }

    /**
     * Handles a tap (finger lifted without crossing the drag
     * threshold) under the Pattern mode bar. In a paint mode the
     * press is planned from the tapped cell's captured state so
     * the tap SETS the mode's target state; a tap on a cell
     * already holding the target sends nothing at all (set-state
     * means the cell is left exactly as it is, where the legacy
     * cycle tap would have changed it). In Pan mode (modebar2) a
     * tap instead CYCLES the cell one step — uncolored -> black
     * -> white -> uncolored — with a single press of the
     * planner's cycle button: no state read is needed, since the
     * cycle button steps every state in that direction, and one
     * press+release is one engine move. Returns true when the
     * tap was the mode bar's (the caller then lets the shared
     * release logic run if a press was sent), false to fall back
     * to the legacy tap.
     */
    private fun patternTapHandled(): Boolean {
        if (parent.currentBackend != PATTERN) return false
        val start = touchStart ?: return false
        val gamePoint = viewToGame(start)
        if (patternMode == PatternMode.PAN) {
            button = PatternModePlanner.swapForSwapLR(
                PatternModePlanner.panCycleButton(), parent.isSwapLROn()
            )
            parent.sendKey(gamePoint, button)
            touchState = TouchState.DRAGGING
            return true
        }
        val state = patternCellStateAt(gamePoint) ?: return false
        val planned = PatternModePlanner.tapButton(state, patternMode.target)
        if (planned == null) {
            touchState = TouchState.IDLE
            return true
        }
        button = PatternModePlanner.swapForSwapLR(planned, parent.isSwapLROn())
        parent.sendKey(gamePoint, button)
        touchState = TouchState.DRAGGING
        return true
    }

    /**
     * Pan mode's hold timer (modebar2; reverse-cycle press
     * since modebar3): posted on touch-down in Pan mode
     * alongside the (Pattern-gated) long-press runnable, for
     * the same long-press timeout. If the finger is still
     * down and within touch slop when it fires — the gesture is
     * still WAITING_LONG_PRESS — the hold ARMS paint mode: a
     * haptic tick, then the engine gets the hold's REVERSE cycle
     * press on the start cell (the tap steps the cycle forward,
     * the hold steps it backwards — blank -> white, black ->
     * blank, white -> black), so the engine's own drag preview
     * shows the pending state on that cell. From there the
     * gesture is an ordinary DRAGGING stroke through the
     * existing pipeline: drags paint the engine's line in the
     * reverse-cycle state (line-clamped by
     * [clampPanPaintGamePoint] when the target is UNKNOWN, the
     * one state the engine does not snap itself — reached from
     * a black start cell), and the release commits the stroke
     * as one engine move. If the
     * finger lifts after arming but before any drag, the
     * press+release on the start cell commits that cell one
     * REVERSE cycle step — holding a blank cell makes it white;
     * that is the defined behaviour for a plain hold.
     *
     * Cancellation is by removal, posted at every exit from
     * WAITING_LONG_PRESS: movement past slop before the
     * threshold disarms into a pan (no press is ever sent), a
     * second pointer down (pinch) cancels the timer, and a lift
     * before the threshold is a tap. A hold starting on a pinned
     * clue strip never posts this timer — the strip claim runs
     * before the detectors. Arming also requires the start
     * cell's captured state: without it the stroke's target is
     * unknowable and the UNKNOWN line clamp could not be
     * applied, so the gesture stays a tap/pan rather than risk
     * painting a rectangle where a line was specified. This is
     * deliberately NOT the engine long-press runnable
     * ([sendLongPress]) modebar1 gated off — that one flips the
     * shared button to RIGHT mid-gesture; this timer lives
     * entirely inside Pan mode's own handling.
     */
    private val armPanPaint: Runnable = Runnable {
        if (isScaleInProgress) return@Runnable
        if (touchState != TouchState.WAITING_LONG_PRESS) return@Runnable
        if (!::parent.isInitialized || parent.currentBackend != PATTERN) return@Runnable
        if (patternMode != PatternMode.PAN) return@Runnable
        val start = touchStart ?: return@Runnable
        val gamePoint = viewToGame(start)
        val cell = patternCellCoords(gamePoint) ?: return@Runnable
        val state = patternCellStateAt(gamePoint) ?: return@Runnable
        val cycle = PatternModePlanner.panHoldCycleButton()
        panPaintTarget = PatternModePlanner.cycleResult(cycle, state)
        panPaintStartCell = cell
        button = PatternModePlanner.swapForSwapLR(cycle, parent.isSwapLROn())
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        parent.sendKey(gamePoint, button)
        touchState = TouchState.DRAGGING
    }

    /**
     * The game point a Pan-mode hold-paint drag, release or
     * revert should be forwarded at. Only an UNKNOWN-target
     * stroke is adjusted: the engine fills the rectangle between
     * drag start and end for an UNKNOWN drag state, so the point
     * is replaced by the centre of the line-snapped end cell
     * ([PatternModePlanner.snapDragEnd], the engine's own snap
     * rule for FULL/EMPTY drags) — the rectangle then degenerates
     * to the 1-wide line. For every other gesture (including
     * FULL/EMPTY hold-paint strokes, whose drags the engine
     * snaps itself) the point passes through unchanged.
     */
    private fun clampPanPaintGamePoint(gamePoint: PointF): PointF {
        val start = panPaintStartCell
        if (panPaintTarget != PatternCellState.UNKNOWN || start == null) return gamePoint
        val clues = patternClues ?: return gamePoint
        val g = clues.geometry
        val col = floor((gamePoint.x - clues.originX - g.gridOriginX) / g.tileSize).toInt()
        val row = floor((gamePoint.y - clues.originY - g.gridOriginY) / g.tileSize).toInt()
        val (endCol, endRow) =
            PatternModePlanner.snapDragEnd(start.first, start.second, col, row, g.w, g.h)
        if (endCol == col && endRow == row) return gamePoint
        return PointF(
            clues.originX + g.gridOriginX + (endCol + 0.5f) * g.tileSize,
            clues.originY + g.gridOriginY + (endRow + 0.5f) * g.tileSize,
        )
    }

    // --- Pinned clues for Pattern (v0.6.0 groundwork) ---

    private var patternClues: PatternClues? = null

    /**
     * The row clues' board-wide used content width in game units:
     * the widest captured row text (see [patternRowWidths]),
     * clamped to the natural content width. Reset whenever
     * [patternClues] changes. Since the visible-window fit
     * (round 5) this is the clamp/fallback for the left band's
     * width, not the width itself. (The top band's used content
     * needs no measurement: it is pure geometry over the captured
     * anchors, [PatternClues.usedTopContent].)
     */
    private var patternUsedLeftContent = 0f

    /**
     * Every captured row clue's measured width in game units, by
     * row (0 for a row that drew no clue), measured once per
     * capture in [refreshPatternClues] — widths need a Paint, so
     * unlike the column extents they cannot live in PatternClues.
     * The left band's visible-window fit scans this array; no
     * measuring happens at draw time.
     */
    private var patternRowWidths = FloatArray(0)

    /** Scratch paint for measuring captured row clues. */
    private val clueMeasurePaint = Paint()

    /**
     * The corner minimap's own full-board render (round 8): the
     * whole game drawn into this offscreen bitmap with a fit
     * transform, refreshed alongside the clue capture in
     * [refreshPatternClues]. The view bitmap cannot be the
     * minimap's source — it is view-sized and rendered through
     * the settled zoom, so past fit zoom it physically holds
     * only the visible window; the round-6 source rect clamped
     * to it and stretched window content across the whole map
     * while the indicator marked the true window fraction.
     * Follows [patternClues]' lifecycle: null whenever there is
     * no current Pattern capture, and the minimap doesn't draw.
     */
    private var patternMinimapBitmap: Bitmap? = null

    /** Offscreen pixels per game unit in [patternMinimapBitmap]. */
    private var patternMinimapScale = 1f

    /**
     * Re-captures Pattern's clue texts + geometry (a recorder pass
     * through GamePlay, the Describe-board mechanism). No-op for
     * every other game. Refreshed on game start, on every move
     * (clue colours change with errors and the cursor) and on every
     * resize/zoom-settle (the tilesize, hence the margins, can
     * change). The capture is a few thousand cheap JNI calls at move
     * granularity, never per frame.
     */
    private fun refreshPatternClues() {
        if (isInEditMode || !::parent.isInitialized) return
        if (parent.currentBackend != PATTERN) {
            patternClues = null
            patternUsedLeftContent = 0f
            patternRowWidths = FloatArray(0)
            clearPatternMinimap()
            return
        }
        patternClues = parent.capturePatternClues()
        patternRowWidths = measurePatternRowWidths(patternClues)
        patternUsedLeftContent = patternClues?.let { clues ->
            PatternClueGeometry.maxWidthForRows(
                patternRowWidths, 0, patternRowWidths.size - 1
            ).coerceAtMost(PatternClueGeometry.leftContentWidth(clues.geometry))
        } ?: 0f
        renderPatternMinimap(patternClues)
    }

    /** Drops the minimap's offscreen render, if any. */
    private fun clearPatternMinimap() {
        patternMinimapBitmap?.recycle()
        patternMinimapBitmap = null
        patternMinimapScale = 1f
    }

    /**
     * Re-renders the whole game into [patternMinimapBitmap] with
     * a fit transform: the game's full extent (the SIZE values
     * [PatternClueGeometry.compute] validated against the
     * engine's reported extent) in frame at
     * [PatternClueGeometry.minimapRenderScale] — full dip
     * resolution up to the 1024 px cap. Runs at the clue
     * capture's triggers (game start, every move, resize/zoom-
     * settle); a forced full redraw at move granularity is the
     * capture's own cost class.
     *
     * The render reuses the view's own machinery: every engine
     * draw callback lands on [canvas] through [zoomMatrix], so
     * the swap below points both at the offscreen bitmap — the
     * matrix maps game coordinates (the capture's dip space
     * minus its centring origin) onto it, putting the game's
     * origin at the bitmap's — forces one redraw through the
     * same same-size resize the capture uses, and restores
     * everything. Pattern never allocates blitters (no blitter
     * calls in pattern.c), so no blitter state is involved, and
     * the real bitmap is untouched: it keeps showing the
     * current board, rendered by the normal path.
     */
    private fun renderPatternMinimap(clues: PatternClues?) {
        if (clues == null) {
            clearPatternMinimap()
            return
        }
        val g = clues.geometry
        val extentW = PatternClueGeometry.size(g.w, g.tileSize)
        val extentH = PatternClueGeometry.size(g.h, g.tileSize)
        val scale = PatternClueGeometry.minimapRenderScale(extentW, extentH)
        val bmpW = ceil(extentW * scale).toInt().coerceAtLeast(1)
        val bmpH = ceil(extentH * scale).toInt().coerceAtLeast(1)
        val bmp = patternMinimapBitmap
            ?.takeIf { it.width == bmpW && it.height == bmpH }
            ?: createBitmap(bmpW, bmpH, BITMAP_CONFIG).also { fresh ->
                patternMinimapBitmap?.recycle()
                patternMinimapBitmap = fresh
            }
        patternMinimapScale = scale
        bmp.eraseColor(backgroundColour)
        val oldCanvas = canvas
        val oldRestoreCount = canvasRestoreJustAfterCreation
        val oldZoom = Matrix(zoomMatrix)
        val oldInverse = Matrix(inverseZoomMatrix)
        val offscreen = Canvas(bmp)
        canvas = offscreen
        canvasRestoreJustAfterCreation = offscreen.save()
        zoomMatrix.setTranslate(-clues.originX, -clues.originY)
        zoomMatrix.postScale(scale, scale)
        offscreen.setMatrix(zoomMatrix)
        try {
            parent.gameViewResized()
        } finally {
            canvas = oldCanvas
            canvasRestoreJustAfterCreation = oldRestoreCount
            zoomMatrix.set(oldZoom)
            inverseZoomMatrix.set(oldInverse)
        }
    }

    /**
     * Every captured row clue's width, in game units, measured
     * with the paint settings [drawPinnedText] draws with — the
     * clue's own text size (scale 1 in game units) and the
     * typeface its TEXT_MONO flag selects. One entry per board
     * row; 0 for a row that drew no clue, and an empty array when
     * there is no capture.
     */
    private fun measurePatternRowWidths(clues: PatternClues?): FloatArray {
        clues ?: return FloatArray(0)
        val widths = FloatArray(clues.rowClues.size)
        for (row in clues.rowClues.indices) {
            val clue = clues.rowClues[row] ?: continue
            clueMeasurePaint.typeface =
                if (clue.flags and TEXT_MONO != 0) Typeface.MONOSPACE else Typeface.DEFAULT
            clueMeasurePaint.textSize = clue.size.toFloat()
            widths[row] = clueMeasurePaint.measureText(clue.text)
        }
        return widths
    }

    /** Used clue contents of the two bands, fitted to the visible window. */
    private data class VisibleClueContents(val left: Float, val top: Float)

    /**
     * The used contents the pinned bands are sized to, fitted to
     * the rows and columns currently in view (round 5, Katana's
     * fit): the top content is the largest captured column extent
     * among the visible columns, the left content the widest
     * measured row clue among the visible rows (clamped to the
     * board-wide [patternUsedLeftContent]). The visible window is
     * the view rect mapped through [viewToGame] — the same
     * transform inputs the draw path's per-line culling uses.
     * Recomputed per call from the cached capture; the per-call
     * work is two range scans over at most w+h cached floats.
     */
    private fun visibleClueContents(): VisibleClueContents {
        val clues = patternClues ?: return VisibleClueContents(0f, 0f)
        val g = clues.geometry
        val topLeft = viewToGame(PointF(0f, 0f))
        val bottomRight = viewToGame(PointF(w.toFloat(), h.toFloat()))
        val cols = PatternClueGeometry.visibleLineRange(
            topLeft.x, bottomRight.x,
            clues.originX + g.gridOriginX, g.tileSize, g.w,
        )
        val rows = PatternClueGeometry.visibleLineRange(
            topLeft.y, bottomRight.y,
            clues.originY + g.gridOriginY, g.tileSize, g.h,
        )
        val top = cols?.let {
            clues.usedTopContentForColumns(it.first, it.last)
        } ?: 0f
        val left = rows?.let {
            PatternClueGeometry.maxWidthForRows(
                patternRowWidths, it.first, it.last
            ).coerceAtMost(patternUsedLeftContent)
        } ?: 0f
        return VisibleClueContents(left, top)
    }

    /**
     * Per-strip user scales for the pinned clue bands (Katana-style
     * adjustment): 1.0 shows the full clue content, 0.45 the most
     * compressed band. Persisted in a dedicated SharedPreferences
     * file — NOT the default preferences, so the backup export
     * (which snapshots default prefs) never sees these keys.
     */
    private var topClueScale = 1f
    private var leftClueScale = 1f
    private var clueScalesLoaded = false

    private fun loadClueScales() {
        if (clueScalesLoaded) return
        clueScalesLoaded = true
        if (isInEditMode) return
        val prefs = context.getSharedPreferences(PATTERN_CLUES_PREFS, Context.MODE_PRIVATE)
        topClueScale = PatternClueGeometry.clampClueScale(
            prefs.getFloat(PREF_CLUE_SCALE_TOP, 1f)
        )
        leftClueScale = PatternClueGeometry.clampClueScale(
            prefs.getFloat(PREF_CLUE_SCALE_LEFT, 1f)
        )
    }

    private fun persistClueScales() {
        if (isInEditMode) return
        context.getSharedPreferences(PATTERN_CLUES_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_CLUE_SCALE_TOP, topClueScale)
            .putFloat(PREF_CLUE_SCALE_LEFT, leftClueScale)
            .apply()
    }

    /** Screen-space rects of the currently pinned clue bands. */
    private data class PinnedBands(val left: RectF?, val top: RectF?)

    /**
     * The pinned bands' screen rects, recomputed from the same
     * inputs the draw path uses (the captured clues, the view's
     * game-space top-left, the dip→view transform, the current
     * scales). One source of truth for drawing and for touch: a
     * strip contributes a rect only while it is actually pinned —
     * its natural position scrolled off the view's top/left edge.
     * Bands sit flush on the view's edges and cover only the clue
     * content actually present at the strip's user scale — not the
     * natural strip's BORDER dead band, and not the TLBORDER
     * maximum it reserves. The content is fitted to the visible
     * window (see [visibleClueContents]): the longest stack among
     * the lines in view, so a band shrinks and grows again as the
     * board pans under it. A strip whose visible used content is
     * 0 (no clues captured on that axis among the visible lines)
     * contributes no band at all.
     */
    private fun pinnedBandRects(): PinnedBands {
        val clues = patternClues ?: return PinnedBands(null, null)
        loadClueScales()
        val visible = visibleClueContents()
        val topLeft = viewToGame(PointF(0f, 0f))
        val left = if (visible.left > 0f && topLeft.x > clues.originX + 0.5f) {
            val innerX = dipToView(
                PointF(
                    topLeft.x + PatternClueGeometry.bandThickness(
                        visible.left, leftClueScale
                    ),
                    0f,
                )
            ).x
            RectF(0f, 0f, innerX, h.toFloat())
        } else {
            null
        }
        val top = if (visible.top > 0f && topLeft.y > clues.originY + 0.5f) {
            val innerY = dipToView(
                PointF(
                    0f,
                    topLeft.y + PatternClueGeometry.bandThickness(
                        visible.top, topClueScale
                    ),
                )
            ).y
            RectF(0f, 0f, w.toFloat(), innerY)
        } else {
            null
        }
        return PinnedBands(left, top)
    }

    private val gripPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /** Minimap image paint: bilinear filtering for the downscale. */
    private val minimapBitmapPaint = Paint().apply {
        isFilterBitmap = true
    }

    private val minimapBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val minimapViewportPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // --- Touch on the pinned clue bands ---
    //
    // A pinned band is a touch zone of its own: dragging near its
    // inner edge RESIZES the strip (its user scale), dragging
    // elsewhere on it PANS the board along the strip's axis (the
    // Katana handles). A gesture claimed by a band is routed here
    // wholesale — the engine and the gesture detectors never see
    // its events, so no engine key is sent, no long-press is ever
    // posted, and touchState stays IDLE throughout.

    private enum class StripGesture {
        NONE, RESIZE_LEFT, RESIZE_TOP, PAN_LEFT, PAN_TOP
    }

    private var stripGesture = StripGesture.NONE
    private var stripDownPoint = PointF(0f, 0f)
    private var stripLastPoint = PointF(0f, 0f)
    private var stripStartScale = 1f

    /**
     * Claims a single-pointer ACTION_DOWN that landed on a pinned
     * band. The top band is tested first: it is drawn last, so in
     * the corner overlap it is the one on top. Returns true when
     * the gesture was claimed.
     */
    private fun claimStripGesture(event: MotionEvent): Boolean {
        if (patternClues == null || colours.isEmpty()) return false
        val bands = pinnedBandRects()
        val x = event.x
        val y = event.y
        val edgeZone = STRIP_EDGE_ZONE_DP * resources.displayMetrics.density
        bands.top?.let { band ->
            if (band.contains(x, y)) {
                stripDownPoint = PointF(x, y)
                stripLastPoint = PointF(x, y)
                stripStartScale = topClueScale
                stripGesture = if (band.bottom - y <= edgeZone) {
                    StripGesture.RESIZE_TOP
                } else {
                    StripGesture.PAN_TOP
                }
                return true
            }
        }
        bands.left?.let { band ->
            if (band.contains(x, y)) {
                stripDownPoint = PointF(x, y)
                stripLastPoint = PointF(x, y)
                stripStartScale = leftClueScale
                stripGesture = if (band.right - x <= edgeZone) {
                    StripGesture.RESIZE_LEFT
                } else {
                    StripGesture.PAN_LEFT
                }
                return true
            }
        }
        return false
    }

    /**
     * The band's screen thickness (px) at scale 1.0, for resize
     * maths: the strip's visible-window USED content (the same
     * extent the band is currently sized to) through the dip→view
     * transform, so a resize drag's sensitivity matches the band
     * on screen.
     */
    private fun stripContentScreenPx(top: Boolean): Float {
        patternClues ?: return 0f
        val visible = visibleClueContents()
        val topLeft = viewToGame(PointF(0f, 0f))
        return if (top) {
            dipToView(
                PointF(0f, topLeft.y + visible.top)
            ).y
        } else {
            dipToView(
                PointF(topLeft.x + visible.left, 0f)
            ).x
        }
    }

    /** Routes one event of a claimed strip gesture. Always consumes it. */
    private fun handleStripGestureEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                when (stripGesture) {
                    StripGesture.RESIZE_LEFT -> {
                        val contentPx = stripContentScreenPx(top = false)
                        if (contentPx > 0f) {
                            // Away from the screen edge grows the
                            // band; toward it shrinks the band.
                            leftClueScale = PatternClueGeometry.clampClueScale(
                                stripStartScale +
                                    (event.x - stripDownPoint.x) / contentPx
                            )
                            invalidate()
                        }
                    }
                    StripGesture.RESIZE_TOP -> {
                        val contentPx = stripContentScreenPx(top = true)
                        if (contentPx > 0f) {
                            topClueScale = PatternClueGeometry.clampClueScale(
                                stripStartScale +
                                    (event.y - stripDownPoint.y) / contentPx
                            )
                            invalidate()
                        }
                    }
                    StripGesture.PAN_LEFT ->
                        // Scroll distances, in GestureDetector's
                        // convention (last − current), so the board
                        // follows the finger like a normal scroll.
                        scrollBy(0f, stripLastPoint.y - event.y)
                    StripGesture.PAN_TOP ->
                        scrollBy(stripLastPoint.x - event.x, 0f)
                    StripGesture.NONE -> {}
                }
                stripLastPoint = PointF(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endStripGesture()
            else -> {}
        }
        return true
    }

    /**
     * Ends a strip gesture, committing it the way a lift commits a
     * pinch: a resize persists its scale; a pan bakes the in-progress
     * scroll into the zoom matrix via a full redraw.
     */
    private fun endStripGesture() {
        when (stripGesture) {
            StripGesture.RESIZE_LEFT, StripGesture.RESIZE_TOP -> persistClueScales()
            StripGesture.PAN_LEFT, StripGesture.PAN_TOP -> {
                if (mScroller.isFinished) {
                    redrawForInitOrZoomChange()
                    for (edge in edges) edge.onRelease()
                }
            }
            StripGesture.NONE -> {}
        }
        stripGesture = StripGesture.NONE
    }

    // --- TalkBack virtual grid (Phase 2; Solo first) ---

    private var talkBackGrid: SoloTalkBackGrid? = null

    /**
     * Installs the Solo TalkBack grid while touch exploration is on
     * and Solo is the running game; removes it otherwise. Called at
     * game start (setDragModeFor) and whenever touch exploration is
     * toggled mid-session (listener registered in init).
     */
    fun refreshTalkBackGrid() {
        if (isInEditMode) return
        val want = touchExplorationEnabled() &&
            ::parent.isInitialized && parent.currentBackend == SOLO
        if (want && talkBackGrid == null) {
            val helper = SoloTalkBackGrid(this)
            talkBackGrid = helper
            ViewCompat.setAccessibilityDelegate(this, helper)
        } else if (!want && talkBackGrid != null) {
            ViewCompat.setAccessibilityDelegate(this, null)
            talkBackGrid = null
        }
        talkBackGrid?.markDirty()
        if (::parent.isInitialized) {
            parent.onTalkBackGridActiveChanged(talkBackGrid != null)
        }
    }

    /** GamePlay calls this after the engine processes any input. */
    fun onBoardMayHaveChanged() {
        talkBackGrid?.markDirty()
        refreshPatternClues()
    }

    /** The grid's current Solo model, or null when the grid is inactive. */
    internal fun currentTalkBackModel(): SoloGridModel? = talkBackGrid?.currentModel()

    /** True while the Solo TalkBack grid is installed on this view. */
    internal fun isTalkBackGridActive(): Boolean = talkBackGrid != null

    /**
     * A TalkBack user activated a grid cell: select it through the
     * engine's own tap path (a LEFT_BUTTON at the cell's centre, in
     * the same coordinates real touches use), then let GamePlay
     * update its selection mirror and announce. The pre-tap model
     * rides along — after the tap the capture is dirty, and the
     * mirror logic needs the state the tap acted on.
     */
    internal fun onTalkBackCellActivated(virtualId: Int) {
        val helper = talkBackGrid ?: return
        val model = helper.currentModel() ?: return
        if (virtualId !in 0 until model.cr * model.cr) return
        val row = virtualId / model.cr
        val col = virtualId % model.cr
        parent.sendKey(
            PointF(model.colCenters[col], model.rowCenters[row]),
            LEFT_BUTTON,
        )
        parent.talkBackCellSelected(virtualId, model)
    }

    /** One fresh capture of the current board, modelled for Solo; null when unavailable. */
    internal fun captureSoloGridModel(): SoloGridModel? {
        if (isInEditMode || !::parent.isInitialized) return null
        val cap = parent.captureBoardTexts() ?: return null
        val shape = cap.soloShape ?: return null
        return SoloGridModel.fromCapture(
            cap.texts, shape.cr, cap.boardExtent, cap.originX, cap.originY,
            shape.blockCols, shape.blockRows,
            lines = cap.lines,
        )
    }

    /**
     * A game-coordinate rect in view coordinates, following the
     * same transform onDraw applies to the board bitmap (the zoom
     * matrix baked into the bitmap, then the overdraw offset).
     */
    internal fun gameRectToViewRect(gameRect: RectF): Rect {
        val pts = floatArrayOf(
            gameRect.left, gameRect.top, gameRect.right, gameRect.bottom,
        )
        zoomMatrix.mapPoints(pts)
        val out = Rect(
            (pts[0] - overdrawX).roundToInt(), (pts[1] - overdrawY).roundToInt(),
            (pts[2] - overdrawX).roundToInt(), (pts[3] - overdrawY).roundToInt(),
        )
        return out
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val helper = talkBackGrid
        if (helper != null && helper.dispatchHoverEvent(event)) return true
        return super.onHoverEvent(event)
    }

    /** The board's spoken label for its TalkBack stop. */
    private var boardLabel: String? = null

    /**
     * Names the board's TalkBack stop: the puzzle, plus a signpost to
     * Describe board. Without a label TalkBack announces the board as
     * "untitled"/"unlabeled" — Fernando heard that on every puzzle.
     */
    fun updateBoardLabel(whichBackend: BackendName?) {
        boardLabel = whichBackend?.let {
            "${it.displayName}, board. Describe board is in the Game menu."
        }
        contentDescription = boardLabel
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Belt and braces: on Fernando's device the board still
        // announced as unlabeled with only the contentDescription set,
        // so force the label into the node info TalkBack reads.
        boardLabel?.let { info.contentDescription = it }
    }

    private fun revertDragInProgress(here: PointF?) {
        if (touchState == TouchState.DRAGGING) {
            val offScreen = PointF(-1f, -1f)
            // The clamp is a no-op except for an UNKNOWN-target
            // Pan hold-paint stroke (modebar2), whose revert must
            // also release on the painted line, not the raw
            // rectangle.
            val dragTo = clampPanPaintGamePoint(
                when (dragMode) {
                    DragMode.REVERT_OFF_SCREEN -> offScreen
                    DragMode.REVERT_TO_START -> viewToGame(touchStart ?: offScreen)
                    else -> viewToGame(here ?: touchStart ?: offScreen)
                }
            )
            parent.sendKey(dragTo, button + DRAG)
            parent.sendKey(dragTo, button + RELEASE)
        }
    }

    override fun scrollBy(x: Int, y: Int) {
        scrollBy(x.toFloat(), y.toFloat())
    }

    private fun scrollBy(distanceX: Float, distanceY: Float) {
        zoomInProgressMatrix.postTranslate(-distanceX, -distanceY)
        zoomMatrixUpdated(true)
        postInvalidateOnAnimation()
    }

    fun ensureCursorVisible(cursorLocation: RectF?) {
        val topLeft = viewToGame(PointF(0f, 0f))
        val bottomRight = viewToGame(PointF(w.toFloat(), h.toFloat()))
        if (cursorLocation == null) {
            postInvalidateOnAnimation()
            return
        }
        val cursorWithMargin = RectF(
            cursorLocation.left - cursorLocation.width() / 4,
            cursorLocation.top - cursorLocation.height() / 4,
            cursorLocation.right + cursorLocation.width() / 4,
            cursorLocation.bottom + cursorLocation.height() / 4
        )
        var dx = 0f
        var dy = 0f
        if (cursorWithMargin.left < topLeft.x) {
            dx = topLeft.x - cursorWithMargin.left
        }
        if (cursorWithMargin.top < topLeft.y) {
            dy = topLeft.y - cursorWithMargin.top
        }
        if (cursorWithMargin.right > bottomRight.x) {
            dx = bottomRight.x - cursorWithMargin.right
        }
        if (cursorWithMargin.bottom > bottomRight.y) {
            dy = bottomRight.y - cursorWithMargin.bottom
        }
        if (dx != 0f || dy != 0f) {
            Log.d(TAG, "dx $dx dy $dy")
            val scale = getXScale(zoomMatrix)
            zoomInProgressMatrix.postTranslate(dx * scale, dy * scale)
            redrawForInitOrZoomChange()
        }
        postInvalidateOnAnimation()
    }

    private fun zoomMatrixUpdated(userAction: Boolean) {
        // Constrain scrolling to game bounds
        invertZoomMatrix() // needed for viewToGame
        val topLeft = viewToGame(PointF(0f, 0f))
        val bottomRight = viewToGame(PointF(w.toFloat(), h.toFloat()))
        if (topLeft.x < 0) {
            zoomInProgressMatrix.preTranslate(topLeft.x * density, 0f)
            if (userAction) hitEdge(3, -topLeft.x / wDip, 1 - lastTouch.y / h)
        } else if (exceedsTouchSlop(topLeft.x)) {
            edges[3].onRelease()
        }
        if (bottomRight.x > wDip) {
            zoomInProgressMatrix.preTranslate((bottomRight.x - wDip) * density, 0f)
            if (userAction) hitEdge(1, (bottomRight.x - wDip) / wDip, lastTouch.y / h)
        } else if (exceedsTouchSlop(wDip - bottomRight.x)) {
            edges[1].onRelease()
        }
        if (topLeft.y < 0) {
            zoomInProgressMatrix.preTranslate(0f, topLeft.y * density)
            if (userAction) hitEdge(0, -topLeft.y / hDip, lastTouch.x / w)
        } else if (exceedsTouchSlop(topLeft.y)) {
            edges[0].onRelease()
        }
        if (bottomRight.y > hDip) {
            zoomInProgressMatrix.preTranslate(0f, (bottomRight.y - hDip) * density)
            if (userAction) hitEdge(2, (bottomRight.y - hDip) / hDip, 1 - lastTouch.x / w)
        } else if (exceedsTouchSlop(hDip - bottomRight.y)) {
            edges[2].onRelease()
        }
        canvas.setMatrix(zoomMatrix)
        invertZoomMatrix() // now with our changes
    }

    private fun hitEdge(edge: Int, delta: Float, displacement: Float) {
        if (!mScroller.isFinished) {
            edges[edge].onAbsorb(mScroller.currVelocity.roundToInt())
            mScroller.abortAnimation()
        } else {
            val deltaDistance = (delta * 1.5f).coerceAtMost(1f)
            edges[edge].onPull(deltaDistance, displacement)
        }
    }

    private fun exceedsTouchSlop(dist: Float): Boolean {
        return dist.toDouble().pow(2.0) > maxDistSq
    }

    private fun movedPastTouchSlop(x: Float, y: Float): Boolean {
        touchStart?.let {
            return (abs(x - it.x).toDouble().pow(2.0)
                    + abs(y - it.y).toDouble().pow(2.0)
                    > maxDistSq)
        }
        return false
    }

    private val isScaleInProgress: Boolean
        get() = scaleDetector?.isInProgress ?: false

    private fun enablePinchZoom() {
        scaleDetector = ScaleGestureDetector(context, object : SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var factor = detector.scaleFactor
                val scale = getXScale(zoomMatrix) * getXScale(zoomInProgressMatrix)
                val nextScale = scale * factor
                val wasZoomedOut = scale == density
                if (nextScale < density + 0.01f) {
                    if (!wasZoomedOut) {
                        resetZoomMatrix()
                        redrawForInitOrZoomChange()
                    }
                } else {
                    if (nextScale > MAX_ZOOM) {
                        factor = MAX_ZOOM / scale
                    }
                    zoomInProgressMatrix.postScale(
                        factor, factor,
                        overdrawX + detector.focusX,
                        overdrawY + detector.focusY
                    )
                }
                zoomMatrixUpdated(true)
                postInvalidateOnAnimation()
                return true
            }
        }).also {
            it.isQuickScaleEnabled = false
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                it.isStylusScaleEnabled = false
            }
        }
    }

    fun resetZoomForClear() {
        resetZoomMatrix()
        canvas.setMatrix(zoomMatrix)
        invertZoomMatrix()
    }

    private fun resetZoomMatrix() {
        zoomMatrix.reset()
        zoomMatrix.postTranslate(overdrawX.toFloat(), overdrawY.toFloat())
        zoomMatrix.postScale(
            density, density,
            overdrawX.toFloat(), overdrawY.toFloat()
        )
        zoomInProgressMatrix.reset()
    }

    private fun invertZoomMatrix() {
        val copy = Matrix(zoomMatrix)
        copy.postConcat(zoomInProgressMatrix)
        copy.postTranslate(-overdrawX.toFloat(), -overdrawY.toFloat())
        if (!copy.invert(inverseZoomMatrix)) {
            error("zoom not invertible")
        }
    }

    private fun redrawForInitOrZoomChange() {
        zoomMatrixUpdated(false) // constrains zoomInProgressMatrix
        zoomMatrix.postConcat(zoomInProgressMatrix)
        zoomInProgressMatrix.reset()
        canvas.setMatrix(zoomMatrix)
        invertZoomMatrix()
        if (!isInEditMode) {
            clear()
            parent.gameViewResized() // not just forceRedraw() - need to reallocate blitters
            refreshPatternClues()
        }
        postInvalidateOnAnimation()
    }

    private fun getXScale(m: Matrix): Float {
        val values = FloatArray(9)
        m.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private val MotionEvent.point: PointF?
        get() = if (x.isNaN() or y.isNaN()) null else PointF(x, y)

    internal fun viewToGame(point: PointF): PointF {
        val f = floatArrayOf(point.x, point.y)
        inverseZoomMatrix.mapPoints(f)
        return PointF(f[0], f[1])
    }

    private fun checkPinchZoom(event: MotionEvent): Boolean {
        return scaleDetector?.onTouchEvent(event) ?: false
    }

    private val sendLongPress: Runnable = object : Runnable {
        override fun run() {
            if (isScaleInProgress) return
            // Pattern's mode bar supersedes the long-press
            // reverse-cycle gesture: a held finger is just a slow
            // tap or drag there, and its press is planned from
            // the mode when the gesture resolves (see
            // beginPatternStroke / patternTapHandled). Switching
            // to RIGHT_BUTTON here would corrupt that plan.
            if (::parent.isInitialized && parent.currentBackend == PATTERN) return
            button = RIGHT_BUTTON
            touchState = TouchState.DRAGGING
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            touchStart?.let { parent.sendKey(viewToGame(it), button) }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (MotionEventCompat.isFromSource(event, SOURCE_MOUSE)
                && event.actionMasked == ACTION_HOVER_MOVE
        ) {
            mousePos = event.point
            if (rightMouseHeld && touchState == TouchState.DRAGGING) {
                event.action = MotionEvent.ACTION_MOVE
                return handleTouchEvent(event, false)
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /** True while a screen reader's touch exploration is driving touches. */
    private fun touchExplorationEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
        return am?.isTouchExplorationEnabled == true
    }

    @SuppressLint("ClickableViewAccessibility") // Not a simple enough view to just "click the view"
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (parent.currentBackend == null) return false
        val evAction = event.action
        if (MotionEventCompat.isFromSource(event, SOURCE_STYLUS)
                && evAction >= 211 && evAction <= 213) {
            event.action = evAction - 211
        }
        if (event.pointerCount > 1 && touchExplorationEnabled()) {
            // TalkBack passes its two-finger gestures (scroll and
            // friends) through to the app, and the board would read
            // them as pan/zoom — hijacking a gesture that belongs to
            // the screen reader. While touch exploration is on,
            // swallow multi-touch wholesale and cancel any pending
            // long-press so it cannot fire mid-gesture. Sighted play
            // (exploration off) never takes this path.
            parent.handler.removeCallbacks(sendLongPress)
            parent.handler.removeCallbacks(armPanPaint)
            when (touchState) {
                TouchState.WAITING_LONG_PRESS -> touchState = TouchState.IDLE
                TouchState.DRAGGING -> {
                    // A drag was already running (e.g. a TalkBack
                    // double-tap-hold drag) when the second finger
                    // landed: cancel it the way the view's own cancel
                    // paths do — revertDragInProgress first drags to
                    // the mode's revert point, then releases. A bare
                    // release at the last position would commit the
                    // drop there in revert-style games (Claude review
                    // round 2).
                    revertDragInProgress(lastTouch)
                    touchState = TouchState.IDLE
                }
                else -> {}
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            // A second finger means a pinch is starting: cancel
            // Pan mode's hold timer (modebar2) so it cannot arm
            // paint mid-pinch. The pinch itself follows the usual
            // path below. (A strip-claimed gesture never posted
            // the timer, so this is a no-op there.)
            parent.handler.removeCallbacks(armPanPaint)
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // The gesture is being taken away (window interrupted):
            // the hold timer must not fire afterwards and arm
            // paint with no finger down.
            parent.handler.removeCallbacks(armPanPaint)
        }
        // Pinned Pattern clue bands are touch zones of their own
        // (resize / strip-pan — see claimStripGesture). A gesture
        // claimed by a band never reaches the engine or the
        // detectors below. The TalkBack multi-touch swallow above
        // keeps priority over this.
        if (stripGesture != StripGesture.NONE) {
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                // A second finger ends the strip gesture (committed
                // as if lifted) and this event falls through to the
                // normal pinch handling below.
                endStripGesture()
            } else {
                return handleStripGestureEvent(event)
            }
        } else if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            event.pointerCount == 1 && claimStripGesture(event)
        ) {
            return true
        }
        val sdRet = checkPinchZoom(event)
        val gdRet = gestureDetector.onTouchEvent(event)
        return handleTouchEvent(event, sdRet || gdRet)
    }

    private fun handleTouchEvent(event: MotionEvent, consumedAsScrollOrGesture: Boolean): Boolean {
        event.point?.let { lastTouch = it }
        return when (event.action) {
            MotionEvent.ACTION_UP -> {
                parent.handler.removeCallbacks(sendLongPress)
                parent.handler.removeCallbacks(armPanPaint)
                if (touchState == TouchState.PANNING) {
                    // A Pan-mode pan settles the way a pinch does:
                    // fold the in-progress transform in, re-render
                    // (which also refreshes the clue capture).
                    redrawForInitOrZoomChange()
                    for (edge in edges) edge.onRelease()
                    patternPanLast = null
                } else if (touchState == TouchState.PINCH && mScroller.isFinished) {
                    redrawForInitOrZoomChange()
                    for (edge in edges) edge.onRelease()
                } else if (touchState == TouchState.WAITING_LONG_PRESS) {
                    if (!patternTapHandled()) {
                        touchStart?.let { parent.sendKey(viewToGame(it), button) }
                        touchState = TouchState.DRAGGING
                    }
                }
                if (touchState == TouchState.DRAGGING) {
                    // The clamp is a no-op except for an
                    // UNKNOWN-target Pan hold-paint stroke, whose
                    // release must land on the painted line.
                    event.point?.let {
                        parent.sendKey(clampPanPaintGamePoint(viewToGame(it)), button + RELEASE)
                    }
                }
                touchState = TouchState.IDLE
                panPaintStartCell = null
                panPaintTarget = null
                true
            }
            MotionEvent.ACTION_MOVE -> {
                // 2nd clause is 2 fingers a constant distance apart
                if (isScaleInProgress || event.pointerCount > 1) {
                    return consumedAsScrollOrGesture
                }
                val x = event.x
                val y = event.y
                if (touchState == TouchState.WAITING_LONG_PRESS && movedPastTouchSlop(x, y)) {
                    parent.handler.removeCallbacks(sendLongPress)
                    // Movement past slop before the hold threshold
                    // disarms Pan hold-paint (modebar2): the
                    // gesture becomes a pan (or a paint-mode
                    // stroke) below, and no cycle press is sent.
                    parent.handler.removeCallbacks(armPanPaint)
                    touchState = if (dragMode == DragMode.PREVENT) {
                        TouchState.IDLE
                    } else {
                        beginPatternStroke() ?: run {
                            touchStart?.let { parent.sendKey(viewToGame(it), button) }
                            TouchState.DRAGGING
                        }
                    }
                }
                if (touchState == TouchState.PANNING) {
                    // Pan mode: the board follows the finger, via
                    // the same scrollBy the pinch and strip pans
                    // use (distances in GestureDetector convention:
                    // previous minus current position).
                    patternPanLast?.let { last ->
                        scrollBy(last.x - x, last.y - y)
                    }
                    patternPanLast = PointF(x, y)
                    return true
                }
                if (touchState == TouchState.DRAGGING) {
                    lastDrag = event.point
                    lastDrag?.let {
                        parent.sendKey(clampPanPaintGamePoint(viewToGame(it)), button + DRAG)
                    }
                    return true
                }
                false
            }
            else -> {
                consumedAsScrollOrGesture
            }
        }
    }

    private val sendSpace = Runnable {
        waitingSpace = false
        parent.sendKey(0, 0, ' '.code)
    }

    init {
        if (!isInEditMode) {
            parent = context as GamePlay
        }
        canvasRestoreJustAfterCreation = canvas.save()
        if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        // The TalkBack grid (Phase 2) follows touch exploration
        // being toggled mid-session, not just at game start.
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.addTouchExplorationStateChangeListener {
                post { refreshTalkBackGrid() }
            }
        gestureDetector =
            GestureDetector(getContext(), object : GestureDetector.OnGestureListener {
                override fun onDown(event: MotionEvent): Boolean {
                    val meta = event.metaState
                    val buttonState = event.buttonState
                    if (meta and META_ALT_ON > 0 ||
                        buttonState == BUTTON_TERTIARY
                    ) {
                        button = MIDDLE_BUTTON
                    } else if (meta and META_SHIFT_ON > 0
                            || buttonState == BUTTON_SECONDARY
                            || buttonState == BUTTON_STYLUS_PRIMARY) {
                        button = RIGHT_BUTTON
                        hasRightMouse = true
                    } else {
                        button = LEFT_BUTTON
                    }
                    touchStart = event.point
                    if (touchStart == null) return false
                    parent.handler.removeCallbacks(sendLongPress)
                    parent.handler.removeCallbacks(armPanPaint)
                    if ((MotionEventCompat.isFromSource(event, SOURCE_MOUSE)
                            || MotionEventCompat.isFromSource(event, SOURCE_STYLUS)
                            || event.getToolType(event.actionIndex) == TOOL_TYPE_STYLUS) &&
                        (hasRightMouse && !alwaysLongPress || button != LEFT_BUTTON)
                    ) {
                        touchStart?.let { parent.sendKey(viewToGame(it), button) }
                        touchState = if (dragMode == DragMode.PREVENT) {
                            TouchState.IDLE
                        } else {
                            TouchState.DRAGGING
                        }
                    } else {
                        touchState = TouchState.WAITING_LONG_PRESS
                        parent.handler.postDelayed(sendLongPress, longPressTimeout.toLong())
                        if (parent.currentBackend == PATTERN &&
                            patternMode == PatternMode.PAN
                        ) {
                            // Pan mode's hold-then-drag paint
                            // timer (modebar2); see armPanPaint.
                            parent.handler.postDelayed(
                                armPanPaint, longPressTimeout.toLong()
                            )
                        }
                    }
                    return true
                }

                override fun onScroll(
                    downEvent: MotionEvent?,
                    event: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    // 2nd clause is 2 fingers a constant distance apart
                    if (isScaleInProgress || event.pointerCount > 1) {
                        revertDragInProgress(event.point)
                        if (touchState == TouchState.WAITING_LONG_PRESS) {
                            parent.handler.removeCallbacks(sendLongPress)
                            parent.handler.removeCallbacks(armPanPaint)
                        }
                        touchState = TouchState.PINCH
                        scrollBy(distanceX, distanceY)
                        return true
                    }
                    return false
                }

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    return true
                }

                override fun onShowPress(e: MotionEvent) {}
                override fun onLongPress(e: MotionEvent) {}
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (touchState != TouchState.PINCH) {  // require 2 fingers
                        return false
                    }
                    val scale = getXScale(zoomMatrix) * getXScale(zoomInProgressMatrix)
                    val currentScroll: PointF = currentScroll
                    mScroller.fling(
                        currentScroll.x.roundToInt(),
                        currentScroll.y.roundToInt(),
                        -(velocityX / scale).roundToInt(),
                        -(velocityY / scale).roundToInt(),
                        0,
                        wDip,
                        0,
                        hDip
                    )
                    animateScroll()
                    return true
                }
            })
        // We do our own long-press detection to capture movement afterwards
        @Suppress("UsePropertyAccessSyntax")
        gestureDetector.setIsLongpressEnabled(false)
        enablePinchZoom()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var key = 0
        val repeat = event.repeatCount
        when (keyCode) {
            KEYCODE_DPAD_UP -> key = CURSOR_UP
            KEYCODE_DPAD_DOWN -> key = CURSOR_DOWN
            KEYCODE_DPAD_LEFT -> key = CURSOR_LEFT
            KEYCODE_DPAD_RIGHT -> key = CURSOR_RIGHT
            KEYCODE_DPAD_CENTER -> {
                if (repeat > 0) return false
                if (event.isShiftPressed) {
                    key = ' '.code
                } else {
                    touchStart = PointF(0f, 0f)
                    waitingSpace = true
                    parent.handler.removeCallbacks(sendSpace)
                    parent.handler.postDelayed(sendSpace, longPressTimeout.toLong())
                    keysHandled++
                    return true
                }
            }

            KEYCODE_ENTER -> key = '\n'.code
            KEYCODE_FOCUS, KEYCODE_SPACE, KEYCODE_BUTTON_X -> key = ' '.code

            KEYCODE_BUTTON_L1 -> key = UI_UNDO
            KEYCODE_BUTTON_R1 -> key = UI_REDO
            KEYCODE_DEL -> key = '\b'.code
        }
        if (preAndroid11MouseBackDown(keyCode, event)) return true
        if (key == CURSOR_UP || key == CURSOR_DOWN || key == CURSOR_LEFT || key == CURSOR_RIGHT) {
            // "only apply to cursor keys"
            // http://www.chiark.greenend.org.uk/~sgtatham/puzzles/devel/backend.html#backend-interpret-move
            if (event.isShiftPressed) key = key or MOD_SHIFT
            if (event.isAltPressed) key = key or MOD_CTRL
        }
        // we probably don't want MOD_NUM_KEYPAD here (numbers are in a line on G1 at least)
        if (key == 0) {
            val exactKey = event.unicodeChar
            if (exactKey >= 'A'.code && exactKey <= 'Z'.code || hardwareKeys.indexOf(exactKey.toChar()) >= 0) {
                key = exactKey
            } else {
                key = event.getMatch(hardwareKeys.toCharArray()).code
                if (key == 0 && (exactKey == 'u'.code || exactKey == 'r'.code)) key = exactKey
            }
        }
        if (key == 0) return super.onKeyDown(keyCode, event) // handles Back etc.
        parent.sendKey(0, 0, key, repeat > 0)
        keysHandled++
        return true
    }

    private fun preAndroid11MouseBackDown(keyCode: Int, event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < VERSION_CODES.R
                && keyCode == KEYCODE_BACK
                && mouseBackSupport
                && event.source == SOURCE_MOUSE) {
            if (rightMouseHeld) {
                return true
            }
            rightMouseHeld = true
            hasRightMouse = true
            touchStart = mousePos
            button = RIGHT_BUTTON
            touchStart?.let { parent.sendKey(viewToGame(it), button) }
            touchState = if (dragMode == DragMode.PREVENT) {
                TouchState.IDLE
            } else {
                TouchState.DRAGGING
            }
            return true
        }
        return false
    }

    fun setHardwareKeys(hardwareKeys: String) {
        this.hardwareKeys = hardwareKeys
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (preAndroid11MouseBackUp(event, keyCode)) return true
        if (keyCode == KEYCODE_DPAD_CENTER && waitingSpace) {
            parent.handler.removeCallbacks(sendSpace)
            parent.sendKey(0, 0, '\n'.code)
            return true
        }
        return super.onKeyUp(
            keyCode,
            event
        )
    }

    @SuppressLint("GestureBackNavigation")
    private fun preAndroid11MouseBackUp(event: KeyEvent, keyCode: Int): Boolean {
        if (Build.VERSION.SDK_INT < VERSION_CODES.R
                && mouseBackSupport
                && event.source == SOURCE_MOUSE) {
            if (keyCode == KEYCODE_BACK && rightMouseHeld) {
                rightMouseHeld = false
                if (touchState == TouchState.DRAGGING) {
                    mousePos?.let { parent.sendKey(viewToGame(it), button + RELEASE) }
                }
                touchState = TouchState.IDLE
            }
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        // Mouse right-click-and-hold sends MENU as a "keyboard" on at least Galaxy S7, ignore
        if (event.keyCode == KEYCODE_MENU && rightMouseHeld)
            true
        else
            super.dispatchKeyEvent(event)

    override fun onDraw(c: Canvas) {
        tempDrawMatrix.reset()
        tempDrawMatrix.preTranslate(-overdrawX.toFloat(), -overdrawY.toFloat())
        tempDrawMatrix.preConcat(zoomInProgressMatrix)
        c.withMatrix(tempDrawMatrix) {
            val f = floatArrayOf(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            tempDrawMatrix.mapPoints(f)
            if (f[0] > 0 || f[1] < w || f[2] < 0 || f[3] > h) {
                c.drawPaint(checkerboardPaint)
            }
            c.drawBitmap(bitmap, 0f, 0f, null)
        }
        drawPinnedPatternClues(c)
        var keepAnimating = false
        for (i in 0..3) {
            if (!edges[i].isFinished) {
                keepAnimating = true
                c.withRotation((i * 90).toFloat()) {
                    when (i) {
                        1 -> c.translate(0f, -w.toFloat())
                        2 -> c.translate(-w.toFloat(), -h.toFloat())
                        3 -> c.translate(-h.toFloat(), 0f)
                    }
                    val flip = i % 2 > 0
                    edges[i].setSize(if (flip) h else w, if (flip) w else h)
                    edges[i].draw(c)
                }
            }
        }
        if (keepAnimating) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Maps a point in the draw callbacks' space (game coordinates
     * plus the frontend's centring origin — "dip space") to screen
     * pixels, through exactly the transform [onDraw] applies to the
     * board bitmap: zoomMatrix into the bitmap, then the in-progress
     * zoom and the overdraw offset onto the screen.
     */
    private fun dipToView(q: PointF): PointF {
        val f = floatArrayOf(q.x, q.y)
        zoomMatrix.mapPoints(f)
        tempDrawMatrix.reset()
        tempDrawMatrix.preTranslate(-overdrawX.toFloat(), -overdrawY.toFloat())
        tempDrawMatrix.preConcat(zoomInProgressMatrix)
        tempDrawMatrix.mapPoints(f)
        return PointF(f[0], f[1])
    }

    /**
     * Pinned clues for Pattern: when the board is panned/zoomed so a
     * clue strip's natural position has scrolled off the top or left
     * of this view, redraw that strip as a band pinned flush to the
     * view's edge — background-filled, showing the visible
     * rows'/columns' own clue texts.
     *
     * The text band covers only the clue content actually
     * present — the captured texts' used extent — never the
     * natural strip's BORDER dead band and never the TLBORDER
     * maximum the strip reserves, so the numbers sit against the
     * screen edge (Katana-style) with no dead space for boards
     * whose stacks run short. The used extent is fitted to the
     * visible window (round 5): only the rows and columns in view
     * count toward it, so panning to a stretch of short stacks
     * shrinks the band and panning back grows it — the occlusion
     * fill below is the one thing that does NOT follow the
     * window, since the natural strip it hides is window-blind.
     * Each strip has a persistent user scale in
     * [PatternClueGeometry.MIN_CLUE_SCALE]..1.0 that compresses the
     * band toward the edge: row clues stay right-aligned a hair
     * inside the band's inner edge with their row alignment
     * untouched; column clues keep their anchor x and compress
     * toward the edge from the strip's bottom, glyphs scaled by the
     * same factor. A subtle grip bar on the band's inner edge marks
     * it as draggable — dragging the grip resizes the strip,
     * dragging elsewhere on the band pans the board along the
     * strip's axis (see the touch section above).
     *
     * Occlusion: the text band is NOT the fill's edge. The board
     * bitmap underneath still carries the engine's natural clue
     * strips, and any of them left uncovered past the band shows
     * through beside the pinned copy as duplicate clues and
     * sliced glyphs. The fill and the clip are therefore anchored
     * to the captured texts' own extents — the positions that
     * demonstrably map correctly through dipToView, where the
     * earlier origin-derived grid edge did not reliably cover
     * the strip (the natural strip's last digit and bottom glyph
     * row leaked past it at shallow pans). Each fill reaches the
     * natural strip's rightmost/bottommost glyph extent plus the
     * gutter, stopping a hair short of the grid border
     * ([PatternClues.naturalLeftFillRight] /
     * [PatternClues.naturalTopFillBottom]) — and never less than
     * the text band itself, whose own glyphs the clip must not
     * cut on deep pans where the natural extent has scrolled
     * inside the band. The region the fill covers past the band
     * holds only the natural strip and the gutter — no cells —
     * so it hides no board content. The grip and the touch claim
     * stay on the text band: they mark the adjustable clue line,
     * not the occlusion edge.
     *
     * When nothing has scrolled off (including every unzoomed
     * board) this draws nothing, so the rendering is exactly the
     * engine's own. The
     * bands live inside this view; the control bar below is a
     * separate view and stays visible and uncovered.
     *
     * When both strips are pinned at once, the corner square
     * where they overlap — as wide as the left band, as tall as
     * the top band — carries a minimap of the whole grid with a
     * viewport indicator (see [drawCornerMinimap]), drawn last.
     */
    private fun drawPinnedPatternClues(c: Canvas) {
        val clues = patternClues ?: return
        if (colours.isEmpty()) return
        val bands = pinnedBandRects()
        if (bands.left == null && bands.top == null) return
        // The same visible-window contents the bands were sized
        // to: the anchors must agree with the band edges.
        val visible = visibleClueContents()
        val g = clues.geometry
        val ts = g.tileSize.toFloat()
        val topLeft = viewToGame(PointF(0f, 0f))
        val bottomRight = viewToGame(PointF(w.toFloat(), h.toFloat()))
        val scale = getXScale(zoomMatrix) * getXScale(zoomInProgressMatrix)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        bands.left?.let { band ->
            // Fill and clip reach the natural strip's own extent,
            // not just the text band (see the occlusion note
            // above): the bitmap underneath still carries the
            // natural row strip, and whatever of it pokes past
            // the band would show through as duplicate clues.
            val fillRight = clues.naturalLeftFillRight?.let {
                maxOf(band.right, dipToView(PointF(it, 0f)).x)
            } ?: band.right
            val fill = RectF(band.left, band.top, fillRight, band.bottom)
            paint.color = colours[0]
            c.drawRect(fill, paint)
            val save = c.save()
            c.clipRect(fill)
            // Right-aligned anchors on a line a hair inside the
            // band's inner edge; anchor y is the clue's own, so row
            // alignment with the board is unchanged.
            val anchorX = topLeft.x + PatternClueGeometry.bandThickness(
                visible.left, leftClueScale
            ) - PatternClueGeometry.rowAnchorPad(g.tileSize)
            for (row in 0 until g.h) {
                val clue = clues.rowClues[row] ?: continue
                val rowTopDip = clues.originY + g.gridOriginY + row * ts
                if (rowTopDip + ts < topLeft.y || rowTopDip > bottomRight.y) continue
                val anchor = dipToView(PointF(anchorX, clue.y.toFloat()))
                drawPinnedText(c, clue, anchor, scale * leftClueScale)
            }
            c.restoreToCount(save)
        }
        bands.top?.let { band ->
            // Same occlusion rule as the left band, downward: the
            // natural column strip in the bitmap runs past the
            // text band, out to just short of the grid's top edge.
            val fillBottom = clues.naturalTopFillBottom?.let {
                maxOf(band.bottom, dipToView(PointF(0f, it)).y)
            } ?: band.bottom
            val fill = RectF(band.left, band.top, band.right, fillBottom)
            paint.color = colours[0]
            c.drawRect(fill, paint)
            val save = c.save()
            c.clipRect(fill)
            for (col in 0 until g.w) {
                val colLeftDip = clues.originX + g.gridOriginX + col * ts
                if (colLeftDip + ts < topLeft.x || colLeftDip > bottomRight.x) continue
                for (clue in clues.colClues[col]) {
                    // Anchor x is the clue's own; anchor y compresses
                    // the clue's offset from the strip's bottom edge
                    // by the strip's scale, inside the flush band.
                    val anchor = dipToView(
                        PointF(
                            clue.x.toFloat(),
                            PatternClueGeometry.topClueAnchorY(
                                clue.y, clues.originY, topLeft.y, g,
                                visible.top, topClueScale
                            ),
                        )
                    )
                    drawPinnedText(c, clue, anchor, scale * topClueScale)
                }
            }
            c.restoreToCount(save)
        }
        bands.left?.let { drawBandGrip(c, it, vertical = true) }
        bands.top?.let { drawBandGrip(c, it, vertical = false) }
        if (bands.left != null && bands.top != null) {
            drawCornerMinimap(c, bands)
        }
    }

    /**
     * The corner minimap (round 6; source fixed in round 8):
     * when both strips are pinned, their overlap square shows
     * the whole grid, letterboxed to the grid's aspect ratio,
     * with a viewport indicator over the part currently on
     * screen.
     *
     * The image is the grid region of the minimap's own
     * full-board render ([patternMinimapBitmap], kept current
     * by [renderPatternMinimap]) — NOT the view bitmap: that
     * one is view-sized and rendered through the settled zoom,
     * so past fit zoom it physically holds only the visible
     * window, and the round-6 thumbnail stretched that window
     * across the whole map while the indicator marked the true
     * window fraction. The offscreen render's space is game
     * coordinates at [patternMinimapScale], so the source rect
     * is [PatternClueGeometry.gridRectInGameCoords] at that
     * scale — the same grid rect the indicator below maps
     * against, offset by the capture origin into dip space.
     * The shared derivation is the pairing: thumbnail and
     * indicator cannot drift apart.
     *
     * Draw-only: no touch handling changes — the corner stays
     * inside both bands' claim area, where the top band's strip
     * gestures keep working exactly as before.
     */
    private fun drawCornerMinimap(c: Canvas, bands: PinnedBands) {
        val clues = patternClues ?: return
        val left = bands.left ?: return
        val top = bands.top ?: return
        val fullBoard = patternMinimapBitmap ?: return
        val g = clues.geometry
        // THE grid rect, in game coordinates; both the source
        // and the indicator below derive from it (see above).
        val gridGame = PatternClueGeometry.gridRectInGameCoords(g)
        // The grid's rect in dip space: the game-coords rect
        // offset by the capture's centring origin.
        val gridDip = PatternClueGeometry.FloatRect(
            clues.originX + gridGame.left,
            clues.originY + gridGame.top,
            clues.originX + gridGame.right,
            clues.originY + gridGame.bottom,
        )
        val corner = PatternClueGeometry.FloatRect(0f, 0f, left.right, top.bottom)
        val fit = PatternClueGeometry.minimapFitRect(
            gridDip.width, gridDip.height, corner
        ) ?: return
        // The grid's pixels in the offscreen render: inside the
        // bitmap by construction (the render's fit transform
        // puts the game extent exactly in frame); the intersect
        // is only rounding safety at the edges.
        val srcF = PatternClueGeometry.minimapSrcRect(g, patternMinimapScale)
        val src = Rect(
            srcF.left.roundToInt(), srcF.top.roundToInt(),
            srcF.right.roundToInt(), srcF.bottom.roundToInt(),
        )
        if (!src.intersect(0, 0, fullBoard.width, fullBoard.height) || src.isEmpty) return
        val dst = RectF(fit.left, fit.top, fit.right, fit.bottom)
        c.drawBitmap(fullBoard, src, dst, minimapBitmapPaint)
        // A thin border in the ink colour, so the map reads as
        // its own element against the strips behind it.
        val d = resources.displayMetrics.density
        minimapBorderPaint.color = if (colours.size > 1) colours[1] else colours[0]
        minimapBorderPaint.alpha = 160
        minimapBorderPaint.strokeWidth = 1f * d
        c.drawRect(dst, minimapBorderPaint)
        // The viewport indicator: the visible window — the view
        // rect inset by the bands, because the board beneath a
        // band is covered, not visible (round 7; the round-6
        // indicator used the full view rect and overstated the
        // visible region by the bands' thickness) — mapped
        // through viewToGame and clamped to the grid by the pure
        // mapping. Its colour is the game's own
        // cursor colour: for Pattern (the only game this path
        // runs for, so its palette is the one in [colours]) that
        // is index 6, a bright red (1, .25, .25) in pattern.c,
        // which reads against every shade the map contains —
        // white cells, black fills, grey unknowns.
        val viewRect = PatternClueGeometry.bandInsetViewRect(
            w.toFloat(), h.toFloat(), left.right, top.bottom
        )
        val visibleTopLeft = viewToGame(PointF(viewRect.left, viewRect.top))
        val visibleBottomRight = viewToGame(PointF(viewRect.right, viewRect.bottom))
        val indicator = PatternClueGeometry.minimapIndicatorRect(
            gridDip,
            PatternClueGeometry.FloatRect(
                visibleTopLeft.x, visibleTopLeft.y,
                visibleBottomRight.x, visibleBottomRight.y,
            ),
            fit,
        ) ?: return
        minimapViewportPaint.color = when {
            colours.size > 6 -> colours[6]
            colours.size > 1 -> colours[1]
            else -> colours[0]
        }
        minimapViewportPaint.alpha = 255
        minimapViewportPaint.strokeWidth = 2f * d
        c.drawRect(
            RectF(indicator.left, indicator.top, indicator.right, indicator.bottom),
            minimapViewportPaint,
        )
    }

    /**
     * The grip on a pinned band's inner edge: a short rounded bar,
     * centred along the edge and straddling it, in the board's ink
     * colour at low alpha — visible as a handle, never mistakable
     * for a clue or a cell.
     */
    private fun drawBandGrip(c: Canvas, band: RectF, vertical: Boolean) {
        gripPaint.color = if (colours.size > 1) colours[1] else colours[0]
        gripPaint.alpha = 150
        val d = resources.displayMetrics.density
        val len = GRIP_LENGTH_DP * d
        val thick = GRIP_THICKNESS_DP * d
        val rect = if (vertical) {
            RectF(
                band.right - thick / 2, (band.top + band.bottom - len) / 2,
                band.right + thick / 2, (band.top + band.bottom + len) / 2,
            )
        } else {
            RectF(
                (band.left + band.right - len) / 2, band.bottom - thick / 2,
                (band.left + band.right + len) / 2, band.bottom + thick / 2,
            )
        }
        c.drawRoundRect(rect, thick / 2, thick / 2, gripPaint)
    }

    /**
     * Draws one captured clue text at a screen-space anchor, with
     * the same alignment handling [drawText] applies on the bitmap
     * canvas — textSize and the vertical-centre nudge scaled by
     * [scale] (screen pixels per game unit).
     */
    private fun drawPinnedText(
        c: Canvas,
        clue: DescribeRecorder.DrawnText,
        anchor: PointF,
        scale: Float,
    ) {
        if (clue.colour !in colours.indices) return
        paint.color = colours[clue.colour]
        paint.typeface =
            if (clue.flags and TEXT_MONO != 0) Typeface.MONOSPACE else Typeface.DEFAULT
        paint.textSize = clue.size * scale
        paint.textAlign = when {
            clue.flags and ALIGN_H_CENTRE != 0 -> Paint.Align.CENTER
            clue.flags and ALIGN_H_RIGHT != 0 -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        val fm = paint.fontMetrics
        val asc = abs(fm.ascent)
        val desc = abs(fm.descent)
        val alignedY = if (clue.flags and ALIGN_V_CENTRE != 0) {
            anchor.y + (asc - (asc + desc) / 2)
        } else {
            anchor.y
        }
        c.drawText(clue.text, anchor.x, alignedY, paint)
    }

    override fun onSizeChanged(viewW: Int, viewH: Int, oldW: Int, oldH: Int) {
        lastDrag?.let { revertDragInProgress(it) }
        w = viewW.coerceAtLeast(1)
        h = viewH.coerceAtLeast(1)
        Log.d("GameView", "onSizeChanged: $w, $h")
        rebuildBitmap()
        if (isInEditMode) {
            // Draw a little placeholder to aid UI editing
            val d = checkNotNull(ContextCompat.getDrawable(context, R.drawable.net)) { "Missing R.drawable.net" }
            val s = min(w, h)
            val mx = (w - s) / 2
            val my = (h - s) / 2
            d.bounds = Rect(mx, my, mx + s, my + s)
            d.draw(canvas)
        }
    }

    fun rebuildBitmap() {
        density = if (isInEditMode) {
            1f
        } else {
            when (limitDpi) {
                LIMIT_OFF -> 1f
                LIMIT_AUTO -> min(parent.suggestDensity(w, h), resources.displayMetrics.density)
                LIMIT_ON -> resources.displayMetrics.density
            }
        }
        Log.d("GameView", "density: $density")
        wDip = (w.toFloat() / density).roundToInt().coerceAtLeast(1)
        hDip = (h.toFloat() / density).roundToInt().coerceAtLeast(1)
        overdrawX = ((ZOOM_OVERDRAW_PROPORTION * wDip).roundToInt() * density).roundToInt()
        overdrawY = ((ZOOM_OVERDRAW_PROPORTION * hDip).roundToInt() * density).roundToInt()
        // texture size limit, see http://stackoverflow.com/a/7523221/6540
        val maxTextureSize = maxTextureSize
        // Assumes maxTextureSize >= (w,h) otherwise you get checkerboard edges
        // https://github.com/chrisboyle/sgtpuzzles/issues/199
        overdrawX = min(overdrawX, (maxTextureSize.x - w) / 2)
        overdrawY = min(overdrawY, (maxTextureSize.y - h) / 2)
        bitmap.recycle()
        bitmap = createBitmap(
            (w + 2 * overdrawX).coerceAtLeast(1),
            (h + 2 * overdrawY).coerceAtLeast(1), BITMAP_CONFIG)
        clear()
        canvas = Canvas(bitmap)
        canvasRestoreJustAfterCreation = canvas.save()
        resetZoomForClear()
        redrawForInitOrZoomChange()
    }

    private val maxTextureSize: Point
        get() {
            val maxW = canvas.maximumBitmapWidth
            val maxH = canvas.maximumBitmapHeight
            if (maxW < 2048 || maxH < 2048) {
                return Point(maxW, maxH)
            }
            // maxW/maxH are otherwise likely a lie, and we should be careful of OOM risk anyway
            // https://github.com/chrisboyle/sgtpuzzles/issues/195
            val metrics = resources.displayMetrics
            val largestDimension = max(metrics.widthPixels, metrics.heightPixels)
            return if (largestDimension > 2048) Point(4096, 4096) else Point(2048, 2048)
        }

    fun clear() {
        bitmap.eraseColor(backgroundColour)
    }

    fun refreshColours(whichBackend: BackendName, newColours: FloatArray) {
        val checkerboardDrawable = checkNotNull(ContextCompat.getDrawable(
            context,
            if (night) R.drawable.checkerboard_night else R.drawable.checkerboard
        )) { "Missing R.drawable.checkerboard" }
        val checkerboard = (checkerboardDrawable as BitmapDrawable).bitmap
        checkerboardPaint.shader =
            BitmapShader(checkerboard, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        colours = IntArray(newColours.size / 3)
        for (i in 0 until newColours.size / 3) {
            val colour = Color.rgb(
                (newColours[i * 3] * 255).toInt(),
                (newColours[i * 3 + 1] * 255).toInt(),
                (newColours[i * 3 + 2] * 255).toInt()
            )
            colours[i] = colour
        }
        if (night) {
            // Only replace colours[0] at night: Untangle uses a darker grey to distinguish the play area
            colours[0] = ContextCompat.getColor(context, R.color.game_background)
            @ColorRes val nightColours = whichBackend.nightColours // doesn't include background
            for (i in 1 until colours.size) {
                //Log.d("GameView", "\t<color name=\"" + resourceName + "\">" + String.format("#%06x", (0xFFFFFF & colours[i])) + "</color>");
                if (nightColours.size >= i) {
                    @ColorRes val nightRes = nightColours[i - 1]
                    if (nightRes != 0) {
                        colours[i] = ContextCompat.getColor(context, nightRes)
                    }
                }  // else GenerateBackendsTask is broken, but don't crash
            }
        }
        if (colours.isNotEmpty()) {
            setBackgroundColor(colours[0])
        } else {
            setBackgroundColor(defaultBackgroundColour)
        }
    }

    override fun setBackgroundColor(@ColorInt colour: Int) {
        super.setBackgroundColor(colour)
        backgroundColour = colour
    }

    @get:ColorInt
    @get:UsedByJNI
    override val defaultBackgroundColour: Int
        /** Unfortunately backends do things like setting other colours as fractions of this, so
         * e.g. black (night mode) would make all of Undead's monsters white - but we replace all
         * the colours in night mode anyway.  */
        get() = ContextCompat.getColor(context, R.color.fake_game_background_to_derive_colours_from)

    @UsedByJNI
    override fun clipRect(x: Int, y: Int, w: Int, h: Int) {
        canvas.restoreToCount(canvasRestoreJustAfterCreation)
        canvasRestoreJustAfterCreation = canvas.save()
        canvas.setMatrix(zoomMatrix)
        canvas.clipRect(RectF(x - 0.5f, y - 0.5f, x + w - 0.5f, y + h - 0.5f))
    }

    @UsedByJNI
    override fun unClip(left: Int, top: Int, right: Int, bottom: Int) {
        canvas.restoreToCount(canvasRestoreJustAfterCreation)
        canvasRestoreJustAfterCreation = canvas.save()
        canvas.setMatrix(zoomMatrix)
        canvas.clipRect(
            left - 0.5f,
            top - 0.5f,
            right - 1.5f,
            bottom - 1.5f
        )
    }

    @UsedByJNI
    override fun fillRect(x: Int, y: Int, w: Int, h: Int, colour: Int) {
        paint.color = colours[colour]
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false // required for regions in Map to look continuous (and by API)
        if (w == 1 && h == 1) {
            canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
        } else if ((w == 1) xor (h == 1)) {
            canvas.drawLine(
                x.toFloat(),
                y.toFloat(),
                (x + w - 1).toFloat(),
                (y + h - 1).toFloat(),
                paint
            )
        } else {
            canvas.drawRect(x - 0.5f, y - 0.5f, x + w - 0.5f, y + h - 0.5f, paint)
        }
        paint.isAntiAlias = true
    }

    @UsedByJNI
    override fun drawLine(
        thickness: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        colour: Int
    ) {
        paint.color = colours[colour]
        paint.strokeWidth = thickness.coerceAtLeast(1f)
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.strokeWidth = 1f
    }

    @UsedByJNI
    override fun drawPoly(
        thickness: Float,
        points: IntArray,
        ox: Int,
        oy: Int,
        line: Int,
        fill: Int
    ) {
        val path = Path()
        path.moveTo((points[0] + ox).toFloat(), (points[1] + oy).toFloat())
        for (i in 1 until points.size / 2) {
            path.lineTo((points[2 * i] + ox).toFloat(), (points[2 * i + 1] + oy).toFloat())
        }
        path.close()
        // cheat slightly: polygons up to square look prettier without (and adjacent squares want to
        // look continuous in lightup)
        val disableAntiAlias = points.size <= 8 // 2 per point
        if (disableAntiAlias) paint.isAntiAlias = false
        drawPoly(thickness, path, line, fill)
        paint.isAntiAlias = true
    }

    private fun drawPoly(thickness: Float, p: Path, lineColour: Int, fillColour: Int) {
        if (fillColour != -1) {
            paint.color = colours[fillColour]
            paint.style = Paint.Style.FILL
            canvas.drawPath(p, paint)
        }
        paint.color = colours[lineColour]
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = thickness.coerceAtLeast(1f)
        canvas.drawPath(p, paint)
        paint.strokeWidth = 1f
    }

    @UsedByJNI
    override fun drawCircle(
        thickness: Float,
        x: Float,
        y: Float,
        r: Float,
        lineColour: Int,
        fillColour: Int
    ) {
        drawCircleInternal(
            thickness,
            x,
            y,
            r.coerceAtLeast(0.4f),
            lineColour,
            if (r <= 0.5f) lineColour else fillColour
        )
    }

    private fun drawCircleInternal(
        thickness: Float,
        x: Float,
        y: Float,
        r: Float,
        lineColour: Int,
        fillColour: Int
    ) {
        if (fillColour != -1) {
            paint.color = colours[fillColour]
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, r, paint)
        }
        paint.color = colours[lineColour]
        paint.style = Paint.Style.STROKE
        if (thickness > 1f) {
            paint.strokeWidth = thickness
        }
        canvas.drawCircle(x, y, r, paint)
        paint.strokeWidth = 1f
    }

    @UsedByJNI
    override fun drawText(x: Int, y: Int, flags: Int, size: Int, colour: Int, text: String) {
        paint.color = colours[colour]
        paint.style = Paint.Style.FILL
        paint.typeface =
            if (flags and TEXT_MONO != 0) Typeface.MONOSPACE else Typeface.DEFAULT
        paint.textSize = size.toFloat()
        val fm = paint.fontMetrics
        val asc = abs(fm.ascent)
        val desc = abs(fm.descent)
        if (flags and ALIGN_H_CENTRE != 0) paint.textAlign =
            Paint.Align.CENTER else if (flags and ALIGN_H_RIGHT != 0) paint.textAlign =
            Paint.Align.RIGHT else paint.textAlign = Paint.Align.LEFT
        val alignedY = if (flags and ALIGN_V_CENTRE != 0) y + (asc - (asc + desc) / 2).toInt() else y
        canvas.drawText(text, x.toFloat(), alignedY.toFloat(), paint)
    }

    @UsedByJNI
    override fun blitterAlloc(w: Int, h: Int): Int {
        for (i in blitters.indices) {
            if (blitters[i] == null) {
                val zoom = getXScale(zoomMatrix)
                blitters[i] =
                    createBitmap((zoom * w).roundToInt(), (zoom * h).roundToInt(), BITMAP_CONFIG)
                return i
            }
        }
        error("No free blitter found!")
    }

    @UsedByJNI
    override fun blitterFree(i: Int) {
        blitters[i]?.recycle()
        blitters[i] = null
    }

    private fun blitterPosition(x: Int, y: Int, save: Boolean): PointF {
        val f = floatArrayOf(x.toFloat(), y.toFloat())
        zoomMatrix.mapPoints(f)
        f[0] = floor(f[0].toDouble()).toFloat()
        f[1] = floor(f[1].toDouble()).toFloat()
        if (save) {
            f[0] *= -1f
            f[1] *= -1f
        }
        return PointF(f[0], f[1])
    }

    @UsedByJNI
    override fun blitterSave(i: Int, x: Int, y: Int) {
        blitters[i]?.let {
            val blitterPosition = blitterPosition(x, y, true)
            Canvas(it).drawBitmap(bitmap, blitterPosition.x, blitterPosition.y, null)
        }
    }

    @UsedByJNI
    override fun blitterLoad(i: Int, x: Int, y: Int) {
        blitters[i]?.let {
            val blitterPosition = blitterPosition(x, y, false)
            Canvas(bitmap).drawBitmap(it, blitterPosition.x, blitterPosition.y, null)
        }
    }

    @VisibleForTesting
    fun screenshot(gameCoords: Rect, gameSizeInGameCoords: Point): Bitmap {
        val offX = (wDip - gameSizeInGameCoords.x) / 2
        val offY = (hDip - gameSizeInGameCoords.y) / 2
        val r = RectF(
            (gameCoords.left + offX).toFloat(),
            (gameCoords.top + offY).toFloat(),
            (gameCoords.right + offX).toFloat(),
            (gameCoords.bottom + offY).toFloat()
        )
        zoomMatrix.mapRect(r)
        return Bitmap.createBitmap(
            bitmap,
            r.left.toInt(),
            r.top.toInt(),
            (r.right - r.left).toInt(),
            (r.bottom - r.top).toInt()
        )
    }

    companion object {
        private const val TAG = "GameView"
        private const val LEFT_BUTTON = 0x0200
        private const val MIDDLE_BUTTON = 0x201
        private const val RIGHT_BUTTON = 0x202
        private const val LEFT_DRAG = 0x203 //MIDDLE_DRAG = 0x204, RIGHT_DRAG = 0x205,
        private const val LEFT_RELEASE = 0x206
        const val FIRST_MOUSE = LEFT_BUTTON
        const val LAST_MOUSE = 0x208
        private const val MOD_CTRL = 0x1000
        private const val MOD_SHIFT = 0x2000
        private const val ALIGN_V_CENTRE = 0x100
        private const val ALIGN_H_CENTRE = 0x001
        private const val ALIGN_H_RIGHT = 0x002
        private const val TEXT_MONO = 0x10
        private const val DRAG = LEFT_DRAG - LEFT_BUTTON // not bit fields, but there's a pattern
        private const val RELEASE = LEFT_RELEASE - LEFT_BUTTON
        const val CURSOR_UP = 0x209
        const val CURSOR_DOWN = 0x20a
        const val CURSOR_LEFT = 0x20b
        const val CURSOR_RIGHT = 0x20c
        const val CURSOR_SELECT = 0x20d
        const val UI_UNDO = 0x213
        const val UI_REDO = 0x214
        const val MOD_NUM_KEYPAD = 0x4000
        val CURSOR_KEYS = setOf(
            CURSOR_UP,
            CURSOR_DOWN,
            CURSOR_LEFT,
            CURSOR_RIGHT,
            MOD_NUM_KEYPAD or '7'.code,
            MOD_NUM_KEYPAD or '1'.code,
            MOD_NUM_KEYPAD or '9'.code,
            MOD_NUM_KEYPAD or '3'.code
        )
        private const val MAX_ZOOM = 30f
        private const val ZOOM_OVERDRAW_PROPORTION =
            0.25f // of a screen-full, in each direction, that you can see before checkerboard

        // Pinned Pattern clues: dedicated prefs file (kept out of
        // the default preferences the backup export snapshots) and
        // the touch geometry of the bands' grips.
        private const val PATTERN_CLUES_PREFS = "pattern_clues"
        private const val PREF_CLUE_SCALE_TOP = "clue_scale_top"
        private const val PREF_CLUE_SCALE_LEFT = "clue_scale_left"
        private const val STRIP_EDGE_ZONE_DP = 24f
        private const val GRIP_LENGTH_DP = 24f
        private const val GRIP_THICKNESS_DP = 3f

        // ARGB_8888 is viewable in Android Studio debugger but very memory-hungry
        private val BITMAP_CONFIG = Bitmap.Config.RGB_565
    }
}
