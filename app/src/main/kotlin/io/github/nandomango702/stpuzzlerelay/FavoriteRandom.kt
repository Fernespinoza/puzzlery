package io.github.nandomango702.stpuzzlerelay

/**
 * Random "From favorites" (v0.7.0): the pure pool logic behind
 * the chooser's two favorite random flows, kept free of Android
 * and engine types so the standalone harness and the unit tests
 * can pin it exactly.
 *
 * Both pools walk a puzzle's FULL preset tree — the enumeration
 * Stats uses (PresetTree.flatten), nested "Bonus" presets
 * included: they can be starred, so they must be reachable here.
 * Preset identity is the raw params string, the same identity
 * favorites (GamePlay.favoriteKeyFor) and bests
 * (GamePlay.bestTimeKeyFor) key off. Solved means a legit best
 * time exists, the standard used everywhere.
 *
 * Choosing "From favorites" is itself the opt-in, so this
 * preference is always on — it is NOT gated behind the Preset
 * progression toggle.
 */
object FavoriteRandom {

    /**
     * Plain "From favorites": when the picked puzzle has favorite
     * presets, the preset to deal is a uniform random one of them,
     * chosen through [randomIndex] (given the pool size, returns an
     * index into it) so tests can drive the choice deterministically.
     * When it has none, null — the caller deals exactly as it
     * always has.
     */
    fun favoriteDeal(
        allPresets: List<String>,
        favorites: Set<String>,
        randomIndex: (bound: Int) -> Int,
    ): String? {
        val pool = allPresets.filter { it in favorites }
        if (pool.isEmpty()) return null
        return pool[randomIndex(pool.size)]
    }

    /**
     * One starred game's three unsolved pools, in cascade order
     * (see [unsolvedCascadeDeal]). Lists are in the game's own
     * preset order; the caller builds them from the settled
     * enumerations — favorites across the full preset tree,
     * top-level as the completion math defines it, nested as
     * PresetTree.partition splits them off.
     */
    data class GamePools(
        /** Unsolved favorite presets, anywhere in the full preset tree. */
        val unsolvedFavorites: List<String>,
        /** Unsolved top-level presets — the completion-chase pool. */
        val unsolvedTopLevel: List<String>,
        /** Unsolved nested ("Bonus") presets. */
        val unsolvedNested: List<String>,
    )

    /** Which cascade tier a deal came from, highest first. */
    enum class Tier { FAVORITES, TOP_LEVEL, NESTED }

    /** A cascade deal: which game (index into the caller's list), its tier, and the preset to launch. */
    data class CascadeDeal(val gameIndex: Int, val tier: Tier, val params: String)

    /**
     * Unsolved "From favorites": the cascade (Fernando's
     * decisions, 2026-11-24, replacing Round 3's within-game-only
     * fallback). The tiers apply at BOTH levels — which game is
     * picked and which preset of it is dealt:
     *
     *  1. FAVORITES — a uniform random game among those with an
     *     unsolved favorite preset, dealing a uniform random one
     *     of that game's unsolved favorites.
     *  2. TOP_LEVEL — else, a uniform random game among those
     *     with an unsolved top-level preset, dealing one of
     *     those. A nested preset is never dealt while any starred
     *     game still has top-level work: this flow is the
     *     completion chase, and nested presets don't advance it.
     *  3. NESTED — else, a uniform random game among those with
     *     an unsolved nested preset, dealing one of those, so the
     *     flow keeps serving unsolved boards until every starred
     *     game's whole tree is solved.
     *
     * Null only when every pool of every game is empty — the
     * caller toasts "none left", which then means exactly that.
     * Both picks (game, then preset) go through [randomIndex],
     * uniform over the tier's eligible games and then over the
     * picked game's pool for that tier.
     */
    fun unsolvedCascadeDeal(
        games: List<GamePools>,
        randomIndex: (bound: Int) -> Int,
    ): CascadeDeal? {
        for (tier in Tier.values()) {
            val eligible = games.indices.filter { games[it].pool(tier).isNotEmpty() }
            if (eligible.isEmpty()) continue
            val gameIndex = eligible[randomIndex(eligible.size)]
            val pool = games[gameIndex].pool(tier)
            return CascadeDeal(gameIndex, tier, pool[randomIndex(pool.size)])
        }
        return null
    }

    private fun GamePools.pool(tier: Tier): List<String> = when (tier) {
        Tier.FAVORITES -> unsolvedFavorites
        Tier.TOP_LEVEL -> unsolvedTopLevel
        Tier.NESTED -> unsolvedNested
    }
}
