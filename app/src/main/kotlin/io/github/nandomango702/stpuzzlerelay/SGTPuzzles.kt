package io.github.nandomango702.stpuzzlerelay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.app.TaskStackBuilder
import io.github.nandomango702.stpuzzlerelay.launch.GameGenerator

/**
 * The Solo tester build's brief screen, referenced by class name: the
 * class itself lives in the solotester flavor source set, so the
 * standard variant ships no tester code and cannot link to it
 * directly. Only ever used when BuildConfig.TESTER_BUILD is true.
 */
const val TESTER_BRIEF_ACTIVITY =
    "io.github.nandomango702.stpuzzlerelay.SoloTesterBriefActivity"

class SGTPuzzles : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.TESTER_BUILD) {
            // Solo tester build: the brief screen is the front door,
            // whatever deep link or restore brought us here.
            if (GameGenerator.executableIsMissing(this)) {
                finish()
                return
            }
            startActivity(Intent().setClassName(this, TESTER_BRIEF_ACTIVITY))
            finish()
            return
        }
        if (GameGenerator.executableIsMissing(this)) {
            finish()
            return
        }
        TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(intent.setClass(this, GamePlay::class.java))
            .startActivities()
        finish()
    }
}