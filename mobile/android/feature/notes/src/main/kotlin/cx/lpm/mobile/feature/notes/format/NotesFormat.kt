package cx.lpm.mobile.feature.notes.format

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dates the way the iOS notes screens say them, relative to [now] in [zone]. Every
 * notes timestamp is unix MILLISECONDS.
 */
class NotesClock(
    val now: Instant,
    val zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
) {
    private val mediumDate = DateTimeFormatter.ofLocalizedDate(
        FormatStyle.MEDIUM
    ).withLocale(locale)
    private val shortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    /**
     * "22m ago", "1d ago", "1w ago" — `RelativeDateTimeFormatter` with the abbreviated
     * style: the largest calendar unit that isn't zero, not rounded. Nothing for an
     * unstamped item.
     */
    fun relative(millis: Long): String {
        if (millis <= 0) return ""
        val from = Instant.ofEpochMilli(millis).atZone(zone)
        val to = now.atZone(zone)
        val past = !from.isAfter(to)
        val (start, end) = if (past) from to to else to to from
        var cursor = start
        for (unit in Unit.entries) {
            val amount = unit.chrono.between(cursor, end)
            if (amount > 0) return phrase(amount, unit, past)
            cursor = cursor.plus(amount, unit.chrono)
        }
        return phrase(0, Unit.Seconds, past = false)
    }

    /** "Today", "Yesterday", or "Oct 5, 2026": a medium date, relative when it can be. */
    fun dayLabel(day: LocalDate): String {
        val today = now.atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "Today"
            -1L -> "Yesterday"
            1L -> "Tomorrow"
            else -> mediumDate.format(day)
        }
    }

    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** "3:41 PM"; nothing for an unstamped note. */
    fun clockTime(millis: Long): String {
        if (millis <= 0) return ""
        return shortTime.format(Instant.ofEpochMilli(millis).atZone(zone))
    }

    private fun phrase(amount: Long, unit: Unit, past: Boolean): String {
        val words = "$amount${unit.short}"
        return if (past) "$words ago" else "in $words"
    }

    private enum class Unit(val chrono: ChronoUnit, val short: String) {
        Years(ChronoUnit.YEARS, "y"),
        Months(ChronoUnit.MONTHS, "mo"),
        Weeks(ChronoUnit.WEEKS, "w"),
        Days(ChronoUnit.DAYS, "d"),
        Hours(ChronoUnit.HOURS, "h"),
        Minutes(ChronoUnit.MINUTES, "m"),
        Seconds(ChronoUnit.SECONDS, "s")
    }
}

/**
 * "245 KB", "1.2 MB", "12 bytes" — `ByteCountFormatter`'s file style: decimal units,
 * whole kilobytes, up to one decimal for megabytes and two above.
 */
fun noteSizeText(bytes: Int, locale: Locale = Locale.getDefault()): String {
    fun scaled(divisor: Double, digits: Int, unit: String): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = digits
            minimumFractionDigits = 0
        }
        return "${number.format(bytes / divisor)} $unit"
    }
    return when {
        bytes == 0 -> "Zero KB"
        bytes == 1 -> "1 byte"
        bytes < KILO -> "$bytes bytes"
        bytes < KILO * KILO -> scaled(KILO, 0, "KB")
        bytes < KILO * KILO * KILO -> scaled(KILO * KILO, 1, "MB")
        else -> scaled(KILO * KILO * KILO, 2, "GB")
    }
}

private const val KILO = 1000.0
