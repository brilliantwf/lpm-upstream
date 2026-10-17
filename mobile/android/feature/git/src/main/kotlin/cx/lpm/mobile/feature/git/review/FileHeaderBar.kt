package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/**
 * A file's full-bleed bar (GitHub "Files changed" style): the commit checkmark, the
 * collapse chevron with the path (truncated at the head) and +N −M, the "viewed"
 * circle, and "…" with "Ask agent…" — also on a long press.
 */
@Composable
internal fun FileHeaderBar(
    row: ReviewRow.FileHeader,
    actions: ReviewActions,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    var menu by remember { mutableStateOf(false) }
    val path = row.path
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListDefaults.RowMinHeight)
                .background(colors.secondarySystemGroupedBackground)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { menu = true }) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleToggle(
                on = row.selected,
                label = stringResource(R.string.git_file_include),
                onColor = colors.tint,
                size = 24.dp,
                onToggle = { actions.toggleSelected(path) }
            )
            PathButton(
                row = row,
                onClick = { actions.toggleCollapsed(path) },
                onLongClick = { menu = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 5.dp, end = 10.dp)
            )
            CircleToggle(
                on = row.viewed,
                label = stringResource(R.string.git_file_viewed),
                onColor = colors.green,
                size = 22.dp,
                onToggle = { actions.toggleViewed(path) }
            )
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(28.dp)
                    .clickableNoIndication(onClick = { menu = true }),
                contentAlignment = Alignment.Center
            ) {
                // A menu's label in the secondary style of the accent.
                Icon(
                    painter = painterResource(DesignR.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.git_more),
                    tint = colors.tint.copy(alpha = SECONDARY_ALPHA),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Under the bar's content, which a row's minimum height leaves 2 dp short.
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 2.dp),
            thickness = Dp.Hairline,
            color = colors.separator
        )
        ContextMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ContextMenuItem(
                title = stringResource(R.string.git_file_ask_agent),
                onClick = { actions.ask(path) },
                icon = painterResource(DesignR.drawable.ic_auto_awesome)
            )
        }
    }
}

/** The chevron and path that collapse the diff, with the change counts once parsed. */
@Composable
private fun PathButton(
    row: ReviewRow.FileHeader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val state = stringResource(
        if (row.collapsed) R.string.git_file_collapsed else R.string.git_file_expanded
    )
    Row(
        modifier = modifier
            .combinedClickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .semantics { stateDescription = state },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(if (row.collapsed) CHEVRON_CLOSED else CHEVRON_OPEN),
            contentDescription = null,
            tint = colors.label.copy(alpha = SECONDARY_ALPHA),
            modifier = Modifier.size(22.dp)
        )
        if (row.file.isConflicted) {
            val conflict = stringResource(R.string.git_file_conflict)
            Text(
                text = "!",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.red,
                modifier = Modifier.semantics { contentDescription = conflict }
            )
        }
        Text(
            text = row.path,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.weight(1f)
        )
        if (row.added != null && row.removed != null) {
            DiffStat(row.added, row.removed, Modifier.padding(start = 6.dp))
        }
    }
}

/** The per-file "+N −M", each only when nonzero. */
@Composable
internal fun DiffStat(added: Int, removed: Int, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (added >
            0
        ) {
            Text(
                stringResource(R.string.git_review_added, added),
                style = style,
                color = colors.green
            )
        }
        if (removed >
            0
        ) {
            Text(
                stringResource(R.string.git_review_removed, removed),
                style = style,
                color = colors.red
            )
        }
    }
}

/** A checkmark circle that's filled in [onColor] while on, an empty gray ring while off. */
@Composable
private fun CircleToggle(
    on: Boolean,
    label: String,
    onColor: Color,
    size: Dp,
    onToggle: () -> Unit
) {
    val value = stringResource(if (on) R.string.git_file_on else R.string.git_file_off)
    Box(
        modifier = Modifier
            .size(size)
            .toggleable(
                value = on,
                interactionSource = null,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .semantics {
                contentDescription = label
                stateDescription = value
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                if (on) DesignR.drawable.ic_check_circle_fill else DesignR.drawable.ic_circle
            ),
            contentDescription = null,
            tint = if (on) onColor else LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(size)
        )
    }
}

private const val SECONDARY_ALPHA = 0.5f
private val CHEVRON_CLOSED = DesignR.drawable.ic_chevron_right
private val CHEVRON_OPEN = DesignR.drawable.ic_keyboard_arrow_down
