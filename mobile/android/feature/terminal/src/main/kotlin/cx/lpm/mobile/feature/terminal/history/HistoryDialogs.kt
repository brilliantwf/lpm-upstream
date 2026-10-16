package cx.lpm.mobile.feature.terminal.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.AlertTextField
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.HistoryFolder
import cx.lpm.mobile.core.model.HistoryItem
import cx.lpm.mobile.feature.terminal.R

/** A centered placeholder: icon, title and a line under it. */
@Composable
internal fun EmptyMessage(
    title: String,
    message: String,
    icon: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painterResource(icon),
            null,
            tint = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(48.dp)
        )
        Text(
            title,
            style = LpmTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            message,
            style = LpmTheme.typography.subheadline,
            color = LpmTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

/** "New folder": a name, then Create or Cancel. */
@Composable
internal fun NewFolderAlert(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    LpmAlert(
        title = stringResource(R.string.history_new_folder),
        textField = {
            AlertTextField(name, {
                name = it
            }, placeholder = stringResource(R.string.history_folder_name))
        },
        buttons = listOf(
            AlertButton(stringResource(R.string.history_create)) { onCreate(name) },
            AlertButton(stringResource(R.string.history_cancel), AlertRole.Cancel)
        ),
        onDismissRequest = onDismiss
    )
}

/** "Move to folder": each folder, out of its folder, a new one, or Cancel. */
@Composable
internal fun MoveToFolderDialog(
    item: HistoryItem,
    folders: List<HistoryFolder>,
    onMove: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val buttons = folders.map { folder -> AlertButton(folder.name) { onMove(folder.id) } } +
        listOfNotNull(
            if (item.folder !=
                null
            ) {
                AlertButton(stringResource(R.string.history_remove_from_folder)) {
                    onMove(null)
                }
            } else {
                null
            },
            AlertButton(
                stringResource(R.string.history_new_folder_ellipsis),
                onClick = onNewFolder
            ),
            AlertButton(stringResource(R.string.history_cancel), AlertRole.Cancel)
        )
    LpmConfirmationDialog(
        title = stringResource(R.string.history_move_to_folder),
        buttons = buttons,
        onDismissRequest = onDismiss
    )
}
