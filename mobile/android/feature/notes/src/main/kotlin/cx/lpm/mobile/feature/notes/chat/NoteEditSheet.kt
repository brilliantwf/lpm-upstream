package cx.lpm.mobile.feature.notes.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.notes.R

/** Edit a note's text: Cancel, the title, and Save once there's something to save. */
@Composable
internal fun NoteEditSheet(
    text: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable { mutableStateOf(text) }
    val sheet = rememberModalSheetState()
    ModalSheet(onDismissRequest = onDismiss, modifier = modifier, state = sheet, plain = true) {
        LpmTopBar(
            title = stringResource(R.string.notes_note_edit_title),
            modifier = Modifier.padding(top = 16.dp),
            windowInsets = WindowInsets(0),
            leading = {
                GlassCapsuleButton(
                    text = stringResource(R.string.notes_cancel),
                    onClick = { sheet.dismiss(onDismiss) }
                )
            },
            trailing = {
                GlassCapsuleButton(
                    text = stringResource(R.string.notes_save),
                    onClick = {
                        onSave(draft)
                        sheet.dismiss(onDismiss)
                    },
                    enabled = draft.trimmingWhitespace().isNotEmpty(),
                    textStyle = LpmTheme.typography.headline
                )
            }
        )
        PlainTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
