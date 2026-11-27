package io.github.nandomango702.stpuzzlerelay

import io.github.nandomango702.stpuzzlerelay.launch.MenuEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Stats preset-tree split (v0.7.0, Fernando's Bonus-section
 * ruling): top-level presets form the main section and its figures;
 * presets nested under submenus form the Bonus section with its own
 * solved count. Mirrors the standalone harness checks.
 */
class PresetTreeTest {

    private fun preset(id: Int, params: String) = MenuEntry(id, "P$id", params)
    private fun submenu(id: Int, vararg children: MenuEntry) =
        MenuEntry(id, "More $id", arrayOf(*children))

    /** A Loopy-shaped tree: two top-level presets, then a submenu. */
    private fun tree() = listOf(
        preset(1, "7x7t0"),
        preset(2, "10x10t0"),
        submenu(3, preset(4, "7x7t1"), preset(5, "10x10t1")),
    )

    @Test
    fun partitionSeparatesTopLevelFromNestedPreservingOrder() {
        val (topLevel, nested) = PresetTree.partition(tree())
        assertEquals(listOf("7x7t0", "10x10t0"), topLevel.map { it.params })
        assertEquals(listOf("7x7t1", "10x10t1"), nested.map { it.params })
    }

    @Test
    fun flattenWalksTheFullTreeInMenuOrder() {
        assertEquals(
            listOf("7x7t0", "10x10t0", "7x7t1", "10x10t1"),
            PresetTree.flatten(tree()).map { it.params },
        )
    }

    @Test
    fun deepNestingLandsInTheBonusSection() {
        val deep = listOf(
            preset(1, "a"),
            submenu(2, preset(3, "b"), submenu(4, preset(5, "c"))),
        )
        val (topLevel, nested) = PresetTree.partition(deep)
        assertEquals(listOf("a"), topLevel.map { it.params })
        assertEquals(listOf("b", "c"), nested.map { it.params })
    }

    @Test
    fun bonusSolvedCountCountsOnlyNestedPresetsWithBests() {
        val (_, nested) = PresetTree.partition(tree())
        // One nested preset solved, plus a TOP-LEVEL preset solved:
        // the bonus count must see only the nested one.
        val solved = setOf("7x7t0", "10x10t1")
        assertEquals(1, PresetTree.solvedCount(nested, solved))
        assertEquals(0, PresetTree.solvedCount(nested, emptySet()))
        assertEquals(2, PresetTree.solvedCount(nested, setOf("7x7t1", "10x10t1")))
    }

    @Test
    fun noSubmenusMeansNoBonusSection() {
        val flat = listOf(preset(1, "a"), preset(2, "b"))
        val (topLevel, nested) = PresetTree.partition(flat)
        assertEquals(2, topLevel.size)
        assertEquals(0, nested.size)
    }

    @Test
    fun emptyTreePartitionsToEmpty() {
        val (topLevel, nested) = PresetTree.partition(emptyList())
        assertEquals(0, topLevel.size)
        assertEquals(0, nested.size)
    }
}
