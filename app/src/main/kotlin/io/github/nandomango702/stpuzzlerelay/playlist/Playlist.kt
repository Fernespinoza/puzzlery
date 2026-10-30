package io.github.nandomango702.stpuzzlerelay.playlist

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A named, ordered list of puzzles. Each entry is a [BackendName] source name
 * (e.g. "loopy"), so playlists survive renames of display names.
 *
 * A playlist also carries its own in-progress run, if any: [sessionGames] is
 * the order that run was launched in (shuffle included) and [sessionIndex]
 * the position it stopped on. Each playlist tracks its run independently.
 */
data class Playlist(
    val name: String,
    val games: List<String>,
    val sessionGames: List<String>? = null,
    val sessionIndex: Int = 0,
) {
    val hasSession: Boolean
        get() = !sessionGames.isNullOrEmpty() && sessionIndex in sessionGames.indices
}

/**
 * Loads and saves playlists as a JSON array in the default SharedPreferences,
 * so they persist across app restarts. Corrupt data is discarded, never crashes.
 */
object PlaylistStore {
    private const val KEY = "playlists_v1"

    fun load(context: Context): MutableList<Playlist> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(json)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val games = mutableListOf<String>()
                val ga = o.optJSONArray("games") ?: JSONArray()
                for (j in 0 until ga.length()) games.add(ga.getString(j))
                val sessionGames = mutableListOf<String>()
                val sa = o.optJSONArray("session_games") ?: JSONArray()
                for (j in 0 until sa.length()) sessionGames.add(sa.getString(j))
                Playlist(
                    o.getString("name"),
                    games,
                    sessionGames.ifEmpty { null },
                    o.optInt("session_index", 0),
                )
            }
        } catch (_: JSONException) {
            mutableListOf()
        }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        val arr = JSONArray()
        for (p in playlists) {
            val o = JSONObject().put("name", p.name).put("games", JSONArray(p.games))
            if (p.sessionGames != null) {
                o.put("session_games", JSONArray(p.sessionGames))
                o.put("session_index", p.sessionIndex)
            }
            arr.put(o)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit { putString(KEY, arr.toString()) }
    }

    /** Finds the stored playlist a run belongs to: same name, same set of games. */
    private fun indexOfRun(playlists: List<Playlist>, name: String, order: List<String>): Int =
        playlists.indexOfFirst { it.name == name && it.games.sorted() == order.sorted() }

    /** Records (or replaces) the in-progress run of the matching playlist. */
    fun saveSession(context: Context, name: String, order: List<String>, index: Int) {
        val playlists = load(context)
        val i = indexOfRun(playlists, name, order)
        if (i < 0) return
        playlists[i] = playlists[i].copy(sessionGames = order, sessionIndex = index)
        save(context, playlists)
    }

    /** Drops the in-progress run of the matching playlist, if it has one. */
    fun clearSession(context: Context, name: String, order: List<String>) {
        val playlists = load(context)
        val i = indexOfRun(playlists, name, order)
        if (i < 0 || playlists[i].sessionGames == null) return
        playlists[i] = playlists[i].copy(sessionGames = null, sessionIndex = 0)
        save(context, playlists)
    }

    /**
     * Per-game board slots live in the state prefs under keys of the form
     * "<prefix><backend>|pl|<playlist name>" (see GamePlay.slotKey), so a
     * playlist's in-progress boards are separate from general play. These
     * prefixes mirror the slot GamePlay writes for every board.
     */
    private val boardSlotPrefixes = listOf(
        PrefsConstants.SAVED_GAME_PREFIX,
        PrefsConstants.SAVED_COMPLETED_PREFIX,
        PrefsConstants.SAVED_TIMER_PREFIX,
        PrefsConstants.SAVED_USED_SOLVE_PREFIX,
        PrefsConstants.SAVED_USED_RESTART_PREFIX,
    )

    private fun boardSlotKeys(name: String, games: List<String>): List<String> =
        games.flatMap { game -> boardSlotPrefixes.map { "$it$game|pl|$name" } }

    /** Moves a playlist's board slots to its new name after a rename. */
    fun renameBoardSlots(context: Context, oldName: String, newName: String, games: List<String>) {
        if (oldName == newName) return
        val state = context.getSharedPreferences(
            PrefsConstants.STATE_PREFS_NAME, Context.MODE_PRIVATE
        )
        val all = state.all
        state.edit {
            for (game in games) {
                for (prefix in boardSlotPrefixes) {
                    val oldKey = "$prefix$game|pl|$oldName"
                    val newKey = "$prefix$game|pl|$newName"
                    when (val v = all[oldKey]) {
                        is String -> putString(newKey, v)
                        is Boolean -> putBoolean(newKey, v)
                        is Long -> putLong(newKey, v)
                        is Int -> putInt(newKey, v)
                        is Float -> putFloat(newKey, v)
                        else -> continue
                    }
                    remove(oldKey)
                }
            }
        }
    }

    /** Drops a playlist's board slots (run ended by an edit, or deleted). */
    fun clearBoardSlots(context: Context, name: String, games: List<String>) {
        val state = context.getSharedPreferences(
            PrefsConstants.STATE_PREFS_NAME, Context.MODE_PRIVATE
        )
        state.edit {
            for (key in boardSlotKeys(name, games)) remove(key)
        }
    }
}

