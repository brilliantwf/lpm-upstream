package cx.lpm.mobile.feature.config.add

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.config.R
import cx.lpm.mobile.feature.config.ui.PlainKeyboard
import cx.lpm.mobile.feature.config.ui.UrlKeyboard

/** Every change the add-project form can make, each forwarded to its ViewModel. */
class AddProjectActions(
    val onMode: (AddProjectMode) -> Unit,
    val onBrowse: (FolderPurpose) -> Unit,
    val onCloneUrl: (String) -> Unit,
    val onBranch: (String) -> Unit,
    val onHostPick: (String) -> Unit,
    val onHost: (String) -> Unit,
    val onUser: (String) -> Unit,
    val onPort: (String) -> Unit,
    val onIdentityFile: (String) -> Unit,
    val onRemoteDir: (String) -> Unit,
    val onName: (String) -> Unit
)

/** What a folder picked in the browser is for. */
enum class FolderPurpose { ProjectFolder, CloneInto }

/** The mode picker, the chosen mode's fields, then the project's name. */
@Composable
internal fun AddProjectForm(state: AddProjectUiState, actions: AddProjectActions) {
    ListSection {
        ListRow(verticalPadding = 14.dp) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.config_mode_local),
                    stringResource(R.string.config_mode_clone),
                    stringResource(R.string.config_mode_ssh)
                ),
                selectedIndex = state.mode.ordinal,
                onSelect = { actions.onMode(AddProjectMode.entries[it]) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    when (state.mode) {
        AddProjectMode.Local -> LocalSection(state.draft, actions)
        AddProjectMode.Clone -> CloneSection(state.draft, actions)
        AddProjectMode.Ssh -> SshSections(state, actions)
    }
    ListSection(footer = stringResource(R.string.config_name_footer)) {
        TextFieldRow(
            value = state.draft.name,
            onValueChange = actions.onName,
            placeholder = stringResource(R.string.config_project_name),
            keyboardOptions = PlainKeyboard
        )
    }
}

@Composable
private fun LocalSection(draft: AddProjectDraft, actions: AddProjectActions) {
    ListSection(footer = stringResource(R.string.config_local_footer)) {
        FolderRow(
            label = stringResource(R.string.config_local_folder),
            value = draft.localRoot,
            onClick = { actions.onBrowse(FolderPurpose.ProjectFolder) }
        )
    }
}

@Composable
private fun CloneSection(draft: AddProjectDraft, actions: AddProjectActions) {
    ListSection(footer = stringResource(R.string.config_clone_footer)) {
        TextFieldRow(
            value = draft.cloneUrl,
            onValueChange = actions.onCloneUrl,
            placeholder = stringResource(R.string.config_repo_address),
            keyboardOptions = UrlKeyboard
        )
        FolderRow(
            label = stringResource(R.string.config_clone_into),
            value = draft.cloneParent,
            onClick = { actions.onBrowse(FolderPurpose.CloneInto) }
        )
        TextFieldRow(
            value = draft.branch,
            onValueChange = actions.onBranch,
            placeholder = stringResource(R.string.config_branch),
            keyboardOptions = PlainKeyboard
        )
    }
}

@Composable
private fun SshSections(state: AddProjectUiState, actions: AddProjectActions) {
    val draft = state.draft
    if (state.sshHostsLoading) {
        ListSection {
            ListRow {
                ActivityIndicator(size = ActivityIndicatorSize.Small)
                Text(
                    text = stringResource(R.string.config_ssh_loading),
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
    if (state.sshHosts.isNotEmpty()) {
        val select = stringResource(R.string.config_ssh_select)
        val manual = stringResource(R.string.config_ssh_manual)
        val labels = state.sshHosts.associate { host ->
            host.name to if (host.user.isEmpty()) {
                host.name
            } else {
                stringResource(R.string.config_ssh_host_label, host.name, host.user)
            }
        }
        ListSection(footer = stringResource(R.string.config_ssh_pick_footer)) {
            PickerRow(
                title = stringResource(R.string.config_ssh_pick),
                options = listOf("") + state.sshHosts.map { it.name } + MANUAL_HOST_PICK,
                selected = draft.hostPick,
                onSelect = actions.onHostPick,
                optionLabel = { pick ->
                    when (pick) {
                        "" -> select
                        MANUAL_HOST_PICK -> manual
                        else -> labels[pick] ?: pick
                    }
                }
            )
        }
    }
    if (!state.sshFieldsVisible) return
    ListSection(
        header = stringResource(R.string.config_ssh_connection),
        headerStyle = ListHeaderStyle.Title,
        footer = stringResource(R.string.config_ssh_connection_footer)
    ) {
        TextFieldRow(
            value = draft.host,
            onValueChange = actions.onHost,
            placeholder = stringResource(R.string.config_ssh_host),
            keyboardOptions = UrlKeyboard
        )
        TextFieldRow(
            value = draft.user,
            onValueChange = actions.onUser,
            placeholder = stringResource(R.string.config_ssh_user),
            keyboardOptions = PlainKeyboard
        )
        TextFieldRow(
            value = draft.port,
            onValueChange = actions.onPort,
            placeholder = stringResource(R.string.config_ssh_port),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
    ListSection(footer = stringResource(R.string.config_ssh_identity_footer)) {
        TextFieldRow(
            value = draft.identityFile,
            onValueChange = actions.onIdentityFile,
            placeholder = stringResource(R.string.config_ssh_identity),
            keyboardOptions = PlainKeyboard
        )
        TextFieldRow(
            value = draft.remoteDir,
            onValueChange = actions.onRemoteDir,
            placeholder = stringResource(R.string.config_ssh_dir),
            keyboardOptions = PlainKeyboard
        )
    }
}

/** A folder on the Mac, or "Choose…" until one is picked; opens the folder browser. */
@Composable
private fun FolderRow(label: String, value: String, onClick: () -> Unit) {
    ListRow(onClick = onClick, trailing = { DisclosureChevron() }) {
        Text(label, style = LpmTheme.typography.body)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value.ifEmpty { stringResource(R.string.config_choose) },
            style = LpmTheme.typography.body,
            color = if (value.isEmpty()) LpmTheme.colors.secondaryLabel else LpmTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
    }
}
