package cx.lpm.mobile.feature.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.LinkStatusLine
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import kotlinx.coroutines.delay

/**
 * A terminal screen's title: the tab's name over how long its agent has been at it
 * ("3m 5s"). The connection's status takes that line whenever the Mac isn't
 * connected; a plain shell has no line at all.
 */
@Composable
internal fun TerminalTitle(
    label: String,
    statusLine: LinkStatusLine?,
    agent: AgentTiming?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = LpmTheme.typography.headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        when {
            statusLine != null -> LinkStatusLabel(statusLine)
            agent != null -> AgentElapsed(agent)
        }
    }
}

@Composable
private fun AgentElapsed(agent: AgentTiming) {
    val now by produceState(agent.until ?: System.currentTimeMillis(), agent) {
        if (agent.until != null) return@produceState
        while (true) {
            delay(nextTick(value - agent.since))
            value = System.currentTimeMillis()
        }
    }
    Text(
        text = preciseDuration(
            ((agent.until ?: now) - agent.since).coerceAtLeast(0) / MILLIS_PER_SECOND
        ),
        style = LpmTheme.typography.caption2.copy(
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum"
        ),
        color = LpmTheme.colors.secondaryLabel,
        maxLines = 1
    )
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val PRECISE_FOR_MILLIS = 3_600_000L

/** Ticks each second while the reading shows seconds (its first hour), then each minute. */
private fun nextTick(elapsed: Long): Long {
    val step = if (elapsed <
        PRECISE_FOR_MILLIS
    ) {
        MILLIS_PER_SECOND
    } else {
        MILLIS_PER_SECOND * SECONDS_PER_MINUTE
    }
    return step - ((elapsed % step) + step) % step
}

/** "45s", "4m 30s", "2h 5m". */
private fun preciseDuration(secs: Long): String {
    if (secs < SECONDS_PER_MINUTE) return "${secs}s"
    val mins = secs / SECONDS_PER_MINUTE
    if (mins < SECONDS_PER_MINUTE) {
        val rest = secs % SECONDS_PER_MINUTE
        return if (rest > 0) "${mins}m ${rest}s" else "${mins}m"
    }
    val rest = mins % SECONDS_PER_MINUTE
    val hours = mins / SECONDS_PER_MINUTE
    return if (rest > 0) "${hours}h ${rest}m" else "${hours}h"
}
