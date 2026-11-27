package io.github.nandomango702.stpuzzlerelay

import android.content.Context
import androidx.annotation.StringRes
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.LOOPY
import io.github.nandomango702.stpuzzlerelay.backend.MINES
import io.github.nandomango702.stpuzzlerelay.backend.PATTERN
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

        /**
         * Fully playable through the Phase 2 TalkBack grid. The
         * phrase carries "(untested)" until a TalkBack-first
         * player has validated the tranche (the standing Q5/Q15
         * rule); the qualifier is part of the phrase so it drops
         * in one place when validation lands.
         */
        FULL_PLAY(R.string.tb_level_full_play),
    }

    private val levels: Map<BackendName, Level> = mapOf(
        // Phase 2 grid shipped in v0.6.0. The label was held at
        // FULL_TEXT (its verified Describe level) until Fernando
        // matched it to Pattern's on 2026-11-24: FULL_PLAY, with
        // the shared "(untested)" phrase, until a TalkBack-first
        // player validates the tranche.
        SOLO to Level.FULL_PLAY,
        // Verified 2026-10-31: clue rows exact; drawn lines are not text.
        LOOPY to Level.CLUES_ONLY,
        // Phase 2 grid shipped in v0.7.0 (this release): full play
        // via the virtual grid, labelled untested until a
        // TalkBack-first player validates it. (Pattern had no
        // honest Describe level — its Describe output was never
        // device-verified — so it was untagged until now.)
        PATTERN to Level.FULL_PLAY,
        // Phase 2 grid shipped in v0.7.0 (this release): full
        // play via the virtual grid, labelled untested until a
        // TalkBack-first player validates it. Deliberately
        // untagged before (external review catch, 2026-10-31):
        // a hidden cell and a revealed-empty cell both draw no
        // text, so no honest Describe level existed; the grid
        // reads state from the drawn tiles themselves instead.
        MINES to Level.FULL_PLAY,
    )

    fun level(backend: BackendName): Level? = levels[backend]

    /** The level phrase ("Describe board reads the clues only"), or null when untagged. */
    fun phrase(context: Context, backend: BackendName): String? =
        level(backend)?.let { context.getString(it.phraseRes) }

    /** The full tag as shown and spoken in the chooser ("TalkBack: …"), or null when untagged. */
    fun tag(context: Context, backend: BackendName): String? =
        phrase(context, backend)?.let { context.getString(R.string.tb_tag, it) }
}
