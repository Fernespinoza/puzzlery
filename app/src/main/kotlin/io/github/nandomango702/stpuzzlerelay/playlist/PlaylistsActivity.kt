package io.github.nandomango702.stpuzzlerelay.playlist

import io.github.nandomango702.stpuzzlerelay.NightModeHelper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import io.github.nandomango702.stpuzzlerelay.GamePlay
import io.github.nandomango702.stpuzzlerelay.R
import io.github.nandomango702.stpuzzlerelay.backend.BackendName.Companion.byLowerCase
import io.github.nandomango702.stpuzzlerelay.databinding.ActivityPlaylistsBinding
import io.github.nandomango702.stpuzzlerelay.databinding.PlaylistRowBinding

/** Lists the user's saved playlists. Play starts the first puzzle of a playlist. */
class PlaylistsActivity : NightModeHelper.ActivityWithNightMode() {
    private lateinit var binding: ActivityPlaylistsBinding
    private val playlists = mutableListOf<Playlist>()
    private lateinit var adapter: PlaylistsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.playlists)
        }
        // One-time cleanup: the short-lived global session key (banner build)
        // is superseded by per-playlist runs stored in PlaylistStore.
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit { remove("playlist_session_v1") }
        adapter = PlaylistsAdapter()
        binding.playlistList.adapter = adapter
        binding.playlistList.emptyView = binding.emptyView
        binding.newPlaylist.setOnClickListener {
            startActivity(Intent(this, PlaylistEditorActivity::class.java))
        }
        // Thumb reachability: the button lives at the bottom, above the nav bar.
        // The inset goes on the *margin* (lifting the button), not padding:
        // padding would stretch the button down behind the nav bar.
        val baseBottomMargin =
            (binding.newPlaylist.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.newPlaylist) { v, windowInsets ->
            val inset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseBottomMargin + inset
            }
            windowInsets
        }
    }

    override fun onResume() {
        super.onResume()
        playlists.clear()
        playlists.addAll(PlaylistStore.load(this))
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Starts the playlist over from the first puzzle, replacing any
     * in-progress run of it. Used by Play (when no run is going), Restart,
     * and Shuffle. The first board is dealt fresh, not resumed.
     */
    private fun playPlaylist(position: Int, shuffle: Boolean = false) {
        val playlist = playlists.getOrNull(position) ?: return
        val validGames = playlist.games.filter { byLowerCase(it) != null }
        if (validGames.isEmpty()) {
            Toast.makeText(this, R.string.playlist_pick_at_least_one, Toast.LENGTH_SHORT).show()
            return
        }
        // Shuffle only affects this playthrough; the saved playlist order is untouched.
        val playOrder = if (shuffle) validGames.shuffled() else validGames
        playlists[position] = playlist.copy(sessionGames = playOrder, sessionIndex = 0)
        PlaylistStore.save(this, playlists)
        adapter.notifyDataSetChanged()
        launchPlaylist(playlist.name, playOrder, 0, fresh = true)
    }

    /** Resumes the playlist's in-progress run where it stopped. */
    private fun continuePlaylist(position: Int) {
        val playlist = playlists.getOrNull(position) ?: return
        val order = playlist.sessionGames ?: return
        launchPlaylist(playlist.name, order, playlist.sessionIndex, fresh = false)
    }

    /** Starts GamePlay inside the named playlist at the given position of the given order. */
    private fun launchPlaylist(name: String, order: List<String>, index: Int, fresh: Boolean) {
        val backend = byLowerCase(order[index]) ?: return
        startActivity(Intent(this, GamePlay::class.java).apply {
            data = Uri.fromParts(GamePlay.OUR_SCHEME, backend.toString(), null)
            putExtra(GamePlay.EXTRA_PLAYLIST_NAME, name)
            putStringArrayListExtra(GamePlay.EXTRA_PLAYLIST_GAMES, ArrayList(order))
            putExtra(GamePlay.EXTRA_PLAYLIST_INDEX, index)
            putExtra(GamePlay.EXTRA_PLAYLIST_FRESH, fresh)
        })
    }

    private fun editPlaylist(position: Int) {
        startActivity(Intent(this, PlaylistEditorActivity::class.java).apply {
            putExtra(PlaylistEditorActivity.EXTRA_PLAYLIST_POSITION, position)
        })
    }

    private fun deletePlaylist(position: Int) {
        val playlist = playlists.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_playlist_confirm, playlist.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                playlists.removeAt(position)
                PlaylistStore.save(this, playlists)
                PlaylistStore.clearBoardSlots(this, playlist.name, playlist.games)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class PlaylistsAdapter : BaseAdapter() {
        override fun getCount() = playlists.size
        override fun getItem(position: Int): Any = playlists[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView?.tag as? PlaylistRowBinding)
                ?: PlaylistRowBinding.inflate(
                    LayoutInflater.from(this@PlaylistsActivity), parent, false
                ).also { it.root.tag = it }
            val playlist = playlists[position]
            row.playlistName.text = playlist.name
            row.playlistCount.text = resources.getQuantityString(
                R.plurals.playlist_puzzle_count, playlist.games.size, playlist.games.size
            )
            // With a run in progress, Continue gets its own full-width line
            // above the button row (five across wrapped mid-word), Play is
            // replaced by Restart in the row, and the row stays four across.
            // Each playlist tracks its own run, so several can be in
            // progress at once.
            val hasSession = playlist.hasSession
            row.continueButton.visibility = if (hasSession) View.VISIBLE else View.GONE
            row.continueButton.setOnClickListener { continuePlaylist(position) }
            row.playButton.visibility = if (hasSession) View.GONE else View.VISIBLE
            row.playButton.setOnClickListener { playPlaylist(position) }
            row.restartButton.visibility = if (hasSession) View.VISIBLE else View.GONE
            row.restartButton.setOnClickListener { playPlaylist(position) }
            row.shuffleButton.setOnClickListener { playPlaylist(position, shuffle = true) }
            row.editButton.setOnClickListener { editPlaylist(position) }
            row.deleteButton.setOnClickListener { deletePlaylist(position) }
            // Accessibility: the row's buttons already speak; give the row itself a label too.
            row.root.contentDescription =
                "${playlist.name}, ${row.playlistCount.text}"
            return row.root
        }
    }
}
