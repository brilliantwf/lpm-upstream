package cx.lpm.mobile.feature.projects.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.AlertTextField
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.model.DuplicateOptions
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.ProjectFolder
import cx.lpm.mobile.core.model.WorkStatusInput
import cx.lpm.mobile.feature.projects.R
import cx.lpm.mobile.feature.projects.duplicate.DuplicateOptionsSheet
import cx.lpm.mobile.feature.projects.menu.WorkStatusNotePrompt

/** What the projects list's alerts and sheets do once confirmed. */
@Immutable
class ProjectsDialogActions(
    val createFolder: (name: String) -> Unit = {},
    val moveToFolder: (Project, folder: String?) -> Unit = { _, _ -> },
    val renameFolder: (ProjectFolder, name: String) -> Unit = { _, _ -> },
    val deleteFolder: (ProjectFolder) -> Unit = {},
    val remove: (Project) -> Unit = {},
    val duplicate: (Project, DuplicateOptions) -> Unit = { _, _ -> },
    val setWorkStatus: (project: String, WorkStatusInput) -> Unit = { _, _ -> }
)

/**
 * The open [dialog]: the folder alerts (new, new-and-move, rename), the delete and
 * remove confirmations (the latter with the work only the copy holds, from [removal]),
 * the duplicate options sheet and a status's note prompt.
 */
@Composable
fun ProjectsDialogHost(
    dialog: ProjectsDialog?,
    duplicateDefaults: DuplicateOptions,
    removal: CopyRemovalCheck?,
    actions: ProjectsDialogActions,
    onDismiss: () -> Unit
) {
    var name by remember(dialog) {
        mutableStateOf((dialog as? ProjectsDialog.RenameFolder)?.folder?.name.orEmpty())
    }
    val cancel = AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
    val placeholder = stringResource(R.string.folder_name)
    val field: @Composable () -> Unit = { AlertTextField(name, { name = it }, placeholder) }
    when (dialog) {
        null -> Unit

        ProjectsDialog.NewFolder -> LpmAlert(
            title = stringResource(R.string.folder_new_title),
            textField = field,
            buttons = listOf(
                cancel,
                AlertButton(stringResource(R.string.folder_create)) { actions.createFolder(name) }
            ),
            onDismissRequest = onDismiss
        )

        is ProjectsDialog.NewFolderFor -> LpmAlert(
            title = stringResource(R.string.folder_new_title),
            message = stringResource(R.string.folder_move_message, dialog.project.label),
            textField = field,
            buttons = listOf(
                cancel,
                AlertButton(stringResource(R.string.folder_create_move)) {
                    actions.moveToFolder(dialog.project, name)
                }
            ),
            onDismissRequest = onDismiss
        )

        is ProjectsDialog.RenameFolder -> LpmAlert(
            title = stringResource(R.string.folder_rename_title),
            textField = field,
            buttons = listOf(
                cancel,
                AlertButton(stringResource(R.string.save)) {
                    actions.renameFolder(dialog.folder, name)
                }
            ),
            onDismissRequest = onDismiss
        )

        is ProjectsDialog.DeleteFolder -> LpmConfirmationDialog(
            title = stringResource(R.string.folder_delete_title),
            message = stringResource(R.string.folder_delete_message, dialog.folder.name),
            buttons = listOf(
                AlertButton(stringResource(R.string.folder_delete), AlertRole.Destructive) {
                    actions.deleteFolder(dialog.folder)
                }
            ),
            onDismissRequest = onDismiss
        )

        is ProjectsDialog.RemoveDuplicate -> LpmConfirmationDialog(
            title = stringResource(R.string.remove_title),
            message = copyRemovalMessage(dialog.project, removal),
            buttons = listOf(
                AlertButton(stringResource(R.string.row_remove), AlertRole.Destructive) {
                    actions.remove(dialog.project)
                }
            ),
            onDismissRequest = onDismiss
        )

        is ProjectsDialog.Duplicate -> DuplicateOptionsSheet(
            project = dialog.project,
            defaults = duplicateDefaults,
            onConfirm = { actions.duplicate(dialog.project, it) },
            onDismiss = onDismiss
        )

        is ProjectsDialog.WorkStatusNote -> WorkStatusNotePrompt(
            project = dialog.project,
            choice = dialog.choice,
            onSave = { actions.setWorkStatus(dialog.project.name, it) },
            onDismiss = onDismiss
        )
    }
}
