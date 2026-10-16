package cx.lpm.mobile.feature.git.branch

import android.icu.text.RelativeDateTimeFormatter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * "2 days ago" from a branch's commit date, which the Mac sends as unix seconds or an
 * ISO date; anything else is shown as it came. The largest calendar unit that isn't
 * zero, as Foundation's `RelativeDateTimeFormatter` picks it.
 */
internal fun branchSubtitle(
    raw: String,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val date = text.toDoubleOrNull()?.let { Instant.ofEpochMilli((it * MILLIS).toLong()) }
        ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: return text
    val from = ZonedDateTime.ofInstant(now, zone)
    val to = ZonedDateTime.ofInstant(date, zone)
    val units = listOf(
        ChronoUnit.YEARS to RelativeDateTimeFormatter.RelativeUnit.YEARS,
        ChronoUnit.MONTHS to RelativeDateTimeFormatter.RelativeUnit.MONTHS,
        ChronoUnit.WEEKS to RelativeDateTimeFormatter.RelativeUnit.WEEKS,
        ChronoUnit.DAYS to RelativeDateTimeFormatter.RelativeUnit.DAYS,
        ChronoUnit.HOURS to RelativeDateTimeFormatter.RelativeUnit.HOURS,
        ChronoUnit.MINUTES to RelativeDateTimeFormatter.RelativeUnit.MINUTES
    )
    val (amount, unit) = units.firstNotNullOfOrNull { (chrono, unit) ->
        chrono.between(from, to).takeIf { it != 0L }?.let { it to unit }
    } ?: (ChronoUnit.SECONDS.between(from, to) to RelativeDateTimeFormatter.RelativeUnit.SECONDS)
    val direction = if (amount < 0) {
        RelativeDateTimeFormatter.Direction.LAST
    } else {
        RelativeDateTimeFormatter.Direction.NEXT
    }
    return RelativeDateTimeFormatter.getInstance().format(
        kotlin.math.abs(amount).toDouble(),
        direction,
        unit
    )
}

private const val MILLIS = 1_000
