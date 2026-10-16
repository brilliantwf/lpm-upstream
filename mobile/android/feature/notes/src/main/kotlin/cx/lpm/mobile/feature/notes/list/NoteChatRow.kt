package cx.lpm.mobile.feature.notes.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.PlainListDefaults
import cx.lpm.mobile.core.designsystem.component.SwipeAction
import cx.lpm.mobile.core.designsystem.component.SwipeActionsBox
import cx.lpm.mobile.core.designsystem.component.plainListSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R

/**
 * One chat: its title and last activity; Rename and Delete swiped from the right. The
 * [first] row carries the list's top separator.
 */
@Composable
internal fun NoteChatRow(
    row: NoteChatRowUi,
    first: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    SwipeActionsBox(
        modifier = modifier.plainListSeparator(colors.listSeparator, top = first),
        trailing = listOf(
            SwipeAction(
                stringResource(R.string.notes_rename),
                DesignR.drawable.ic_edit,
                colors.indigo,
                onRename
            ),
            SwipeAction(
                stringResource(R.string.notes_delete),
                DesignR.drawable.ic_delete,
                colors.red,
                onDelete
            )
        ),
        containerColor = colors.systemBackground
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interaction, indication = null, role = Role.Button, onClick = onOpen)
                .background(if (pressed) colors.gray4 else Color.Transparent)
                .padding(
                    horizontal = PlainListDefaults.RowPadding,
                    vertical = PlainListDefaults.RowInset + 4.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = row.title.ifEmpty { stringResource(R.string.notes_untitled) },
                    style = LpmTheme.typography.callout.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.time,
                    style = LpmTheme.typography.caption1,
                    color = colors.secondaryLabel
                )
            }
            if (row.busy) ActivityIndicator(size = ActivityIndicatorSize.Small)
        }
    }
}
