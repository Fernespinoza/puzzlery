package io.github.nandomango702.stpuzzlerelay.playlist

import io.github.nandomango702.stpuzzlerelay.NightModeHelper
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import io.github.nandomango702.stpuzzlerelay.R
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.databinding.ActivityPlaylistEditorBinding
import io.github.nandomango702.stpuzzlerelay.databinding.PlaylistGameRowBinding

/**
 * Creates or edits a playlist. The user names it, then taps puzzles to select
 * them; the playlist order is the order in which puzzles were tapped.
 */
class PlaylistEditorActivity : NightModeHelper.ActivityWithNightMode() {
    private lateinit var binding: ActivityPlaylistEditorBinding
    private val games = BackendName.all.sortedBy { it.displayName }

    /** Source names of selected puzzles, in selection order (= play order). */
    private val selectedOrder = mutableListOf<String>()
    private var editPosition = -1
    private lateinit var adapter: GamesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        // Edge-to-edge is enforced (targetSdk 36), so lift the content above the
        // system navigation bar / keyboard, following the pattern in GameChooser.
        // Top inset too: without it the toolbar slides under the status bar
        // and the title clips through it. Sides too: in landscape the nav
        // bar sits on a side edge, and without the side insets the rows
        // run underneath it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(systemBars() or displayCutout() or ime())
            v.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.new_playlist)
        }
        editPosition = intent.getIntExtra(EXTRA_PLAYLIST_POSITION, -1)
        if (editPosition >= 0) {
            PlaylistStore.load(this).getOrNull(editPosition)?.let { existing ->
                supportActionBar?.title = getString(R.string.edit_playlist_title)
                binding.playlistNameInput.setText(existing.name)
                // Keep only puzzles that still exist, in the saved order.
                selectedOrder.addAll(existing.games.filter { g -> games.any { it.toString() == g } })
            }
        }
        adapter = GamesAdapter()
        binding.gameList.adapter = adapter
        binding.saveButton.setOnClickListener { savePlaylist() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun savePlaylist() {
        val name = binding.playlistNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.playlist_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedOrder.isEmpty()) {
            Toast.makeText(this, R.string.playlist_pick_at_least_one, Toast.LENGTH_SHORT).show()
            return
        }
        val playlists = PlaylistStore.load(this)
        val existing = playlists.getOrNull(editPosition)
        // An in-progress run survives an edit only if the puzzles themselves
        // are untouched (a rename is fine); changing the games ends the run.
        val keepSession = existing != null && existing.games == selectedOrder.toList()
        val playlist = Playlist(
            name,
            selectedOrder.toList(),
            if (keepSession) existing.sessionGames else null,
            if (keepSession) existing.sessionIndex else 0,
        )
        if (editPosition in playlists.indices) {
            playlists[editPosition] = playlist
        } else {
            playlists.add(playlist)
        }
        PlaylistStore.save(this, playlists)
        // Board slots follow the same rule as the run: a rename with the
        // games untouched carries the boards to the new name; an edit
        // that ends the run drops them.
        if (existing != null) {
            if (keepSession && existing.name != name) {
                PlaylistStore.renameBoardSlots(this, existing.name, name, playlist.games)
            } else if (!keepSession) {
                PlaylistStore.clearBoardSlots(this, existing.name, existing.games)
            }
        }
        finish()
    }

    private inner class GamesAdapter : BaseAdapter() {
        override fun getCount() = games.size
        override fun getItem(position: Int): Any = games[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView?.tag as? PlaylistGameRowBinding)
                ?: PlaylistGameRowBinding.inflate(
                    LayoutInflater.from(this@PlaylistEditorActivity), parent, false
                ).also { it.root.tag = it }
            val backend = games[position]
            val sourceName = backend.toString()
            val order = selectedOrder.indexOf(sourceName)
            row.gameName.text = backend.displayName
            row.gameCheckbox.isChecked = order >= 0
            row.gameOrder.text = if (order >= 0) "${order + 1}" else ""
            row.root.contentDescription = if (order >= 0) {
                "${backend.displayName}, selected, position ${order + 1}"
            } else {
                backend.displayName
            }
            row.root.setOnClickListener {
                if (selectedOrder.contains(sourceName)) {
                    selectedOrder.remove(sourceName)
                } else {
                    selectedOrder.add(sourceName)
                }
                notifyDataSetChanged()
            }
            return row.root
        }
    }

    companion object {
        const val EXTRA_PLAYLIST_POSITION = "playlist_position"
    }
}
