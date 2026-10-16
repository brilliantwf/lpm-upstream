package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.QueuedAction
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.projects.R

/**
 * A Start, Stop or Run tapped while the Mac was away, shown on its project: waiting
 * (with Cancel), or not sent after the wait ran out (with Try again).
 */
@Composable
fun QueuedActionLine(
    action: QueuedAction,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (action.expired) LpmTheme.colors.red else LpmTheme.colors.orange
    val style = LpmTheme.typography.caption1
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                if (action.expired) DesignR.drawable.ic_error else DesignR.drawable.ic_schedule
            ),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (action.expired) {
                stringResource(R.string.queued_expired, action.verb)
            } else {
                stringResource(R.string.queued_waiting, action.verb)
            },
            style = style,
            color = color,
            maxLines = 1
        )
        Text(stringResource(R.string.dot_separator), style = style, color = color)
        LinkButton(
            text = stringResource(
                if (action.expired) R.string.queued_retry else R.string.queued_cancel
            ),
            onClick = if (action.expired) onRetry else onCancel,
            color = color,
            textStyle = style.copy(fontWeight = FontWeight.SemiBold)
        )
        if (action.expired) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_close),
                contentDescription = stringResource(R.string.queued_dismiss),
                tint = color,
                modifier = Modifier
                    .size(14.dp)
                    .clickableNoIndication(onCancel)
            )
        }
    }
}
