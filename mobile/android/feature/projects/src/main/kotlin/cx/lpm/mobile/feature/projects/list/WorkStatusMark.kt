package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.WorkState
import cx.lpm.mobile.core.model.WorkStatus
import cx.lpm.mobile.feature.projects.R

/**
 * What a row shows before its name for the status a person gave it: the state's
 * emoji, or its name when a custom status lost its emoji, so it's never blank.
 */
@Composable
fun WorkStatusMark(status: WorkStatus, modifier: Modifier = Modifier) {
    if (status.displayEmoji.isEmpty()) {
        Text(
            text = status.displayLabel,
            style = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
            color = LpmTheme.colors.secondaryLabel,
            modifier = modifier
        )
    } else {
        Text(
            text = status.displayEmoji,
            fontSize = 14.sp,
            modifier = modifier.semantics { contentDescription = status.displayLabel }
        )
    }
}

/**
 * The line under a row whose status carries a note: the status's word, then the
 * note. Only Blocked's word takes color.
 */
@Composable
fun WorkStatusNoteLine(status: WorkStatus, note: String, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val style = LpmTheme.typography.caption1
        Text(
            text = status.displayLabel,
            style = style,
            color = if (status.state == WorkState.Blocked) colors.red else colors.secondaryLabel,
            maxLines = 1
        )
        Text(stringResource(R.string.dot_separator), style = style, color = colors.secondaryLabel)
        Text(
            text = note,
            style = style,
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
