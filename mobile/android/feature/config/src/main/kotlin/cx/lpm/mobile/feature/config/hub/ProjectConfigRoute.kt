package cx.lpm.mobile.feature.config.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.navigation.ProjectConfigKey
import cx.lpm.mobile.feature.config.ConfigSlots
import cx.lpm.mobile.feature.config.R
import cx.lpm.mobile.feature.config.profile.ProfileEditorSheet
import cx.lpm.mobile.feature.config.yaml.YamlConfigEditorSheet

/** "Configure…": the configuration sheet, with each editor opening as a sheet over it. */
@Composable
fun ProjectConfigRoute(
    key: ProjectConfigKey,
    onDone: () -> Unit,
    slots: ConfigSlots,
    modifier: Modifier = Modifier,
    viewModel: ProjectConfigViewModel =
        hiltViewModel<ProjectConfigViewModel, ProjectConfigViewModel.Factory>(
            creationCallback = { it.create(key) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<ConfigEditor?>(null) }
    ProjectConfigScreen(
        state = state,
        onDone = onDone,
        onOpen = { editor = it },
        onDeleteService = viewModel::deleteService,
        onDeleteProfile = viewModel::deleteProfile,
        onDeleteAction = viewModel::deleteAction,
        modifier = modifier
    )
    editor?.let { open ->
        key(open) {
            ConfigEditorSheet(open, key, slots, onDismiss = { editor = null })
        }
    }
    // An editor shows its own copy of a failed write, so the sheet's waits for it to close.
    val error = state.mutationError
    if (editor == null && error != null) {
        LpmAlert(
            title = stringResource(R.string.config_update_failed),
            message = error,
            buttons = listOf(AlertButton(stringResource(R.string.ok), AlertRole.Cancel)),
            onDismissRequest = { viewModel.dismissMutationError() }
        )
    }
}

@Composable
private fun ConfigEditorSheet(
    editor: ConfigEditor,
    key: ProjectConfigKey,
    slots: ConfigSlots,
    onDismiss: () -> Unit
) {
    val project = key.project
    val session = key.session
    when (editor) {
        ConfigEditor.AddService -> slots.serviceEditor(project, null, session, onDismiss)
        is ConfigEditor.EditService -> slots.serviceEditor(project, editor.key, session, onDismiss)
        ConfigEditor.AddProfile -> ProfileEditorSheet(project, null, session, onDismiss)
        is ConfigEditor.EditProfile -> ProfileEditorSheet(project, editor.name, session, onDismiss)
        ConfigEditor.AddAction -> slots.actionEditor(project, null, session, onDismiss)
        is ConfigEditor.EditAction -> slots.actionEditor(project, editor.key, session, onDismiss)
        ConfigEditor.Yaml -> YamlConfigEditorSheet(project, session, onDismiss)
    }
}
