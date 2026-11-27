package io.github.nandomango702.stpuzzlerelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Random "From favorites" (v0.7.0): the favorite-preset
 * preference in the chooser's two favorite random flows. These
 * tests pin:
 *
 *  - the plain flow deals a uniform random favorite preset when
 *    the picked puzzle has any, and deals nothing (null — the
 *    caller keeps its historical deal) when it has none;
 *  - the unsolved flow is the CASCADE (Fernando's decisions,
 *    2026-11-24, replacing Round 3's within-game fallback):
 *    tier 1 deals an unsolved favorite from a game that has one,
 *    tier 2 an unsolved top-level preset, tier 3 an unsolved
 *    nested preset — the tiers govern BOTH the game pick and
 *    the preset dealt, so a nested preset is never dealt while
 *    any starred game still has top-level presets unsolved;
 *  - null comes back only when every starred game's whole tree
 *    is solved (the caller's "none left" toast then means
 *    exactly that).
 *
 * The identical assertions also run in this environment as a
 * standalone harness against the real sources (JUnit is not
 * resolvable in the offline build sandbox), the same split the
 * earlier v0.7.0 rounds used.
 */
class FavoriteRandomTest {

    // A full preset tree in flatten order: two top-level presets,
    // then two nested ("Bonus") ones.
    private val tree = listOf("7x7t0", "10x10t0", "7x7t1", "10x10t1")

    private fun pools(
        favorites: List<String> = emptyList(),
        topLevel: List<String> = emptyList(),
        nested: List<String> = emptyList(),
    ) = FavoriteRandom.GamePools(favorites, topLevel, nested)

    // --- Plain "From favorites" (unchanged by the cascade) ---

    @Test
    fun plainDealsOnlyFavorites() {
        val favorites = setOf("10x10t0", "7x7t1")
        var seenBound = -1
        val picks = (0 until 2).map { i ->
            FavoriteRandom.favoriteDeal(tree, favorites) { bound ->
                seenBound = bound
                i
            }
        }
        assertEquals(2, seenBound)
        assertEquals(listOf("10x10t0", "7x7t1"), picks)
    }

    @Test
    fun plainReachesNestedFavorite() {
        assertEquals(
            "10x10t1",
            FavoriteRandom.favoriteDeal(tree, setOf("10x10t1")) { 0 },
        )
    }

    @Test
    fun plainIsNullWithoutFavorites() {
        var randomCalls = 0
        val countingRandom: (Int) -> Int = { _ -> randomCalls++; 0 }
        assertNull(FavoriteRandom.favoriteDeal(tree, emptySet(), countingRandom))
        assertNull(FavoriteRandom.favoriteDeal(tree, setOf("9x9"), countingRandom))
        assertNull(
            FavoriteRandom.favoriteDeal(emptyList(), setOf("7x7t0"), countingRandom)
        )
        assertEquals(0, randomCalls)
    }

    // --- The unsolved cascade ---

    @Test
    fun cascadeTier1BeatsLowerTiersAtGameLevel() {
        // List order puts the lower tiers first: under Round 3's
        // uniform game pick the top-level game would usually win.
        // The cascade must still deal from the tier-1 game.
        val games = listOf(
            pools(topLevel = listOf("a-top")),
            pools(nested = listOf("b-nested")),
            pools(favorites = listOf("c-fav")),
        )
        val deal = FavoriteRandom.unsolvedCascadeDeal(games) { 0 }
        assertEquals(
            FavoriteRandom.CascadeDeal(2, FavoriteRandom.Tier.FAVORITES, "c-fav"),
            deal,
        )
    }

    @Test
    fun cascadeTier2BeatsTier3AtGameLevel() {
        val games = listOf(
            pools(nested = listOf("a-nested")),
            pools(topLevel = listOf("b-top")),
        )
        val deal = FavoriteRandom.unsolvedCascadeDeal(games) { 0 }
        assertEquals(
            FavoriteRandom.CascadeDeal(1, FavoriteRandom.Tier.TOP_LEVEL, "b-top"),
            deal,
        )
    }

    @Test
    fun cascadeWithinTier2NeverDealsNested() {
        val games = listOf(
            pools(topLevel = listOf("t1", "t2"), nested = listOf("n1")),
        )
        val picks = (0 until 2).map { i ->
            FavoriteRandom.unsolvedCascadeDeal(games) { bound ->
                if (bound == 2) i else 0
            }
        }
        assertEquals(listOf("t1", "t2"), picks.map { it!!.params })
        assertEquals(
            listOf(FavoriteRandom.Tier.TOP_LEVEL, FavoriteRandom.Tier.TOP_LEVEL),
            picks.map { it!!.tier },
        )
    }

    @Test
    fun cascadeTier3OnlyWhenHigherTiersGloballyEmpty() {
        val games = listOf(
            pools(nested = listOf("a-nested")),
            pools(),
        )
        val deal = FavoriteRandom.unsolvedCascadeDeal(games) { 0 }
        assertEquals(
            FavoriteRandom.CascadeDeal(0, FavoriteRandom.Tier.NESTED, "a-nested"),
            deal,
        )
    }

    @Test
    fun cascadeNestedFavoriteDealsBeforeTopLevelNonFavorite() {
        // The game's only unsolved favorite is a nested preset;
        // it still outranks the same game's top-level pool.
        val games = listOf(
            pools(favorites = listOf("nested-fav"), topLevel = listOf("t1")),
        )
        val deal = FavoriteRandom.unsolvedCascadeDeal(games) { 0 }
        assertEquals(
            FavoriteRandom.CascadeDeal(0, FavoriteRandom.Tier.FAVORITES, "nested-fav"),
            deal,
        )
    }

    @Test
    fun cascadePicksUniformlyAmongEligibleGamesThenPool() {
        val games = listOf(
            pools(favorites = listOf("a-fav")),
            pools(),
            pools(favorites = listOf("c-fav1", "c-fav2")),
        )
        val bounds = mutableListOf<Int>()
        val deal = FavoriteRandom.unsolvedCascadeDeal(games) { bound ->
            bounds += bound
            bound - 1 // last eligible game, last preset in its pool
        }
        assertEquals(listOf(2, 2), bounds)
        assertEquals(
            FavoriteRandom.CascadeDeal(2, FavoriteRandom.Tier.FAVORITES, "c-fav2"),
            deal,
        )
    }

    @Test
    fun cascadeSolvedOutGameContributesNothing() {
        // A game whose pools are all empty (everything solved, as
        // the caller builds them) is invisible to every tier.
        val games = listOf(
            pools(),
            pools(topLevel = listOf("b-top")),
        )
        val deal = FavoriteRandom.unsolvedCascadeDeal(games) { 0 }
        assertEquals(
            FavoriteRandom.CascadeDeal(1, FavoriteRandom.Tier.TOP_LEVEL, "b-top"),
            deal,
        )
    }

    @Test
    fun cascadeIsNullWhenWholeTreeSolved() {
        var randomCalls = 0
        val countingRandom: (Int) -> Int = { _ -> randomCalls++; 0 }
        assertNull(
            FavoriteRandom.unsolvedCascadeDeal(
                listOf(pools(), pools()), countingRandom,
            )
        )
        assertNull(FavoriteRandom.unsolvedCascadeDeal(emptyList(), countingRandom))
        assertEquals(0, randomCalls)
    }
}
