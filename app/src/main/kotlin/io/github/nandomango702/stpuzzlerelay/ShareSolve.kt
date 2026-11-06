package io.github.nandomango702.stpuzzlerelay

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Share-my-solve result texts (spec: share-my-solve-spec.md,
 * Fernando 2026-10-29; pulled forward from v0.6.0 at his call,
 * 2026-10-31). Two formats over the same data: the emoji format
 * (the default, Wordle-style) and a plain-label variant for
 * contexts where emoji read badly — screen readers announce bare
 * emoji names, so the plain one spells the facts in words.
 *
 * Pure builders: GamePlay gathers the live values (including the
 * streak, which needs a Context) and passes them in.
 */
object ShareSolve {

    /** v0.4.0's release day: Daily #1. The number grows forever. */
    private const val DAILY_EPOCH = "2026-10-30"

    /** 1-based day number of a daily date ("YYYY-MM-DD") since the daily launched. */
    fun dailyNumber(dateStr: String): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val epoch = fmt.parse(DAILY_EPOCH)!!.time
        val date = fmt.parse(dateStr)!!.time
        return ((date - epoch) / 86_400_000L).toInt() + 1
    }

    private val SIZE_REGEX = Regex("^(\\d+)x(\\d+)")

    /**
     * The "Size/Preset" part of line 2: WxH derived from the game's
     * params when they carry a plain size, else the preset's title,
     * else null (line 2 is then just the puzzle name).
     */
    fun sizeLabel(params: String?, presetTitle: String?): String? {
        if (params != null) {
            SIZE_REGEX.find(params)?.let { return "${it.groupValues[1]}x${it.groupValues[2]}" }
        }
        return presetTitle
    }

    /** (emoji format, plain format) for one completed solve. */
    fun build(
        puzzleName: String,
        sizeLabel: String?,
        timeText: String,
        newBest: Boolean,
        dailyDate: String?,
        streak: Int?,
    ): Pair<String, String> {
        val headerEmoji = StringBuilder("Puzzlery")
        val line2 = if (sizeLabel != null) "$puzzleName • $sizeLabel" else puzzleName
        val line3Emoji = StringBuilder("⏱️ $timeText")
        val line3Plain = StringBuilder("Time $timeText")
        if (dailyDate != null) {
            headerEmoji.append(" — Daily #").append(dailyNumber(dailyDate))
        }
        if (newBest) {
            line3Emoji.append(" ⚡")
            line3Plain.append(" · New best")
        }
        if (dailyDate != null && streak != null) {
            line3Emoji.append(" | 🔥 ").append(streak)
            line3Plain.append(" · Streak ").append(streak)
        }
        val emoji = "$headerEmoji\n$line2\n$line3Emoji"
        val plain = "$headerEmoji\n$line2\n$line3Plain"
        return emoji to plain
    }
}
