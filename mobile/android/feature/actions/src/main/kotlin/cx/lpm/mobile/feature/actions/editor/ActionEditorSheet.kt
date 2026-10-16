package cx.lpm.mobile.feature.actions.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.feature.actions.R

/**
 * Create or edit one of a project's actions ([editingKey] null creates one): the ⚡
 * sheet's "+" and "Edit Action…" open it, as can the config editor. On save the Mac
 * writes it to the same layer file a desktop edit would, and the sheet goes once it has.
 */
@Composable
fun ActionEditorSheet(
    project: String,
    editingKey: String?,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { ActionEditorContent(project, editingKey, session, onDismiss, modifier) }
}

@Composable
private fun ActionEditorContent(
    project: String,
    editingKey: String?,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActionEditorViewModel =
        hiltViewModel<ActionEditorViewModel, ActionEditorViewModel.Factory>(
            creationCallback = { it.create(project, editingKey, session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalSheetState()
    val haptics = LocalHapticFeedback.current
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.done) {
        if (state.done) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            sheet.dismiss(dismiss)
        }
    }
    ModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        state = sheet,
        dismissible = !state.saving
    ) {
        LpmTopBar(
            title = stringResource(
                if (state.editing) R.string.actions_editor_edit else R.string.actions_editor_add
            ),
            modifier = Modifier.padding(top = 16.dp),
            windowInsets = WindowInsets(0),
            leading = {
                GlassCapsuleButton(
                    text = stringResource(R.string.cancel),
                    onClick = { sheet.dismiss(onDismiss) }
                )
            },
            trailing = {
                if (state.saving) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        ActivityIndicator(size = ActivityIndicatorSize.Small)
                    }
                } else {
                    GlassCapsuleButton(
                        text = stringResource(R.string.save),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.save()
                        },
                        enabled = state.canSave,
                        textStyle = LpmTheme.typography.headline
                    )
                }
            }
        )
        if (state.loading) {
            EditorLoading()
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = HeaderlessTopPadding)
            ) {
                ActionForm(
                    state = state,
                    onEdit = viewModel::edit,
                    onDelete = {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        confirmDelete = true
                    }
                )
            }
        }
    }
    EditorAlerts(
        state = state,
        onDismissMutationError = { viewModel.dismissMutationError() },
        onDismissBodyError = { viewModel.dismissBodyError() },
        onDismiss = onDismiss
    )
    if (confirmDelete) {
        LpmConfirmationDialog(
            title = stringResource(R.string.actions_editor_delete_title),
            message = stringResource(R.string.actions_editor_delete_message),
            buttons = listOf(
                AlertButton(
                    stringResource(R.string.delete),
                    AlertRole.Destructive,
                    onClick = viewModel::delete
                ),
                AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
            ),
            onDismissRequest = { confirmDelete = false }
        )
    }
}

/** The first section has no header; iOS keeps an empty header's room above it. */
private val HeaderlessTopPadding = ListDefaults.ContentTopPadding + 18.dp

@Composable
private fun EditorLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ActivityIndicator()
            Text(
                text = stringResource(R.string.actions_editor_loading),
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** A write the Mac refused, or an action it couldn't read (which closes the sheet). */
@Composable
private fun EditorAlerts(
    state: ActionEditorUiState,
    onDismissMutationError: () -> Unit,
    onDismissBodyError: () -> Unit,
    onDismiss: () -> Unit
) {
    val ok = stringResource(R.string.ok)
    state.mutationError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.actions_editor_save_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = onDismissMutationError
        )
    }
    state.bodyError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.actions_editor_load_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = {
                onDismissBodyError()
                onDismiss()
            }
        )
    }
}
