package cx.lpm.mobile.feature.projects.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.LpmSwitch
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * One configured service: its name and port, with a switch that starts or stops it
 * (a spinner while a toggle is in flight). Tapping the name side opens its logs; that
 * target stops short of the switch.
 */
@Composable
fun ServiceRow(
    entry: ServiceEntry,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val service = entry.service
    ListRow(
        modifier = modifier,
        trailing = {
            if (entry.pending) {
                Box(Modifier.width(SwitchWidth), contentAlignment = Alignment.Center) {
                    ActivityIndicator(size = ActivityIndicatorSize.Small)
                }
            } else {
                LpmSwitch(
                    modifier = Modifier.offset(x = 2.dp),
                    checked = entry.running,
                    onCheckedChange = { onToggle() },
                    contentDescription = service.name
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickableNoIndication(onOpen),
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
        Spacer(Modifier.width(8.dp))
    }
}

private val SwitchWidth = 63.dp
