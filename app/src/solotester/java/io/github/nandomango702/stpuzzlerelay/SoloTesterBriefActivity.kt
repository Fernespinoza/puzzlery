package io.github.nandomango702.stpuzzlerelay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import io.github.nandomango702.stpuzzlerelay.backend.SOLO

/**
 * The Solo tester build's home screen: a one-screen brief, then one
 * tap into a Solo game at the default preset. This activity exists
 * only in the solotester flavor (it is declared in that flavor's
 * manifest, and SGTPuzzles/GameChooser route here by class name when
 * BuildConfig.TESTER_BUILD is set). It is the launcher screen on
 * every launch, so a tester can re-read the brief at any time just
 * by reopening the app; the game stays one tap away.
 */
class SoloTesterBriefActivity : NightModeHelper.ActivityWithNightMode() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tester_brief)
        ViewCompat.setAccessibilityHeading(
            findViewById<TextView>(R.id.testerBriefTitle), true
        )
        findViewById<Button>(R.id.testerPlayButton).setOnClickListener {
            // Exactly the launch a chooser tile tap performs: the
            // backend URI with no params means the default preset.
            startActivity(Intent(this, GamePlay::class.java).apply {
                data = Uri.fromParts(GamePlay.OUR_SCHEME, SOLO.toString(), null)
            })
        }
        findViewById<Button>(R.id.testerFeedbackButton).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tester_feedback_url)))
            )
        }
    }
}
