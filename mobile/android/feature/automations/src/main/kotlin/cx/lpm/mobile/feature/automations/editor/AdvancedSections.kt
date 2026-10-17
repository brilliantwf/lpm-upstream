package cx.lpm.mobile.feature.automations.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.automations.R

/** The two optional commands: whether there's work to do, and whether the work held. */
@Composable
fun AdvancedSections(draft: JobEditorDraft, onEdit: DraftEdit) {
    val code = LpmTheme.typography.callout.copy(fontFamily = FontFamily.Monospace)
    ListSection(
        header = stringResource(R.string.editor_check_header),
        headerStyle = ListHeaderStyle.Title,
        footer = stringResource(R.string.editor_check_footer)
    ) {
        TextFieldRow(
            value = draft.check,
            onValueChange = { v -> onEdit { it.copy(check = v) } },
            placeholder = "git fetch && git log HEAD..@{u} --oneline | grep .",
            textStyle = code,
            keyboardOptions = PlainKeyboard,
            maxLines = CODE_MAX_LINES
        )
    }
    // An action is handed off without waiting, so there's nothing to check after it.
    if (draft.runMode == RunMode.Action) return
    ListSection(
        header = stringResource(R.string.editor_verify_header),
        headerStyle = ListHeaderStyle.Title,
        footer = stringResource(R.string.editor_verify_footer)
    ) {
        TextFieldRow(
            value = draft.verify,
            onValueChange = { v -> onEdit { it.copy(verify = v) } },
            placeholder = "npm test",
            textStyle = code,
            keyboardOptions = PlainKeyboard,
            maxLines = CODE_MAX_LINES
        )
    }
}

/** "Delete automation", and whether the copies its runs made go with it. */
@Composable
fun DeleteSection(
    state: AutomationEditorUiState,
    deleteCopies: Boolean,
    onDeleteCopiesChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListSection(modifier) {
        if (state.offersDeleteCopies) {
            ToggleRow(
                title = stringResource(R.string.editor_delete_copies),
                checked = deleteCopies,
                onCheckedChange = onDeleteCopiesChange
            )
        }
        ListRow(onClick = onDelete, enabled = !state.saving) {
            Text(
                text = stringResource(R.string.editor_delete_job),
                style = LpmTheme.typography.body,
                color = if (state.saving) LpmTheme.colors.tertiaryLabel else LpmTheme.colors.red,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val CODE_MAX_LINES = 6
