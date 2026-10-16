package cx.lpm.mobile.feature.memory.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.PlainListDefaults
import cx.lpm.mobile.core.designsystem.component.SwipeAction
import cx.lpm.mobile.core.designsystem.component.SwipeActionsBox
import cx.lpm.mobile.core.designsystem.component.plainListSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.memory.R

/**
 * One session: title and stamp on a line, the goal under them; Delete swiped from the
 * right. The [first] row carries the list's top separator.
 */
@Composable
internal fun MemorySessionRow(
    row: MemoryRowUi,
    first: Boolean,
    onOpen: () -> Unit,
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
                stringResource(R.string.delete),
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
                    vertical = PlainListDefaults.RowInset + 3.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(
                        text = row.title,
                        style = LpmTheme.typography.subheadline.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline()
                    )
                    Text(
                        text = row.stamp,
                        style = LpmTheme.typography.caption1,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .alignByBaseline()
                    )
                }
                if (row.subtitle.isNotEmpty()) {
                    Text(
                        text = row.subtitle,
                        style = LpmTheme.typography.caption1,
                        color = colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            DisclosureChevron()
        }
    }
}
