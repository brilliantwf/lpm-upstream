package cx.lpm.mobile.feature.projects.detail

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.LpmSwitch
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.crashed
import cx.lpm.mobile.feature.projects.R

/** What a service row's controls do. */
class ServiceRowActions(
    val toggle: () -> Unit = {},
    val restart: () -> Unit = {},
    val stop: () -> Unit = {},
    val open: () -> Unit = {}
)

/**
 * One configured service: its name and port, with a switch that starts or stops it
 * (a spinner while a toggle is in flight). One whose command ended says how, in red
 * when it failed, and offers Restart in place of the switch. Tapping the name side
 * opens its logs (that target stops short of the switch); a long press offers Restart
 * and Stop where the Mac takes them ([canStop]).
 */
@Composable
fun ServiceRow(
    entry: ServiceEntry,
    canStop: Boolean,
    actions: ServiceRowActions,
    modifier: Modifier = Modifier
) {
    val service = entry.service
    val ended = entry.ended
    val hasMenu = ended != null || (entry.running && canStop)
    var menuOpen by remember { mutableStateOf(false) }
    // iOS's compact row: an exit line under the name fits the row's usual height.
    ListRow(
        modifier = modifier,
        verticalPadding = 4.dp,
        trailing = {
            when {
                entry.pending -> Box(
                    Modifier.width(SwitchWidth),
                    contentAlignment = Alignment.Center
                ) {
                    ActivityIndicator(size = ActivityIndicatorSize.Small)
                }

                ended != null -> RestartButton(service.name, actions.restart)

                else -> LpmSwitch(
                    modifier = Modifier.offset(x = 2.dp),
                    checked = entry.running,
                    onCheckedChange = { actions.toggle() },
                    contentDescription = service.name
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = if (hasMenu) ({ menuOpen = true }) else null,
                    onClick = actions.open
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            NameLine(service)
            if (ended != null) ExitLine(ended)
        }
        Spacer(Modifier.width(8.dp))
        Box {
            ContextMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ContextMenuItem(
                    title = stringResource(R.string.service_row_restart),
                    onClick = actions.restart,
                    icon = painterResource(DesignR.drawable.ic_refresh)
                )
                if (canStop) {
                    ContextMenuItem(
                        title = stringResource(R.string.service_row_stop),
                        onClick = actions.stop,
                        icon = painterResource(DesignR.drawable.ic_stop_fill),
                        destructive = true
                    )
                }
            }
        }
    }
}

@Composable
private fun NameLine(service: Service) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = service.name,
            style = LpmTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (service.port > 0) {
            Text(
                text = ":${service.port}",
                style = LpmTheme.typography.body.copy(fontFeatureSettings = "tnum"),
                color = LpmTheme.colors.secondaryLabel,
                maxLines = 1
            )
        }
    }
}

/** "exited with code 1" beside a dot, red when the service failed. */
@Composable
private fun ExitLine(ended: Service) {
    val color = if (ended.crashed) LpmTheme.colors.red else LpmTheme.colors.secondaryLabel
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = color, size = 6.dp)
        Text(
            text = serviceExitText(ended),
            style = LpmTheme.typography.caption1,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun RestartButton(name: String, onRestart: () -> Unit) {
    val label = stringResource(R.string.service_row_restart_named, name)
    LinkButton(
        text = stringResource(R.string.service_row_restart),
        onClick = onRestart,
        icon = painterResource(DesignR.drawable.ic_refresh),
        iconSize = 17.dp,
        textStyle = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick {
                onRestart()
                true
            }
        }
    )
}

private val SwitchWidth = 63.dp
