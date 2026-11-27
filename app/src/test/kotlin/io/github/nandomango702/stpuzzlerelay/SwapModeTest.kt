package io.github.nandomango702.stpuzzlerelay

import io.github.nandomango702.stpuzzlerelay.backend.LOOPY
import io.github.nandomango702.stpuzzlerelay.backend.MINES
import io.github.nandomango702.stpuzzlerelay.buttons.SwapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The "Swap press and long press" toggle's TalkBack label
 * (v0.7.0 Round 5.1): the label must carry the mode in both
 * states, and the toggled label must be the mirror of the
 * untoggled one. Mirrors the standalone harness checks, which
 * run the same assertions against the real SwapMode.kt with a
 * stub resolver.
 */
class SwapModeTest {

    private val resolve: (Int) -> String = { res ->
        when (res) {
            R.string.tb_swap_label -> "Swap press and long press: press %1\$s, long press %2\$s"
            R.string.tb_swap_action_uncover -> "uncovers"
            R.string.tb_swap_action_flag -> "flags"
            R.string.tb_swap_action_left_click -> "acts as left click"
            R.string.tb_swap_action_right_click -> "acts as right click"
            else -> error("unexpected string resource $res")
        }
    }

    @Test
    fun minesLabelsNameTheMode() {
        assertEquals(
            "Swap press and long press: press uncovers, long press flags",
            SwapMode.label(MINES, false, resolve)
        )
        assertEquals(
            "Swap press and long press: press flags, long press uncovers",
            SwapMode.label(MINES, true, resolve)
        )
    }

    @Test
    fun genericLabelsNameTheMode() {
        assertEquals(
            "Swap press and long press: press acts as left click, long press acts as right click",
            SwapMode.label(LOOPY, false, resolve)
        )
        assertEquals(
            "Swap press and long press: press acts as right click, long press acts as left click",
            SwapMode.label(LOOPY, true, resolve)
        )
    }

    @Test
    fun toggledLabelDiffersFromUntoggled() {
        for (backend in listOf(MINES, LOOPY)) {
            assertNotEquals(
                SwapMode.label(backend, false, resolve),
                SwapMode.label(backend, true, resolve)
            )
        }
    }

    @Test
    fun togglingSwapsTheTwoActions() {
        // The press action of the toggled label is the long-press
        // action of the untoggled one, and vice versa — the toggle
        // swaps the two actions, nothing else.
        val off = SwapMode.label(MINES, false, resolve)
        val on = SwapMode.label(MINES, true, resolve)
        assertEquals(
            "Swap press and long press: press uncovers, long press flags", off
        )
        assertEquals(
            "Swap press and long press: press flags, long press uncovers", on
        )
        // Mirror check, independent of the exact verbs: the
        // toggled label's press phrase is the untoggled label's
        // long-press phrase. Anchors sit past the "Swap press
        // and long press:" prefix, which itself contains the
        // words press / long press.
        fun pressAction(label: String) = label.substringAfter(": press ").substringBefore(", long press")
        fun longPressAction(label: String) = label.substringAfter(", long press ")
        assertEquals(longPressAction(off), pressAction(on))
        assertEquals(pressAction(off), longPressAction(on))
    }
}
