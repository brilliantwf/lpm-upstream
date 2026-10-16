package cx.lpm.mobile.feature.projects.services

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
import cx.lpm.mobile.feature.projects.R

/**
 * Create or edit one of a project's services ([editingKey] null creates one). The
 * config editor opens it; on save the Mac writes the same layer file a desktop edit
 * would, and the sheet goes once it has.
 */
@Composable
fun ServiceEditorSheet(
    project: String,
    editingKey: String?,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { ServiceEditorContent(project, editingKey, session, onDismiss, modifier) }
}

@Composable
private fun ServiceEditorContent(
    project: String,
    editingKey: String?,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceEditorViewModel =
        hiltViewModel<ServiceEditorViewModel, ServiceEditorViewModel.Factory>(
            creationCallback = { it.create(project, editingKey, session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalSheetState()
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.done) { if (state.done) sheet.dismiss(dismiss) }
    ModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        state = sheet,
        dismissible = !state.saving
    ) {
        LpmTopBar(
            title = stringResource(
                if (state.editing) R.string.service_edit_title else R.string.service_add_title
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
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        textStyle = LpmTheme.typography.headline
                    )
                }
            }
        )
        if (state.loading) {
            ServiceLoading()
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = ListDefaults.ContentTopPadding)
            ) {
                ServiceForm(
                    state = state,
                    onEdit = viewModel::edit,
                    onDelete = { confirmDelete = true }
                )
            }
        }
    }
    ServiceEditorAlerts(
        state = state,
        onDismissMutationError = { viewModel.dismissMutationError() },
        onDismissBodyError = { viewModel.dismissBodyError() },
        onDismiss = onDismiss
    )
    if (confirmDelete) {
        LpmConfirmationDialog(
            title = stringResource(R.string.service_delete_title),
            message = stringResource(R.string.service_delete_message),
            buttons = listOf(
                AlertButton(
                    stringResource(R.string.service_delete_confirm),
                    AlertRole.Destructive,
                    onClick = viewModel::delete
                ),
                AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
            ),
            onDismissRequest = { confirmDelete = false }
        )
    }
}

@Composable
private fun ServiceLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ActivityIndicator()
            Text(
                text = stringResource(R.string.service_loading),
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** A write the Mac refused, or a service it couldn't read (which closes the sheet). */
@Composable
private fun ServiceEditorAlerts(
    state: ServiceEditorUiState,
    onDismissMutationError: () -> Unit,
    onDismissBodyError: () -> Unit,
    onDismiss: () -> Unit
) {
    val ok = stringResource(R.string.ok)
    state.mutationError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.service_save_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = onDismissMutationError
        )
    }
    state.bodyError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.service_load_failed),
            message = error,
            buttons = listOf(AlertButton(ok, AlertRole.Cancel)),
            onDismissRequest = {
                onDismissBodyError()
                onDismiss()
            }
        )
    }
}
