package cx.lpm.mobile.feature.memory.format

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A session memory `updatedAt` (unix **seconds** — every Notes timestamp is
 * milliseconds) the way iOS's `.relative(presentation: .named)` says it: "5 minutes
 * ago", "yesterday", "last week". The largest unit, rounded below a day and counted in
 * calendar days and weeks above it; nothing for an unstamped session.
 */
fun relativeMemoryTime(
    seconds: Long,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    if (seconds <= 0) return ""
    val delta = seconds - now.epochSecond
    val secs = abs(delta)
    val future = delta > 0
    if (secs == 0L) return "now"
    if (secs < MINUTE) return phrase(secs, "second", future)
    val minutes = (secs / MINUTE.toDouble()).roundToLong()
    if (minutes < MINUTES_IN_HOUR) return phrase(minutes, "minute", future)
    val hours = (secs / HOUR.toDouble()).roundToLong()
    val today = now.atZone(zone).toLocalDate()
    val day = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, day)
    if (hours < HOURS_IN_DAY || days == 0L) return phrase(hours, "hour", future)
    if (abs(days) < DAYS_IN_WEEK) return named(days, "day", "tomorrow", "yesterday")
    val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(day))
    if (months == 0L || abs(days) < DAYS_IN_MONTH) {
        val first = WeekFields.of(locale).firstDayOfWeek
        val weeks = ChronoUnit.WEEKS.between(weekStart(today, first), weekStart(day, first))
        return named(weeks, "week", "next week", "last week")
    }
    if (abs(months) < MONTHS_IN_YEAR) return named(months, "month", "next month", "last month")
    return named(ChronoUnit.YEARS.between(today, day), "year", "next year", "last year")
}

private fun phrase(value: Long, unit: String, future: Boolean): String {
    val words = "$value $unit${if (value == 1L) "" else "s"}"
    return if (future) "in $words" else "$words ago"
}

private fun named(value: Long, unit: String, next: String, last: String): String = when (value) {
    1L -> next
    -1L -> last
    else -> phrase(abs(value), unit, value > 0)
}

private fun weekStart(date: LocalDate, first: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(first))

private const val MINUTE = 60L
private const val HOUR = 3600L
private const val MINUTES_IN_HOUR = 60L
private const val HOURS_IN_DAY = 24L
private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 28L
private const val MONTHS_IN_YEAR = 12L
