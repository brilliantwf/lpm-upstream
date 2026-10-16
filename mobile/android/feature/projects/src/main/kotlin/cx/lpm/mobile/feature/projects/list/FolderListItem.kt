package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.feature.projects.R as ProjectsR

/** A folder's header in the card, with Rename and Delete on a long press. */
@Composable
fun FolderListItem(
    entry: ProjectsListRow.FolderEntry,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.listSeparator(startInset = 36.dp)) {
        FolderHeader(
            name = entry.folder.name,
            count = entry.count,
            expanded = entry.expanded,
            onToggle = onToggle,
            onLongPress = { menuOpen = true }
        )
        ContextMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ContextMenuItem(
                title = stringResource(ProjectsR.string.folder_rename),
                onClick = onRename,
                icon = painterResource(R.drawable.ic_edit)
            )
            ContextMenuItem(
                title = stringResource(ProjectsR.string.folder_delete),
                onClick = onDelete,
                icon = painterResource(R.drawable.ic_delete),
                destructive = true
            )
        }
    }
}
