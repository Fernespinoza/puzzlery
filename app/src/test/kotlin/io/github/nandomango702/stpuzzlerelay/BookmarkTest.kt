package io.github.nandomango702.stpuzzlerelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The v0.6.0 bookmark (checkpoint) holder: one in-memory saved state,
 * keyed by the identity of the run that set it. These tests pin the
 * session-scope contract the feature's safety rests on — a bookmark
 * is only ever served back to the run that set it, a new set replaces
 * the old one, and clearing drops it outright. (The restore path's
 * timer/restart-flag behaviour lives in GamePlay and is verified by
 * code trace, not here.)
 */
class BookmarkTest {

    @Before
    fun reset() {
        Bookmark.clear()
    }

    @Test
    fun nothingSetMeansNoBookmark() {
        assertFalse(Bookmark.has("solo|"))
        assertNull(Bookmark.savedFor("solo|"))
    }

    @Test
    fun setThenServedBackToSameRun() {
        Bookmark.set("solo|", "STATE-A")
        assertTrue(Bookmark.has("solo|"))
        assertEquals("STATE-A", Bookmark.savedFor("solo|"))
    }

    @Test
    fun notServedToADifferentRun() {
        Bookmark.set("solo|", "STATE-A")
        // Different puzzle, different playlist slot, and a daily
        // attempt of the same puzzle are all different runs.
        assertFalse(Bookmark.has("mines|"))
        assertNull(Bookmark.savedFor("mines|"))
        assertFalse(Bookmark.has("solo|pl|Weekend|"))
        assertNull(Bookmark.savedFor("solo|pl|Weekend|"))
        assertFalse(Bookmark.has("solo|2026-11-20"))
        assertNull(Bookmark.savedFor("solo|2026-11-20"))
    }

    @Test
    fun secondSetReplacesTheFirst() {
        Bookmark.set("solo|", "STATE-A")
        Bookmark.set("solo|", "STATE-B")
        assertEquals("STATE-B", Bookmark.savedFor("solo|"))
    }

    @Test
    fun setForAnotherRunReplacesRatherThanStacks() {
        Bookmark.set("solo|", "STATE-A")
        Bookmark.set("mines|", "STATE-B")
        assertFalse(Bookmark.has("solo|"))
        assertEquals("STATE-B", Bookmark.savedFor("mines|"))
    }

    @Test
    fun clearDropsTheBookmark() {
        Bookmark.set("solo|", "STATE-A")
        Bookmark.clear()
        assertFalse(Bookmark.has("solo|"))
        assertNull(Bookmark.savedFor("solo|"))
    }

    @Test
    fun repeatedReadsAreIdempotent() {
        Bookmark.set("solo|", "STATE-A")
        assertEquals("STATE-A", Bookmark.savedFor("solo|"))
        assertEquals("STATE-A", Bookmark.savedFor("solo|"))
        assertTrue(Bookmark.has("solo|"))
    }
}
