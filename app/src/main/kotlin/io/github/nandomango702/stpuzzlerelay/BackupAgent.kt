package io.github.nandomango702.stpuzzlerelay

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants

class BackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        /* For some reason PreferenceManager.getDefaultSharedPreferencesName()
           is private - but the knowledge of its contents is now used widely
           enough that I'm reasonably sure Google wouldn't break it. :-) */
        val defaultPrefs = "io.github.nandomango702.stpuzzlerelay_preferences"
        // The state prefs file comes along too: it holds the records
        // (best times, solve counts, total time), the in-progress boards
        // and the timers. Backing up only the default prefs would keep
        // settings, starred puzzles and daily streaks across a restore
        // while silently losing every record.
        val helper = SharedPreferencesBackupHelper(
            this, defaultPrefs, PrefsConstants.STATE_PREFS_NAME
        )
        addHelper("preferences", helper)
    }
}
