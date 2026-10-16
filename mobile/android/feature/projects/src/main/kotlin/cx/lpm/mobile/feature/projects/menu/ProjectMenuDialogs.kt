package cx.lpm.mobile.feature.projects.menu

import androidx.compose.runtime.Composable
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
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.WorkStatusChoice
import cx.lpm.mobile.core.model.WorkStatusInput
import cx.lpm.mobile.feature.projects.R

/** The alert or sheet the project menu has open. */
sealed interface MenuDialog {
    data object Rename : MenuDialog

    data object Discard : MenuDialog

    data object TerminalSettings : MenuDialog

    data class StatusNote(val choice: WorkStatusChoice) : MenuDialog
}

/**
 * The project menu's presentations: Rename (seeded with the current label), the
 * discard confirmation, a status's note prompt, Terminal Settings, and a failed Git
 * op's alert.
 */
@Composable
fun ProjectMenuDialogs(
    dialog: MenuDialog?,
    state: ProjectMenuUiState,
    project: Project,
    onRename: (String) -> Unit,
    onDiscard: () -> Unit,
    onStatus: (WorkStatusInput) -> Unit,
    onDismissOpError: () -> Unit,
    terminalSettings: @Composable (onDismiss: () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(dialog) { mutableStateOf(project.label) }
    val cancel = AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
    when (dialog) {
        null -> Unit

        MenuDialog.Rename -> LpmAlert(
            title = stringResource(R.string.rename_project_title),
            message = stringResource(R.string.rename_project_message),
            textField = {
                AlertTextField(name, { name = it }, stringResource(R.string.rename_project_field))
            },
            buttons = listOf(cancel, AlertButton(stringResource(R.string.save)) { onRename(name) }),
            onDismissRequest = onDismiss
        )

        MenuDialog.Discard -> LpmConfirmationDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            buttons = listOf(
                AlertButton(
                    stringResource(R.string.discard_confirm),
                    AlertRole.Destructive,
                    onClick = onDiscard
                )
            ),
            onDismissRequest = onDismiss
        )

        MenuDialog.TerminalSettings -> terminalSettings(onDismiss)

        is MenuDialog.StatusNote -> WorkStatusNotePrompt(
            project = project,
            choice = dialog.choice,
            onSave = onStatus,
            onDismiss = onDismiss
        )
    }
    val error = state.opError
    if (dialog == null && error != null) {
        LpmAlert(
            title = error.title,
            message = error.message,
            buttons = listOf(AlertButton(stringResource(R.string.ok), AlertRole.Cancel)),
            onDismissRequest = onDismissOpError
        )
    }
}
