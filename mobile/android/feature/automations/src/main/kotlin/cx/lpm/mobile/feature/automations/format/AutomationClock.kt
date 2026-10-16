package cx.lpm.mobile.feature.automations.format

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Dates the way iOS says them, relative to [now] in [zone]. Timestamps are unix
 * seconds, as the Mac sends them.
 */
class AutomationClock(
    val now: Instant,
    val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault()
) {
    /**
     * "in 9 hours", "tomorrow", "2 days ago", "next week" — iOS's
     * `.relative(presentation: .named)`: the largest unit, rounded below a day and
     * counted in calendar days and weeks above it.
     */
    fun relative(seconds: Long): String {
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

    /**
     * "Today at 10:00 AM" / "Mon at 10:00 AM" / "Jul 24 at 10:00 AM" — the clock time on
     * the day named the way you'd say it out loud. Beyond a week, the date.
     */
    fun clock(seconds: Long): String {
        val date = Instant.ofEpochSecond(seconds).atZone(zone)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(date)
        val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), date.toLocalDate())
        val day = when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            abs(days) < DAYS_IN_WEEK -> DateTimeFormatter.ofPattern("EEE", locale).format(date)
            else -> DateTimeFormatter.ofPattern("MMM d", locale).format(date)
        }
        return "$day at $time"
    }

    private fun phrase(value: Long, unit: String, future: Boolean): String {
        val words = "$value $unit${if (value == 1L) "" else "s"}"
        return if (future) "in $words" else "$words ago"
    }

    private fun named(value: Long, unit: String, next: String, last: String): String =
        when (value) {
            1L -> next
            -1L -> last
            else -> phrase(abs(value), unit, value > 0)
        }

    private fun weekStart(date: LocalDate, first: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(first))

    private companion object {
        const val MINUTE = 60L
        const val HOUR = 3600L
        const val MINUTES_IN_HOUR = 60L
        const val HOURS_IN_DAY = 24L
        const val DAYS_IN_WEEK = 7L
        const val DAYS_IN_MONTH = 28L
        const val MONTHS_IN_YEAR = 12L
    }
}

/** "1 min 12 sec", "1 hr 2 min 5 sec" — iOS's condensed abbreviated duration. */
fun automationDurationText(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    val parts = buildList {
        if (h > 0) add("$h hr")
        if (m > 0) add("$m min")
        if (s > 0 || (h == 0 && m == 0)) add("$s sec")
    }
    return parts.joinToString(" ")
}

/**
 * "45s" / "4m 12s" / "1h 03m" — a clock that ticks in place, short enough to sit in a
 * status pill without resizing it every second.
 */
fun automationElapsedText(since: Long, nowSeconds: Long): String {
    val elapsed = maxOf(0L, nowSeconds - since)
    return when {
        elapsed < 60 -> "${elapsed}s"
        elapsed < 3600 -> String.format(Locale.ROOT, "%dm %02ds", elapsed / 60, elapsed % 60)
        else -> String.format(Locale.ROOT, "%dh %02dm", elapsed / 3600, elapsed % 3600 / 60)
    }
}

/** A run's cost on its history row: "$0", "$0.42", "$12" (Stats' `formatUsd`). */
fun automationUsdText(value: Double): String = when {
    value <= 0 -> "$0"
    value < USD_CENTS_BELOW -> String.format(Locale.US, "$%.2f", value)
    else -> "$" + NumberFormat.getIntegerInstance().format(value.roundToLong())
}

/** A run's cost in its conversation, as a currency amount: "$0.42". */
fun automationCurrencyText(value: Double): String =
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance("USD") }
        .format(value)

private const val USD_CENTS_BELOW = 10
