package cx.lpm.mobile.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * One project's running services: the profile / service chips that start and stop
 * them, the ports they declare, and a stop-everything control. [pending] holds the
 * services with a toggle in flight, spun rather than re-tappable.
 */
@Composable
fun ActivityServiceCard(
    group: ActivityServiceGroup,
    pending: Set<String>,
    stopping: Boolean,
    onChip: (ActivityChip) -> Unit,
    onStop: () -> Unit,
    onOpenProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    // Under the chips rather than beside the name: a port belongs to a service, so
    // it says more paired with one than as a bare number.
    val ports = group.running.mapNotNull { service ->
        group.ports[service]?.takeIf { it > 0 }?.let { "$service :$it" }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.secondarySystemGroupedBackground, CardShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(DesignR.drawable.ic_dns, colors.green)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onOpenProject
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardTitle(group.project.label, Modifier.weight(1f, fill = false))
                ActivityTags(group.project)
            }
            ChipRow(group.chips, pending, onChip)
            if (ports.isNotEmpty()) {
                Text(
                    text = ports.joinToString("  "),
                    style = LpmTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
                    color = colors.tertiaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        StopButton(group.project.label, stopping, onStop)
    }
}

@Composable
private fun StopButton(label: String, stopping: Boolean, onStop: () -> Unit) {
    val colors = LpmTheme.colors
    if (stopping) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            ActivityIndicator(size = ActivityIndicatorSize.Small)
        }
        return
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(colors.tertiarySystemFill, CircleShape)
            .clickable(role = Role.Button, onClick = onStop),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_stop_fill),
            contentDescription = stringResource(R.string.activity_stop_everything, label),
            tint = colors.red,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * The start/stop chips under a service card. They scroll sideways rather than
 * wrapping, so a project with many services never grows the card unpredictably.
 */
@Composable
private fun ChipRow(
    chips: List<ActivityChip>,
    pending: Set<String>,
    onChip: (ActivityChip) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { chip ->
            ChipButton(
                chip = chip,
                pending = chip.kind == ActivityChip.Kind.Service && chip.name in pending,
                onClick = { onChip(chip) }
            )
        }
    }
}

@Composable
private fun ChipButton(chip: ActivityChip, pending: Boolean, onClick: () -> Unit) {
    val colors = LpmTheme.colors
    val label = when {
        chip.crashed && !chip.running -> stringResource(R.string.activity_crashed_chip, chip.name)
        chip.running -> stringResource(R.string.activity_stop_chip, chip.name, chip.noun)
        else -> stringResource(R.string.activity_start_chip, chip.name, chip.noun)
    }
    val tint = when {
        chip.running -> colors.green
        chip.crashed -> colors.red
        else -> null
    }
    Row(
        modifier = Modifier
            .background(colors.tertiarySystemFill, CircleShape)
            .clickable(enabled = !pending, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pending) {
            ActivityIndicator(size = ActivityIndicatorSize.Mini)
        } else {
            StatusDot(color = tint ?: colors.gray3, size = 5.dp)
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = chip.name,
            style = LpmTheme.typography.caption1.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp
            ),
            color = tint ?: colors.secondaryLabel,
            maxLines = 1
        )
    }
}
