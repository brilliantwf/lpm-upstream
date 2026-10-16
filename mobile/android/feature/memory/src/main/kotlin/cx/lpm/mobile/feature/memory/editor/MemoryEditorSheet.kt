package cx.lpm.mobile.feature.memory.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.navigation.MemorySessionKey
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.feature.memory.R

/**
 * Edit a session memory's markdown (iOS `MemoryEditorSheet`): a plain monospaced
 * field with no autocorrect, which would quietly rewrite the markdown as the user
 * types. A dirty draft asks before it's thrown away.
 */
@Composable
fun MemoryEditorSheet(
    key: MemorySessionKey,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { MemoryEditorContent(key, title, onDismiss, modifier) }
}

@Composable
private fun MemoryEditorContent(
    key: MemorySessionKey,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryEditorViewModel =
        hiltViewModel<MemoryEditorViewModel, MemoryEditorViewModel.Factory>(
            creationCallback = { it.create(key) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmingDiscard by remember { mutableStateOf(false) }
    // System back slides even an undismissable sheet away; a fresh one brings it back.
    var presentation by remember { mutableIntStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)
    key(presentation) {
        val sheet = rememberModalSheetState()
        LaunchedEffect(state.saved) { if (state.saved) sheet.dismiss(dismiss) }
        ModalSheet(
            onDismissRequest = {
                if (state.dirty || state.saving) {
                    presentation++
                    if (!state.saving) confirmingDiscard = true
                } else {
                    onDismiss()
                }
            },
            modifier = modifier,
            state = sheet,
            dismissible = !state.saving && !state.dirty,
            plain = true
        ) {
            EditorBar(
                title = title,
                state = state,
                onCancel = {
                    if (state.dirty) confirmingDiscard = true else sheet.dismiss(onDismiss)
                },
                onSave = viewModel::save
            )
            MarkdownField(state.draft, viewModel::edit)
        }
        if (confirmingDiscard) {
            LpmConfirmationDialog(
                title = stringResource(R.string.memory_editor_discard_title),
                buttons = listOf(
                    AlertButton(
                        stringResource(R.string.memory_editor_discard),
                        AlertRole.Destructive
                    ) { sheet.dismiss(onDismiss) },
                    AlertButton(
                        stringResource(R.string.memory_editor_keep_editing),
                        AlertRole.Cancel
                    )
                ),
                onDismissRequest = { confirmingDiscard = false }
            )
        }
    }
    EditorAlerts(state, viewModel::dismissConflict, viewModel::dismissSaveError)
}

@Composable
private fun EditorBar(
    title: String,
    state: MemoryEditorUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    LpmTopBar(
        title = title,
        modifier = Modifier.padding(top = 16.dp),
        windowInsets = WindowInsets(0),
        leading = {
            GlassCapsuleButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                enabled = !state.saving
            )
        },
        trailing = {
            if (state.saving) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    ActivityIndicator(size = ActivityIndicatorSize.Small)
                }
            } else {
                GlassCapsuleButton(
                    text = stringResource(R.string.memory_editor_save),
                    onClick = onSave,
                    enabled = state.dirty,
                    textStyle = LpmTheme.typography.headline
                )
            }
        }
    )
}

@Composable
private fun MarkdownField(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = LpmTheme.typography.body.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 15.5.sp,
            letterSpacing = 0.sp,
            color = colors.label
        ),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false
        ),
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 8.dp)
    )
}

/** The save the Mac turned down, or the newer version it loaded instead. */
@Composable
private fun EditorAlerts(
    state: MemoryEditorUiState,
    onDismissConflict: () -> Unit,
    onDismissSaveError: () -> Unit
) {
    val ok = stringResource(R.string.ok)
    state.conflictNotice?.let { notice ->
        LpmAlert(
            title = stringResource(R.string.memory_editor_conflict_title),
            message = notice,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = onDismissConflict
        )
    }
    state.saveError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.memory_editor_save_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = onDismissSaveError
        )
    }
}
