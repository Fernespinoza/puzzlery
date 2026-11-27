package io.github.nandomango702.stpuzzlerelay.buttons

import io.github.nandomango702.stpuzzlerelay.R
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.MINES

/**
 * The TalkBack label for the bottom bar's "Swap press and long
 * press" toggle (the '*' key in the control bar; shown in every
 * game whose two mouse buttons differ — see GamePlay's
 * setKeyboardVisibility: all ARROWS_LEFT_RIGHT_CLICK games,
 * plus Palisade and Net).
 *
 * The toggle used to read as a bare "Swap press and long press.
 * Button." — no state — so a TalkBack player could not tell
 * whether their next cell action would be the press action or
 * the long-press action. In Mines that is a game-losing
 * distinction (uncover vs flag). The label therefore always
 * carries the mode: which action a press performs, and which a
 * long press performs, in the toggle's current state. The same
 * string is announced when the toggle flips, since focus stays
 * on the button (the Pattern mode bar's switch-announcement
 * precedent).
 *
 * The two actions are named in the game's own vocabulary where
 * the app already knows it — Mines: uncover / flag. Every
 * other game uses the honest generic wording (which mouse
 * button a press acts as); naming each game's two actions
 * needs per-engine verification and is a follow-up, not a
 * guess made here.
 *
 * Pure: the label is a function of (backend, swapped) plus a
 * string resolver, so the arrangement is harness-testable
 * without Android resources.
 */
object SwapMode {

    /**
     * The two actions the toggle swaps, as string resources,
     * in UNSWAPPED order: first is what a press does, second
     * what a long press does.
     */
    fun actionRes(backend: BackendName): Pair<Int, Int> = when (backend) {
        MINES -> R.string.tb_swap_action_uncover to R.string.tb_swap_action_flag
        else -> R.string.tb_swap_action_left_click to R.string.tb_swap_action_right_click
    }

    /**
     * The full spoken label for the toggle in its current
     * state — what TalkBack reads on focus, and what is
     * announced when the toggle flips.
     */
    fun label(backend: BackendName, swapped: Boolean, resolve: (Int) -> String): String {
        val (pressRes, longPressRes) = actionRes(backend)
        val (press, longPress) =
            if (swapped) longPressRes to pressRes else pressRes to longPressRes
        return resolve(R.string.tb_swap_label).format(resolve(press), resolve(longPress))
    }
}
