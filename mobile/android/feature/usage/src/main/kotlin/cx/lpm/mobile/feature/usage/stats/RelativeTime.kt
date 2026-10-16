package cx.lpm.mobile.feature.usage.stats

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * How long ago a unix-**milliseconds** moment was, as iOS's
 * `.relative(presentation: .named)` says it: the largest calendar unit that differs,
 * with "yesterday", "last week" and the like for one of a day or longer.
 */
fun relativeUsageTime(unixMillis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val from = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
    val to = Instant.ofEpochMilli(unixMillis).atZone(zone).toLocalDateTime()
    var cursor = from
    val parts = Units.map { (unit, _, _) ->
        val amount = unit.between(cursor, to)
        cursor = cursor.plus(amount, unit)
        amount
    }
    val formatter = RelativeDateTimeFormatter.getInstance()
    val index = parts.indexOfFirst { it != 0L }
    if (index < 0) return formatter.format(Direction.PLAIN, AbsoluteUnit.NOW)
    val (_, relative, named) = Units[index]
    val amount = parts[index]
    val direction = if (amount < 0) Direction.LAST else Direction.NEXT
    if (abs(amount) == 1L && named != null) return formatter.format(direction, named)
    return formatter.format(abs(amount).toDouble(), direction, relative)
}

private val Units = listOf(
    Triple(ChronoUnit.YEARS, RelativeUnit.YEARS, AbsoluteUnit.YEAR),
    Triple(ChronoUnit.MONTHS, RelativeUnit.MONTHS, AbsoluteUnit.MONTH),
    Triple(ChronoUnit.WEEKS, RelativeUnit.WEEKS, AbsoluteUnit.WEEK),
    Triple(ChronoUnit.DAYS, RelativeUnit.DAYS, AbsoluteUnit.DAY),
    Triple(ChronoUnit.HOURS, RelativeUnit.HOURS, null),
    Triple(ChronoUnit.MINUTES, RelativeUnit.MINUTES, null),
    Triple(ChronoUnit.SECONDS, RelativeUnit.SECONDS, null)
)
