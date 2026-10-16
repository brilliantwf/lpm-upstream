package cx.lpm.mobile.feature.connection

import android.text.format.DateUtils
import cx.lpm.mobile.core.data.link.MacReach
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A clock time ("3:41 PM"), as `formatted(date: .omitted, time: .shortened)`. */
internal fun Instant.clockTime(zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(this)

/** How long ago ("5 minutes ago", "yesterday"), as `.relative(presentation: .named)`. */
internal fun Instant.relativeTo(now: Instant): String {
    val text = DateUtils.getRelativeTimeSpanString(
        toEpochMilli(),
        now.toEpochMilli(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    return text.replaceFirstChar(Char::lowercaseChar)
}

/** Whether a saved machine answers: the last check, so switching isn't a guess. */
internal fun reachText(
    reach: MacReach?,
    lastConnected: Instant?,
    now: Instant,
    words: ReachWords
): String = when (reach) {
    MacReach.Online -> words.online
    MacReach.Checking -> words.checking
    else -> lastConnected?.let { words.lastConnected(it.relativeTo(now)) } ?: words.notAnswering
}

internal class ReachWords(
    val online: String,
    val checking: String,
    val notAnswering: String,
    val lastConnected: (String) -> String
)
