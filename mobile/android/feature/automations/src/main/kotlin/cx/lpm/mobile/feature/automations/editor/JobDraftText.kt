package cx.lpm.mobile.feature.automations.editor

import cx.lpm.mobile.feature.automations.format.capitalized
import cx.lpm.mobile.feature.automations.format.randomDaysPhrase
import java.text.BreakIterator

/** The footer under "Frequency": what the schedule as set will do. */
fun JobEditorDraft.scheduleSummary(): String = when (scheduleMode) {
    ScheduleMode.Manual -> "Runs only when you start it."

    ScheduleMode.Interval -> {
        val unit = when (intervalUnit) {
            IntervalUnit.Days -> "day"
            IntervalUnit.Minutes -> "minute"
            IntervalUnit.Hours -> "hour"
        }
        fun plural(n: Int) = if (n == 1) unit else "${unit}s"
        if (varyInterval && intervalMaxValue > intervalValue) {
            "Every $intervalValue–$intervalMaxValue ${plural(intervalMaxValue)}. " +
                "The gap is picked fresh after every run."
        } else {
            "Every $intervalValue ${plural(intervalValue)}."
        }
    }

    ScheduleMode.Time -> {
        val days = summaryDayPhrase()
        val at = if (randomWindow) "between $time and $untilTime" else "at $time"
        val sentence = if (randomWindow && timesPerDay > 1) {
            "$timesPerDay times ${if (days == "every day") "a day" else "on $days"} $at"
        } else {
            "${days.capitalized()} $at"
        }
        val note = if (randomWindow) {
            " The time inside the window is picked for you and changes each day."
        } else {
            " It starts within a few minutes of that time, so automations set to the same " +
                "hour don't all begin at once."
        }
        "$sentence.$note"
    }
}

/** "every day", "Mon, Thu", or the weekly draw when the job uses only some days. */
private fun JobEditorDraft.summaryDayPhrase(): String {
    val selected = WEEKDAYS.filter { it in days }
    if (!randomDays || pickDays >= dayPool) {
        if (selected.isEmpty() || selected.size == WEEKDAYS.size) return "every day"
        return selected.joinToString(", ") { it.capitalized() }
    }
    return randomDaysPhrase(pickDays, selected.toSet())
}

/** Minutes past midnight for "HH:mm", or null when it isn't a time. */
fun parseTimeMinutes(time: String): Int? {
    val parts = time.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/**
 * A URL-safe slug for [label], uniquified against [taken] so a new job never shadows
 * another job's saved history (the desktop's uniqueKey over slugify(label)).
 */
fun uniqueJobId(label: String, taken: Set<String>): String {
    val slug = label.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
    val base = slug.split("-").filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "job" }
    if (base !in taken) return base
    var n = 2
    while ("$base-$n" in taken) n++
    return "$base-$n"
}

/** The first user-perceived character, so a pasted run of emoji keeps only one. */
internal fun firstGrapheme(value: String): String {
    if (value.isEmpty()) return value
    val it = BreakIterator.getCharacterInstance()
    it.setText(value)
    val end = it.next()
    return if (end == BreakIterator.DONE) value else value.substring(0, end)
}
