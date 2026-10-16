package cx.lpm.mobile.feature.activity

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.AgentState
import cx.lpm.mobile.core.ui.AgentToneText
import cx.lpm.mobile.core.ui.pulseAlpha
import cx.lpm.mobile.core.ui.sweepProgress
import cx.lpm.mobile.core.ui.tint
import cx.lpm.mobile.core.ui.workingSweep

internal val CardShape = RoundedCornerShape(20.dp)

/**
 * One agent or automation: an icon tile, what it is, what it's doing, and how long
 * it has been doing it. The whole card is the tap target. [now] (unix millis) is
 * ticked by the screen so the elapsed label stays live; [hideProject] when the
 * section header already carries the project's name and tags.
 */
@Composable
fun ActivityRowCard(
    row: ActivityRow,
    now: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    hideProject: Boolean = false
) {
    val colors = LpmTheme.colors
    val named = row.project.name.isNotEmpty() && !hideProject
    // What the row calls the thing that is running: the tab's own name for an agent,
    // the job's name for an automation. Null when it has none of its own.
    val subject = if (row.kind == ActivityRowKind.Automation) row.title else row.tabTitle
    val headline = if (named) row.project.label else subject ?: row.title
    // The subject earns its own line whenever the headline isn't already it.
    val subtitle = subject.takeUnless { it == headline }
    val meta = listOfNotNull(row.title.takeIf { row.kind == ActivityRowKind.Agent }, row.detail)
        .joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.secondarySystemGroupedBackground, CardShape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(
            icon = if (row.kind == ActivityRowKind.Automation) {
                DesignR.drawable.ic_history
            } else {
                DesignR.drawable.ic_terminal
            },
            tint = if (row.state == AgentState.Working) row.state.tint else colors.secondaryLabel
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardTitle(headline, Modifier.weight(1f, fill = false))
                if (named) ActivityTags(row.project)
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LpmTheme.typography.subheadline,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StateLine(row, meta, now)
        }
    }
}

@Composable
private fun StateLine(row: ActivityRow, meta: String, now: Long) {
    val colors = LpmTheme.colors
    val caption = LpmTheme.typography.caption1
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(row.state)
        Spacer(Modifier.width(5.dp))
        AgentToneText(
            text = row.state.label,
            state = row.state,
            style = caption.copy(fontWeight = FontWeight.Medium, color = row.state.tint)
        )
        Box(Modifier.weight(1f)) {
            if (meta.isNotEmpty()) {
                Text(
                    text = "· $meta",
                    style = caption,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // The elapsed label keeps its width: a truncated duration reads as a
        // different number, a truncated agent name doesn't.
        Text(
            text = activityElapsedLabel(row, now),
            style = caption.copy(fontFeatureSettings = "tnum"),
            color = colors.tertiaryLabel,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** The state's dot, swept while working and pulsing while it needs you, as its word is. */
@Composable
private fun StateDot(state: AgentState) {
    val tone = when (state) {
        AgentState.Working -> Modifier.workingSweep(sweepProgress())

        AgentState.NeedsYou -> {
            val alpha = pulseAlpha()
            Modifier.graphicsLayer { this.alpha = alpha() }
        }

        else -> Modifier
    }
    Box(
        Modifier
            .size(6.dp)
            .then(tone)
            .background(state.tint, CircleShape)
    )
}

@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LpmTheme.typography.body.copy(
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** The rounded tile a card leads with. */
@Composable
internal fun IconTile(@DrawableRes icon: Int, tint: Color) {
    Box(
        Modifier
            .size(40.dp)
            .background(LpmTheme.colors.tertiarySystemGroupedBackground, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
