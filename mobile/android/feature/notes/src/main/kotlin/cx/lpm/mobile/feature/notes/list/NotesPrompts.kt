package cx.lpm.mobile.feature.notes.list

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
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.feature.notes.R

/** Which of the list's prompts is up. */
sealed interface NotesPrompt {
    data object Create : NotesPrompt

    data class Rename(val chat: NoteChat) : NotesPrompt

    data class Delete(val chat: NoteChat) : NotesPrompt
}

/** The list's three prompts: name a new chat, rename one, confirm a delete. */
@Composable
internal fun NotesPrompts(
    prompt: NotesPrompt,
    onCreate: (String) -> Unit,
    onRename: (NoteChat, String) -> Unit,
    onDelete: (NoteChat) -> Unit,
    onDismiss: () -> Unit
) {
    val cancel = AlertButton(stringResource(R.string.notes_cancel), AlertRole.Cancel)
    var title by remember(prompt) {
        mutableStateOf((prompt as? NotesPrompt.Rename)?.chat?.title.orEmpty())
    }
    val field = stringResource(R.string.notes_chat_title_field)
    when (prompt) {
        NotesPrompt.Create -> LpmAlert(
            title = stringResource(R.string.notes_chat_new_title),
            textField = { AlertTextField(title, { title = it }, field) },
            buttons = listOf(
                cancel,
                AlertButton(stringResource(R.string.notes_chat_create)) {
                    onCreate(title)
                }
            ),
            onDismissRequest = onDismiss
        )

        is NotesPrompt.Rename -> LpmAlert(
            title = stringResource(R.string.notes_chat_rename_title),
            textField = { AlertTextField(title, { title = it }, field) },
            buttons = listOf(
                cancel,
                AlertButton(stringResource(R.string.notes_save)) { onRename(prompt.chat, title) }
            ),
            onDismissRequest = onDismiss
        )

        is NotesPrompt.Delete -> LpmConfirmationDialog(
            title = stringResource(R.string.notes_chat_delete_title),
            message = stringResource(R.string.notes_chat_delete_message, prompt.chat.title),
            buttons = listOf(
                AlertButton(stringResource(R.string.notes_delete), AlertRole.Destructive) {
                    onDelete(prompt.chat)
                },
                cancel
            ),
            onDismissRequest = onDismiss
        )
    }
}
