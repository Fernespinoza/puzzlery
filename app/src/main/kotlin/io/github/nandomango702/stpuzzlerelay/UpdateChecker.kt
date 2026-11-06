package io.github.nandomango702.stpuzzlerelay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.nandomango702.stpuzzlerelay.config.PrefsConstants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update checks against the GitHub releases feed (Fernando,
 * 2026-10-31): Puzzlery ships as a sideloaded APK on a weekly
 * cadence, so without a store there is no other way for a player
 * to learn a new version exists.
 *
 * Automatic checks are ON by default (Settings can turn them off),
 * run at most once a day from the chooser, and only ever READ:
 * a GET to the public releases API carrying nothing but the app's
 * version in the user agent. Nothing is downloaded or installed
 * without the player tapping Download, which opens the release's
 * APK in their browser like any other download.
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/Fernespinoza/puzzlery/releases/latest"
    private const val ASSET_NAME = "st-puzzle-relay.apk"
    private const val KEY_LAST_CHECK = "updateCheckLastAt"
    private const val KEY_DISMISSED = "updateDismissedVersion"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    class UpdateInfo(val version: String, val downloadUrl: String)

    fun currentVersion(activity: Activity): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Dotted-numeric comparison ("0.10.0" > "0.9.9"); suffixes are ignored per part. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            v.trim().removePrefix("v").split('.')
                .map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * The automatic check, called when the chooser starts. Honours
     * the Settings toggle and the once-a-day throttle; a version
     * the player already dismissed with "Later" stays quiet until
     * a newer one appears.
     */
    fun maybeAutoCheck(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!prefs.getBoolean(PrefsConstants.CHECK_FOR_UPDATES_KEY, true)) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        prefs.edit { putLong(KEY_LAST_CHECK, now) }
        runCheck(activity, manual = false)
    }

    /** The manual check from Settings: ignores toggle and throttle, always reports. */
    fun checkNow(activity: Activity) {
        runCheck(activity, manual = true)
    }

    private fun runCheck(activity: Activity, manual: Boolean) {
        Thread {
            val info = try {
                fetchLatest(activity)
            } catch (e: Exception) {
                null
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                val current = currentVersion(activity)
                when {
                    info == null -> if (manual) {
                        Toast.makeText(
                            activity, R.string.update_check_failed, Toast.LENGTH_LONG,
                        ).show()
                    }
                    isNewer(info.version, current) -> {
                        val dismissed = PreferenceManager
                            .getDefaultSharedPreferences(activity)
                            .getString(KEY_DISMISSED, null)
                        if (manual || dismissed != info.version) {
                            showUpdateDialog(activity, info, current)
                        }
                    }
                    manual -> Toast.makeText(
                        activity,
                        activity.getString(R.string.update_up_to_date, current),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.start()
    }

    private fun showUpdateDialog(activity: Activity, info: UpdateInfo, current: String) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(
                activity.getString(R.string.update_available_message, info.version, current),
            )
            .setPositiveButton(R.string.update_download) { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                } catch (e: Exception) {
                    Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG)
                        .show()
                }
            }
            .setNegativeButton(R.string.update_later) { _, _ ->
                PreferenceManager.getDefaultSharedPreferences(activity).edit {
                    putString(KEY_DISMISSED, info.version)
                }
            }
            .show()
    }

    private fun fetchLatest(activity: Activity): UpdateInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Puzzlery/" + currentVersion(activity))
        }
        try {
            if (conn.responseCode != 200) return null
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = root.optString("tag_name").removePrefix("v")
            if (version.isEmpty()) return null
            var downloadUrl = root.optString("html_url")
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name") == ASSET_NAME) {
                        downloadUrl = asset.optString("browser_download_url", downloadUrl)
                        break
                    }
                }
            }
            if (downloadUrl.isEmpty()) return null
            return UpdateInfo(version, downloadUrl)
        } finally {
            conn.disconnect()
        }
    }
}
