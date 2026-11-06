package io.github.nandomango702.stpuzzlerelay

import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.isEmpty
import androidx.preference.minusAssign
import androidx.preference.plusAssign
import io.github.nandomango702.stpuzzlerelay.Utils.copyVersionToClipboard
import io.github.nandomango702.stpuzzlerelay.Utils.sendFeedbackDialog
import io.github.nandomango702.stpuzzlerelay.backend.BRIDGES
import io.github.nandomango702.stpuzzlerelay.backend.BackendName
import io.github.nandomango702.stpuzzlerelay.backend.BackendName.Companion.byLowerCase
import io.github.nandomango702.stpuzzlerelay.backend.GameEngineImpl
import io.github.nandomango702.stpuzzlerelay.backend.UNEQUAL
import io.github.nandomango702.stpuzzlerelay.config.ConfigBuilder.Event.CFG_PREFS
import io.github.nandomango702.stpuzzlerelay.config.ConfigPreferencesBuilder
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.AMOLED_BLACK_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.BRIDGES_SHOW_H_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CATEGORY_BUTTON_PRESSES
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CATEGORY_CHOOSER
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CATEGORY_THIS_GAME
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CATEGORY_THIS_GAME_DISPLAY_AND_INPUT
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LATIN_SHOW_M_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.LONG_PRESS_TIMEOUT
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.MOUSE_BACK_KEY
import io.github.nandomango702.stpuzzlerelay.backup.Backup
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.CHECK_UPDATES_NOW_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.EXPORT_BACKUP_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.IMPORT_BACKUP_KEY
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.PLACEHOLDER_SEND_FEEDBACK
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants.UNEQUAL_SHOW_H_KEY

class PrefsActivity : NightModeHelper.ActivityWithNightMode(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, PrefsMainFragment())
                .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.Settings)
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(amoledPrefListener)
    }

    /** Restyle immediately when the AMOLED setting is toggled on this screen. */
    private val amoledPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AMOLED_BLACK_KEY) recreateForTheme()
        }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(amoledPrefListener)
        super.onDestroy()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        return supportFragmentManager.popBackStackImmediate() || super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    abstract class CurrentGamePrefFragment : PreferenceFragmentCompat() {

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val root = super.onCreateView(inflater, container, savedInstanceState)
            val appCompatActivity = activity as AppCompatActivity
            appCompatActivity.setSupportActionBar(root.findViewById(R.id.toolbar))
            appCompatActivity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            return root
        }

        fun onCreateCurrentGamePrefs(backend: BackendName?, thisGameCategory: PreferenceCategory) {
            if (backend == null) {
                preferenceScreen -= thisGameCategory
            } else {
                thisGameCategory.title = backend.displayName
                with(GameEngineImpl.forPreferencesOnly(backend, requireContext())) {
                    configEvent(
                        CFG_PREFS.jni,
                        ConfigPreferencesBuilder(thisGameCategory, requireContext(), this, backend)
                    )
                }
            }
        }

        protected fun requirePref(key: CharSequence): Preference = findPreference(key)!!

        protected fun requireCategory(key: CharSequence): PreferenceCategory = findPreference(key)!!
    }

    class PrefsMainFragment : CurrentGamePrefFragment() {

        /** Save-picker for the backup export (Storage Access Framework — no storage permission). */
        private val exportBackupLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri == null) return@registerForActivityResult // picker cancelled
                val context = requireContext()
                val ok = runCatching {
                    // Build the JSON BEFORE opening the stream: a
                    // build failure must not leave a 0-byte file
                    // behind. "wt" truncates — plain "w" is not
                    // guaranteed to on every provider, and a leftover
                    // tail would corrupt the backup.
                    val json = Backup.buildExport(context).toString(2)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("no output stream")
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) R.string.backup_saved else R.string.backup_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }

        /** Open-picker for the backup import. Any file type may be picked;
         *  validation (Backup.parseImport) is the real gate. */
        private val importBackupLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@registerForActivityResult // picker cancelled
                val context = requireContext()
                val text = runCatching {
                    // Bounded read: the picker accepts any file, and
                    // a real backup is tens of KB — refuse to slurp
                    // an unbounded one into memory.
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buf = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            buf.write(chunk, 0, n)
                            if (buf.size() > Backup.MAX_IMPORT_BYTES) {
                                error("backup file too large")
                            }
                        }
                        buf.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
                if (text == null) {
                    Toast.makeText(context, R.string.backup_read_failed, Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                when (val parsed = Backup.parseImport(context, text)) {
                    is Backup.ImportParse.Failure -> Toast.makeText(
                        context,
                        when (parsed.reason) {
                            Backup.ImportFailure.NOT_JSON, Backup.ImportFailure.WRONG_APP ->
                                R.string.backup_not_puzzlery
                            Backup.ImportFailure.SCHEMA_TOO_NEW -> R.string.backup_too_new
                            Backup.ImportFailure.MALFORMED -> R.string.backup_damaged
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                    is Backup.ImportParse.Success -> confirmImport(parsed.backup)
                }
            }

        /** The replace-wholesale warning, showing which backup is about to land. */
        private fun confirmImport(backup: Backup.ParsedBackup) {
            val activity = requireActivity()
            var message = getString(
                R.string.import_confirm_message,
                backup.exportedAt.ifEmpty { "—" },
                backup.appVersion.ifEmpty { "—" },
            )
            // Wrong-typed entries the filter dropped (Claude review
            // round 2: drops must not be silent — the player should
            // know part of the file didn't make it).
            if (backup.dropped > 0) {
                message += "\n\n" + resources.getQuantityString(
                    R.plurals.import_dropped_entries, backup.dropped, backup.dropped,
                )
            }
            AlertDialog.Builder(activity)
                .setTitle(R.string.import_confirm_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.import_action) { _, _ ->
                    if (Backup.applyImport(activity, backup)) {
                        Toast.makeText(
                            activity.applicationContext, R.string.backup_imported, Toast.LENGTH_SHORT,
                        ).show()
                        // Clean restart into the chooser: every screen re-reads
                        // the imported state, nothing stale survives in memory.
                        startActivity(
                            Intent(activity, GameChooser::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        activity.finish()
                    } else {
                        Toast.makeText(activity, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences)
            val backend = byLowerCase(requireActivity().intent.getStringExtra(BACKEND_EXTRA))
            val chooserCategory = requireCategory(CATEGORY_CHOOSER)
            val thisGameCategory = requireCategory(CATEGORY_THIS_GAME)
            onCreateCurrentGamePrefs(backend, thisGameCategory)
            if (backend != null) {
                preferenceScreen -= chooserCategory
                addGameSpecificAndroidPreferences(backend, thisGameCategory)
            }
            requirePref("about_content").apply {
                summary = String.format(getString(R.string.about_content), BuildConfig.VERSION_NAME)
                setOnPreferenceClickListener {
                    copyVersionToClipboard(requireContext())
                    true
                }
            }
            requirePref(PLACEHOLDER_SEND_FEEDBACK).setOnPreferenceClickListener {
                sendFeedbackDialog(requireContext())
                true
            }
            requirePref(CHECK_UPDATES_NOW_KEY).setOnPreferenceClickListener {
                UpdateChecker.checkNow(requireActivity())
                true
            }
            requirePref(EXPORT_BACKUP_KEY).setOnPreferenceClickListener {
                exportBackupLauncher.launch(Backup.suggestedFileName())
                true
            }
            requirePref(IMPORT_BACKUP_KEY).setOnPreferenceClickListener {
                importBackupLauncher.launch(arrayOf("*/*"))
                true
            }
        }

        private fun addGameSpecificAndroidPreferences(
            whichBackend: BackendName,
            thisGameCategory: PreferenceCategory
        ) {
            if (!whichBackend.isLatin)    thisGameCategory -= requirePref(LATIN_SHOW_M_KEY)
            if (whichBackend !== BRIDGES) thisGameCategory -= requirePref(BRIDGES_SHOW_H_KEY)
            if (whichBackend !== UNEQUAL) thisGameCategory -= requirePref(UNEQUAL_SHOW_H_KEY)
            thisGameCategory += SwitchPreferenceCompat(requireContext()).apply {
                order = 1000  // after upstream prefs, before XML prefs
                isIconSpaceReserved = false
                key = GamePlay.getArrowKeysPrefName(whichBackend, resources.configuration)
                setDefaultValue(GamePlay.getArrowKeysDefault(whichBackend, resources))
                setTitle(R.string.showArrowKeys)
                if (whichBackend.isLatin) setSummary(R.string.arrowKeysLatinSummary)
            }
        }

        companion object {
            const val BACKEND_EXTRA = "backend"
        }
    }

    class PrefsDisplayAndInputFragment : CurrentGamePrefFragment() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs_display_and_input, rootKey)
            val backend = byLowerCase(requireActivity().intent.getStringExtra(PrefsMainFragment.BACKEND_EXTRA))
            val thisGameCategory = requireCategory(CATEGORY_THIS_GAME_DISPLAY_AND_INPUT)
            onCreateCurrentGamePrefs(backend, thisGameCategory)
            if (thisGameCategory.isEmpty()) preferenceScreen -= thisGameCategory
            if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
                requirePref(MOUSE_BACK_KEY).isVisible = false
            }
            requireCategory(CATEGORY_BUTTON_PRESSES) += SeekBarPreference(requireContext()).apply {
                order = 0
                isIconSpaceReserved = false
                key = LONG_PRESS_TIMEOUT
                setTitle(R.string.longPressTimeout)
                min = 100
                max = 2000
                seekBarIncrement = 50
                showSeekBarValue = true
                // Calling this after the pref is inflated from XML doesn't work :-( hence creating the pref here
                setDefaultValue(ViewConfiguration.getLongPressTimeout())
            }
        }

    }

    companion object {
        private const val TITLE_TAG = "prefsActivityTitle"
    }
}
