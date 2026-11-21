package io.github.nandomango702.stpuzzlerelay

/**
 * The v0.6.0 checkpoint, called a bookmark in the menus: one saved
 * board state per game session, so the player can try a route and
 * return to the saved state if it dead-ends. It is a save-point —
 * strictly stronger than Undo in one way, because after undoing past
 * it and taking a different branch, Undo alone could never get back,
 * but the bookmark still can. Restoring is one-way: it does not
 * create a return point for the branch left behind.
 *
 * Held in memory only, on purpose: never written to SharedPreferences,
 * a file, or the backup export. Storing it would let a bookmark from
 * a different board of the same size be restored later and silently
 * swap the whole puzzle; session scope makes that impossible.
 *
 * The state lives here, outside the Activity, so an Activity
 * recreation (rotation, theme flip) does not lose it. It is keyed by
 * the identity of the run that set it (save slot plus daily date, see
 * GamePlay.bookmarkKey) and is only ever served back to that same
 * run; GamePlay clears it whenever a different run starts.
 *
 * Restoring a bookmark does NOT disqualify the solve and does NOT
 * count as a restart: the state can only be one the player genuinely
 * reached by playing this board — no solver, no foreign states. The
 * restore path in GamePlay.startGameConfirmed is built to leave the
 * solve timer and the record-disqualifying flags untouched (the
 * ROADMAP's build-time checks for this feature).
 */
internal object Bookmark {

    private var key: String? = null
    private var saved: String? = null

    /** Stores [saved] as the checkpoint for the run identified by [key], replacing any previous one. */
    fun set(key: String, saved: String) {
        this.key = key
        this.saved = saved
    }

    /** The checkpoint stored for [key], or null if none was set for this run. */
    fun savedFor(key: String): String? = if (key == this.key) saved else null

    fun has(key: String): Boolean = savedFor(key) != null

    fun clear() {
        key = null
        saved = null
    }
}
