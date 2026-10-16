package cx.lpm.mobile.feature.projects.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.AlertTextField
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.WorkState
import cx.lpm.mobile.core.model.WorkStatusChoice
import cx.lpm.mobile.core.model.WorkStatusInput
import cx.lpm.mobile.core.model.existingNote
import cx.lpm.mobile.feature.projects.R

/**
 * The line a status asks for when applied (Blocked, or a custom status flagged for
 * it), seeded with the note already on the row when the choice keeps the same
 * status, so re-applying it edits the line rather than starting from blank.
 */
@Composable
fun WorkStatusNotePrompt(
    project: Project,
    choice: WorkStatusChoice,
    onSave: (WorkStatusInput) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var note by rememberSaveable(choice.id) {
        mutableStateOf(existingNote(project.workStatus, choice))
    }
    val blocked = choice.input.state == WorkState.Blocked
    LpmAlert(
        title = choice.label,
        message = stringResource(
            if (blocked) R.string.status_note_blocked_message else R.string.status_note_message,
            project.label
        ),
        textField = {
            AlertTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(
                    if (blocked) {
                        R.string.status_note_blocked_placeholder
                    } else {
                        R.string.status_note_placeholder
                    }
                )
            )
        },
        buttons = listOf(
            AlertButton(stringResource(R.string.cancel), AlertRole.Cancel),
            AlertButton(stringResource(R.string.save)) { onSave(choice.input.copy(note = note)) }
        ),
        onDismissRequest = onDismiss,
        modifier = modifier
    )
}
