package cx.lpm.mobile.feature.automations.editor

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
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.feature.automations.R
import cx.lpm.mobile.feature.automations.ui.HeaderlessTopPadding

/**
 * Create or edit a scheduled automation (iOS `AutomationEditorSheet`, the desktop's
 * JobEditorModal). On save the Mac writes the body to the same layer file a desktop
 * edit would, and the sheet goes once it has.
 */
@Composable
fun AutomationEditorSheet(
    target: AutomationEditorTarget,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { AutomationEditorContent(target, session, onDismiss, modifier) }
}

@Composable
private fun AutomationEditorContent(
    target: AutomationEditorTarget,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutomationEditorViewModel =
        hiltViewModel<AutomationEditorViewModel, AutomationEditorViewModel.Factory>(
            creationCallback = { it.create(target, session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteCopies by remember { mutableStateOf(false) }
    val sheet = rememberModalSheetState()
    val dismiss by rememberUpdatedState(onDismiss)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.done) {
        if (!state.done) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        sheet.dismiss(dismiss)
    }
    ModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        state = sheet,
        dismissible = !state.saving
    ) {
        EditorTopBar(
            state = state,
            onCancel = { sheet.dismiss(onDismiss) },
            onSave = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                viewModel.save()
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
                EditorForm(
                    state = state,
                    onEdit = viewModel::edit,
                    deleteCopies = deleteCopies,
                    onDeleteCopiesChange = { deleteCopies = it },
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
        onDismissMutationError = viewModel::dismissMutationError,
        onDismissConfigError = {
            viewModel.dismissConfigError()
            onDismiss()
        }
    )
    if (confirmDelete) {
        LpmConfirmationDialog(
            title = stringResource(R.string.editor_delete_title),
            message = stringResource(
                if (state.global) R.string.editor_delete_global else R.string.editor_delete_project
            ),
            buttons = listOf(
                AlertButton(
                    stringResource(R.string.editor_delete_confirm),
                    AlertRole.Destructive
                ) { viewModel.delete(deleteCopies) },
                AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
            ),
            onDismissRequest = { confirmDelete = false }
        )
    }
}

@Composable
private fun EditorTopBar(state: AutomationEditorUiState, onCancel: () -> Unit, onSave: () -> Unit) {
    LpmTopBar(
        title = stringResource(
            if (state.editing) R.string.editor_edit_title else R.string.editor_new_title
        ),
        modifier = Modifier.padding(top = 16.dp),
        windowInsets = WindowInsets(0),
        leading = {
            GlassCapsuleButton(text = stringResource(R.string.cancel), onClick = onCancel)
        },
        trailing = {
            if (state.saving) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    ActivityIndicator(size = ActivityIndicatorSize.Small)
                }
            } else {
                GlassCapsuleButton(
                    text = stringResource(R.string.editor_save),
                    onClick = onSave,
                    enabled = state.canSave,
                    textStyle = LpmTheme.typography.headline
                )
            }
        }
    )
}

@Composable
private fun EditorLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ActivityIndicator()
            Text(
                text = stringResource(R.string.editor_loading),
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** A write the Mac refused, or a job it couldn't read (which closes the sheet). */
@Composable
private fun EditorAlerts(
    state: AutomationEditorUiState,
    onDismissMutationError: () -> Unit,
    onDismissConfigError: () -> Unit
) {
    val ok = stringResource(R.string.ok)
    state.mutationError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.editor_save_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = onDismissMutationError
        )
    }
    state.configError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.editor_load_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = onDismissConfigError
        )
    }
}
