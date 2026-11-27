package io.github.nandomango702.stpuzzlerelay

/**
 * Preset progression (v0.7.0): the pure dealing logic behind the
 * smart chooser deal, kept free of Android and engine types so the
 * standalone harness and the unit tests can pin it exactly.
 *
 * The deal walks a puzzle's TOP-LEVEL presets in picker order — the
 * same set and order the daily rotation and the completion math
 * use. Nested presets live in Stats and favorites, never here, and
 * custom games aren't presets at all.
 */
object PresetProgression {

    /**
     * The first preset in [presets] (picker order) that is not in
     * [solved], or null when every preset is solved (or there are
     * none). Solved entries that are not presets are ignored.
     */
    fun nextUnsolved(presets: List<String>, solved: Set<String>): String? =
        presets.firstOrNull { it !in solved }

    /**
     * The preset a chooser tap deals, or null when the caller should
     * fall back to the historical default generation: progression is
     * off, or the puzzle has no top-level presets at all.
     *
     * With progression on and at least one preset: the next unsolved
     * preset when one exists (the common case — [randomIndex] is not
     * consulted); otherwise a uniform random preset, chosen through
     * [randomIndex] (given the preset count, returns an index into
     * [presets]) so tests can drive the choice deterministically.
     */
    fun deal(
        presets: List<String>,
        solved: Set<String>,
        progressionOn: Boolean,
        randomIndex: (bound: Int) -> Int,
    ): String? {
        if (!progressionOn || presets.isEmpty()) return null
        nextUnsolved(presets, solved)?.let { return it }
        return presets[randomIndex(presets.size)]
    }
}
