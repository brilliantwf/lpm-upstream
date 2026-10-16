package cx.lpm.mobile.feature.usage.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ClaudeInUse
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.feature.usage.R
import cx.lpm.mobile.feature.usage.ui.groupedCard
import cx.lpm.mobile.feature.usage.usage.UsageProviderCard

/**
 * What an expanded usage row shows: the card the desktop opens on hover, and on a
 * project's screen a button that switches the project to the row's account — the
 * desktop's "Switch to …" on a right-click.
 */
@Composable
internal fun UsageRowDetail(
    row: UsageRow,
    inUse: ClaudeInUse?,
    switchTarget: String?,
    now: Double,
    macNow: Double,
    onSwitch: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val footer = listOfNotNull(row.account?.let { inUse?.note(it) }, row.spentLine)
    val fill = LpmTheme.colors.tertiarySystemGroupedBackground
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val project = inUse?.project
        if (switchTarget != null && onSwitch != null && project != null) {
            SwitchButton(row, switchTarget, inUse, project, onSwitch)
        }
        val data = row.data
        if (data != null) {
            UsageProviderCard(
                data = data,
                now = now,
                macNow = macNow,
                title = row.label,
                subtitle = row.subtitle,
                footer = footer,
                fill = fill,
                fiveHourFirst = row.windowShort == "5h"
            )
        } else {
            Waiting(row, footer)
        }
    }
}

@Composable
private fun SwitchButton(
    row: UsageRow,
    account: String,
    inUse: ClaudeInUse,
    project: Project,
    onSwitch: (String) -> Unit
) {
    val colors = LpmTheme.colors
    val current = inUse.current == account
    val pinned = current && inUse.pinned
    val tint = if (current) colors.secondaryLabel else colors.tint
    val content = if (pinned) colors.tertiaryLabel else tint
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val title = when {
        pinned -> stringResource(R.string.rows_using, row.label)
        current -> stringResource(R.string.rows_always_use, row.label)
        else -> stringResource(R.string.rows_switch_to, row.label)
    }
    val subtitle = when {
        pinned -> stringResource(R.string.rows_new_sessions_always, project.label)
        current -> stringResource(R.string.rows_new_sessions_now, project.label)
        else -> stringResource(R.string.rows_new_sessions_in, project.label)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SwitchShape)
            .background(if (pinned) colors.tertiarySystemFill else tint.copy(alpha = 0.15f))
            .clickable(interaction, indication = null, enabled = !pinned, role = Role.Button) {
                onSwitch(account)
            }
            .alpha(if (pressed) 0.6f else 1f)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                if (current) {
                    DesignR.drawable.ic_check_circle_fill
                } else {
                    DesignR.drawable.ic_swap_horizontal_circle
                }
            ),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = content
            )
            Text(
                subtitle,
                style = LpmTheme.typography.caption1,
                color = content,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

/**
 * A row with nothing but the day's spend, or an account that hasn't run yet, has no
 * windows to draw — the desktop's plain tooltip.
 */
@Composable
private fun Waiting(row: UsageRow, footer: List<String>) {
    val colors = LpmTheme.colors
    val caption = LpmTheme.typography.caption1
    Column(
        Modifier
            .fillMaxWidth()
            .groupedCard(color = colors.tertiarySystemGroupedBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            row.label,
            style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold)
        )
        row.subtitle?.let { Text(it, style = caption, color = colors.secondaryLabel) }
        footer.forEach { line ->
            Text(
                line,
                style = caption.copy(fontFeatureSettings = "tnum"),
                color = colors.secondaryLabel
            )
        }
        Text(
            stringResource(R.string.rows_no_window),
            style = caption,
            color = colors.secondaryLabel
        )
    }
}

private val SwitchShape = RoundedCornerShape(14.dp)
