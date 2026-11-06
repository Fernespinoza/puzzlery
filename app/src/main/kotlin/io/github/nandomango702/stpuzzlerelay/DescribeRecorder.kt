package io.github.nandomango702.stpuzzlerelay

import io.github.nandomango702.stpuzzlerelay.backend.GameEngine

/**
 * A [GameEngine.ViewCallbacks] that records instead of rendering.
 *
 * Describe board (TalkBack Phase 1) works by swapping this in for the
 * real GameView, forcing one full redraw of the current board through
 * it (GamePlay.describeBoard), then swapping back. The engine draws
 * the whole board synchronously during that call, so when it returns
 * the capture is complete — and the real view's bitmap was never
 * touched, so the player sees no flicker.
 *
 * Blitters are NOT stubbed — they delegate to the real view. The
 * forced redraw frees the old drawstate and builds a new one
 * (midend_force_redraw), and blitter handles are owned by the C
 * drawstate and allocated lazily through whichever callbacks are
 * installed at the time. If the recorder minted its own handles, the
 * new drawstate would keep using them after the swap back, pointing
 * at stale or empty slots in the real view's blitter store (wrong
 * regions pasted on later drags in Pegs, Loopy, Signpost and the
 * other blitter games). Delegating keeps exactly one allocator —
 * the real view's — so handles stay valid across the swap. Saves
 * delegate too: the region the engine means to snapshot is precisely
 * what the real bitmap already shows, since the board state doesn't
 * change during a capture. Loads are the one exception — a load only
 * paints pixels onto a surface, and this recorder has none; the text
 * a load would re-display was already recorded when it was drawn.
 */
class DescribeRecorder(
    private val realView: GameEngine.ViewCallbacks,
) : GameEngine.ViewCallbacks {

    data class DrawnText(
        val x: Int,
        val y: Int,
        val flags: Int,
        val size: Int,
        val colour: Int,
        val text: String,
    )

    val texts = mutableListOf<DrawnText>()

    override fun drawText(x: Int, y: Int, flags: Int, size: Int, colour: Int, text: String) {
        texts += DrawnText(x, y, flags, size, colour, text)
    }

    // The other primitives carry no text, so they are not recorded;
    // only their absence matters (a board with no texts at all gets
    // the formatter's no-text fallback).
    override fun drawLine(thickness: Float, x1: Float, y1: Float, x2: Float, y2: Float, colour: Int) {}

    override fun fillRect(x: Int, y: Int, w: Int, h: Int, colour: Int) {}

    override fun drawCircle(
        thickness: Float,
        x: Float,
        y: Float,
        r: Float,
        lineColour: Int,
        fillColour: Int,
    ) {
    }

    override fun drawPoly(thickness: Float, points: IntArray, ox: Int, oy: Int, line: Int, fill: Int) {}

    override fun clipRect(x: Int, y: Int, w: Int, h: Int) {}
    override fun unClip(left: Int, top: Int, right: Int, bottom: Int) {}

    override fun blitterAlloc(w: Int, h: Int): Int = realView.blitterAlloc(w, h)

    override fun blitterFree(i: Int) = realView.blitterFree(i)

    override fun blitterSave(i: Int, x: Int, y: Int) = realView.blitterSave(i, x, y)

    override fun blitterLoad(i: Int, x: Int, y: Int) {}

    override val defaultBackgroundColour: Int
        get() = realView.defaultBackgroundColour

    override fun postInvalidateOnAnimation() {}
}
