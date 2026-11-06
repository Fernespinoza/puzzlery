package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import androidx.annotation.StringRes
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.LOOPY
import io.github.nandomango702.stpuzzlerelay.backend.SOLO

/**
 * Per-puzzle TalkBack support tags (Fernando's idea, 2026-10-31):
 * as Describe board support rolls out puzzle by puzzle, each puzzle
 * carries a tag stating what Describe can actually do for it.
 *
 * Levels are evidence-based: a puzzle is tagged only once its
 * Describe output has been verified on-device, and the tag upgrades
 * as the Phase 2 virtual-grid tranches land (the tranche that ships
 * support for a puzzle updates its entry here in the same release).
 * Untagged means "not yet verified" — never read it as unsupported.
 */
object TalkBackSupport {

    enum class Level(@StringRes val phraseRes: Int) {
        /** Describe reads the board's full text, row by row. */
        FULL_TEXT(R.string.tb_level_full_text),

        /** Describe reads the printed clues; the player's own marks are not text. */
        CLUES_ONLY(R.string.tb_level_clues_only),

        /** The board has no text for Describe to read (status line only). */
        NO_TEXT(R.string.tb_level_no_text),
    }

    private val levels: Map<BackendName, Level> = mapOf(
        // Verified 2026-10-31: Describe read all 9 rows exactly.
        SOLO to Level.FULL_TEXT,
        // Verified 2026-10-31: clue rows exact; drawn lines are not text.
        LOOPY to Level.CLUES_ONLY,
        // MINES is deliberately UNTAGGED (external review catch,
        // 2026-10-31): its fresh board has no text, but once cells
        // are uncovered the revealed numbers ARE drawn text — and a
        // hidden cell and a revealed-empty cell both draw nothing,
        // so both would read "blank" and a blind player could not
        // tell them apart. No honest level exists yet; the tag
        // arrives with the Phase 2 Mines grid (v0.7.0).
    )

    fun level(backend: BackendName): Level? = levels[backend]

    /** The level phrase ("Describe board reads the clues only"), or null when untagged. */
    fun phrase(context: Context, backend: BackendName): String? =
        level(backend)?.let { context.getString(it.phraseRes) }

    /** The full tag as shown and spoken in the chooser ("TalkBack: …"), or null when untagged. */
    fun tag(context: Context, backend: BackendName): String? =
        phrase(context, backend)?.let { context.getString(R.string.tb_tag, it) }
}
