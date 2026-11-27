package io.github.nandomango702.stpuzzlerelay

import io.github.nandomango702.stpuzzlerelay.launch.MenuEntry

/**
 * Pure helpers over a puzzle's preset tree (the bridge's menu
 * entries), shared by the stats screen's sections and pinned by
 * the harness and unit tests.
 *
 * The settled split (grilling Q6): completion math — chooser
 * badges, dailies, the stats summary figures — walks TOP-LEVEL
 * presets only, forever. Stats and favorites use the full tree:
 * presets nested under submenus (Loopy's "More..." set) render in
 * Stats as a Bonus section with its own solved count, and can be
 * starred and checked like any other preset — they just never
 * count toward a completion figure.
 */
object PresetTree {

    /**
     * Every playable preset in [entries], in menu order, descending
     * into submenus — the full preset tree.
     */
    fun flatten(entries: List<MenuEntry>): List<MenuEntry> =
        entries.flatMap { entry ->
            when {
                entry.params != null -> listOf(entry)
                entry.submenu != null -> flatten(entry.submenu!!.toList())
                else -> emptyList()
            }
        }

    /**
     * Splits [entries] into the top-level presets (in picker order)
     * and the nested ones (every preset under a submenu, in menu
     * order). Together they are the full tree; apart they are the
     * two sections the stats screen renders.
     */
    fun partition(entries: List<MenuEntry>): Pair<List<MenuEntry>, List<MenuEntry>> {
        val topLevel = entries.filter { it.params != null }
        val nested = entries
            .filter { it.params == null && it.submenu != null }
            .flatMap { flatten(listOf(it)) }
        return topLevel to nested
    }

    /** How many of [presets] have their params in [solvedParams]. */
    fun solvedCount(presets: List<MenuEntry>, solvedParams: Set<String>): Int =
        presets.count { it.params != null && it.params in solvedParams }
}
