package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/**
 * The Pattern mode bar (v0.6.0): four real buttons — Fill, Mark,
 * Erase, Pan — in a slim strip in the layout chain directly
 * above the timer/status/bottom-control-bar stack, visible for
 * Pattern only (GamePlay owns the visibility and the mode
 * itself; GameView owns the mode's effect on touches).
 *
 * Exactly one button is selected at a time. The selected button
 * carries View's selected state — the highlight background and
 * the accessibility selected flag TalkBack reports — and each
 * button's content description names its mode ("Fill mode", …).
 * GamePlay announces the new mode on a switch, since focus does
 * not move.
 *
 * The buttons are built here rather than in the two layout
 * files so portrait and landscape can never drift apart; each
 * layout declares just this one element.
 */
class PatternModeBar(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    /** Called when the user taps a mode button (not on [selectMode] syncs). */
    var onModeSelected: ((PatternMode) -> Unit)? = null

    private val modeButtons = mutableMapOf<PatternMode, Button>()

    private var selectedMode: PatternMode = PatternMode.FILL

    init {
        orientation = HORIZONTAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.game_background))
        val density = resources.displayMetrics.density
        val padH = (4 * density).toInt()
        val padV = (3 * density).toInt()
        for (mode in PatternMode.values()) {
            val button = Button(context).apply {
                text = context.getString(labelRes(mode))
                contentDescription = context.getString(descriptionRes(mode))
                textSize = 11f
                minimumHeight = 0
                minHeight = 0
                setPadding(padH, padV, padH, padV)
                background = ContextCompat.getDrawable(
                    context, R.drawable.pattern_mode_button_bg
                )
                val icon = ContextCompat.getDrawable(context, iconRes(mode))?.mutate()
                // The glyphs follow the button's text colour so
                // they read in both themes — except Erase, whose
                // icon is a grey swatch (the game's own colour
                // for an undecided cell) and keeps its grey.
                if (mode != PatternMode.ERASE) {
                    icon?.setTintList(textColors)
                }
                setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null)
                compoundDrawablePadding = (2 * density).toInt()
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    selectMode(mode)
                    onModeSelected?.invoke(mode)
                }
            }
            modeButtons[mode] = button
            addView(button)
        }
        selectMode(PatternMode.FILL)
    }

    /** Marks [mode] as the selected one (visual + accessibility state only). */
    fun selectMode(mode: PatternMode) {
        selectedMode = mode
        for ((m, button) in modeButtons) {
            button.isSelected = m == selectedMode
        }
    }

    companion object {
        fun labelRes(mode: PatternMode): Int = when (mode) {
            PatternMode.FILL -> R.string.pattern_mode_fill
            PatternMode.MARK -> R.string.pattern_mode_mark
            PatternMode.ERASE -> R.string.pattern_mode_erase
            PatternMode.PAN -> R.string.pattern_mode_pan
        }

        fun descriptionRes(mode: PatternMode): Int = when (mode) {
            PatternMode.FILL -> R.string.pattern_mode_fill_desc
            PatternMode.MARK -> R.string.pattern_mode_mark_desc
            PatternMode.ERASE -> R.string.pattern_mode_erase_desc
            PatternMode.PAN -> R.string.pattern_mode_pan_desc
        }

        fun iconRes(mode: PatternMode): Int = when (mode) {
            PatternMode.FILL -> R.drawable.ic_pattern_fill
            PatternMode.MARK -> R.drawable.ic_pattern_mark
            PatternMode.ERASE -> R.drawable.ic_pattern_erase
            PatternMode.PAN -> R.drawable.ic_pattern_pan
        }
    }
}
