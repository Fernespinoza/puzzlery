package io.github.nandomango702.stpuzzlerelay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Completion badge for a chooser tile (Fernando's design): a white
 * check on a dark translucent disc, ringed by a progress arc that
 * grows clockwise from the top as the puzzle's top-level presets are
 * legitimately solved, over a faint full track ring. At 100% the
 * whole disc turns gold and the check turns dark — the "cleared"
 * trophy. The dark disc keeps the thin ring legible on busy icons
 * (Flood, SameGame) in both themes.
 */
class ClearedBadgeDrawable(
    private val fraction: Float,
    private val complete: Boolean,
) : Drawable() {
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (complete) GOLD else Color.argb(150, 0, 0, 0)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(70, 0, 0, 0)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(70, 255, 255, 255)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (complete) Color.rgb(33, 33, 33) else Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f
        canvas.drawCircle(cx, cy, r, discPaint)
        if (complete) {
            // A hairline dark edge so the gold disc still separates
            // from pale icons in day mode.
            edgePaint.strokeWidth = r * 0.08f
            canvas.drawCircle(cx, cy, r - edgePaint.strokeWidth / 2f, edgePaint)
        } else {
            val stroke = r * 0.18f
            trackPaint.strokeWidth = stroke
            arcPaint.strokeWidth = stroke
            val ringR = r - stroke / 2f - r * 0.06f
            val ring = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            canvas.drawCircle(cx, cy, ringR, trackPaint)
            if (fraction > 0f) {
                canvas.drawArc(ring, -90f, 360f * fraction, false, arcPaint)
            }
        }
        checkPaint.strokeWidth = r * 0.20f
        val check = Path().apply {
            moveTo(cx - r * 0.40f, cy + r * 0.05f)
            lineTo(cx - r * 0.13f, cy + r * 0.33f)
            lineTo(cx + r * 0.42f, cy - r * 0.32f)
        }
        canvas.drawPath(check, checkPaint)
    }

    private val paints = listOf(discPaint, edgePaint, trackPaint, arcPaint, checkPaint)

    /** Each paint's baked-in alpha (the disc and rings are translucent by design). */
    private val baseAlphas = paints.map { it.alpha }

    override fun setAlpha(alpha: Int) {
        // Scale from each paint's own alpha: assigning the raw value
        // would wipe the baked-in translucency (150 disc, 70 rings).
        paints.forEachIndexed { i, paint -> paint.alpha = baseAlphas[i] * alpha / 255 }
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paints.forEach { it.colorFilter = colorFilter }
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private val GOLD = Color.rgb(255, 193, 7)
    }
}
