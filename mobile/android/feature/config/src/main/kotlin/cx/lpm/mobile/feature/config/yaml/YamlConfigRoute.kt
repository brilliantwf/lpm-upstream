package cx.lpm.mobile.feature.config.yaml

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.InteractiveDismissDisabled
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.core.navigation.YamlConfigKey

/** "Edit Configuration File" as its own sheet, from a project whose YAML the Mac can't read. */
@Composable
fun YamlConfigRoute(
    key: YamlConfigKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YamlConfigViewModel =
        hiltViewModel<YamlConfigViewModel, YamlConfigViewModel.Factory>(
            creationCallback = { it.create(key.project, key.session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InteractiveDismissDisabled(state.saving)
    SavedEffect(state.done, onDismiss)
    YamlConfigContent(
        state = state,
        onCancel = onDismiss,
        onSave = viewModel::save,
        onSelectLayer = viewModel::selectLayer,
        onEdit = viewModel::edit,
        onRetry = viewModel::load,
        onDismissError = { viewModel.dismissMutationError() },
        modifier = modifier
    )
}

/** "Edit Configuration File" over the configuration sheet. */
@Composable
fun YamlConfigEditorSheet(
    project: String,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PresentationScope { YamlConfigSheetContent(project, session, onDismiss, modifier) }
}

@Composable
private fun YamlConfigSheetContent(
    project: String,
    session: SessionKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YamlConfigViewModel =
        hiltViewModel<YamlConfigViewModel, YamlConfigViewModel.Factory>(
            creationCallback = { it.create(project, session) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet = rememberModalSheetState()
    SavedEffect(state.done) { sheet.dismiss(onDismiss) }
    ModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        state = sheet,
        dismissible = !state.saving,
        plain = true
    ) {
        YamlConfigContent(
            state = state,
            onCancel = { sheet.dismiss(onDismiss) },
            onSave = viewModel::save,
            onSelectLayer = viewModel::selectLayer,
            onEdit = viewModel::edit,
            onRetry = viewModel::load,
            onDismissError = { viewModel.dismissMutationError() }
        )
    }
}

/** The Mac wrote the file: a success tap, then the sheet goes. */
@Composable
private fun SavedEffect(done: Boolean, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(onDone)
    LaunchedEffect(done) {
        if (done) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            latest()
        }
    }
}

@Composable
private fun YamlConfigContent(
    state: YamlConfigUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onSelectLayer: (ConfigLayer) -> Unit,
    onEdit: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemBackground)
    ) {
        YamlConfigEditor(
            state = state,
            onCancel = onCancel,
            onSave = onSave,
            onSelectLayer = onSelectLayer,
            onEdit = onEdit,
            onRetry = onRetry
        )
    }
    YamlSaveErrorAlert(state.mutationError, onDismissError)
}
