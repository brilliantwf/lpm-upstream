package cx.lpm.mobile.feature.config.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigState
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.SshHostInfo
import cx.lpm.mobile.core.model.trimmingWhitespace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class AddProjectUiState(
    val mode: AddProjectMode = AddProjectMode.Local,
    val draft: AddProjectDraft = AddProjectDraft(),
    val sshHosts: List<SshHostInfo> = emptyList(),
    val sshHostsLoaded: Boolean = false,
    val sshHostsError: String? = null,
    val saving: Boolean = false,
    val mutationError: String? = null,
    /** The Mac added it: the sheet goes. */
    val done: Boolean = false
) {
    val canCreate: Boolean get() = draft.isComplete(mode) && !saving

    val sshHostsLoading: Boolean
        get() = !sshHostsLoaded && sshHostsError == null && sshHosts.isEmpty()

    /**
     * The connection fields show once a host (or manual entry) is picked, or when the
     * Mac has no saved hosts or couldn't list them — the desktop's `showFields`.
     */
    val sshFieldsVisible: Boolean
        get() = draft.hostPick.isNotEmpty() ||
            sshHostsError != null ||
            (sshHostsLoaded && sshHosts.isEmpty())
}

/**
 * "Add Project…" (iOS `AddProjectSheet`): a folder on the Mac, a clone of a
 * repository, or a project on an SSH host. Each settles through the shared config
 * write, so the button spins while the Mac works and the sheet goes once it's done.
 */
@HiltViewModel
class AddProjectViewModel @Inject constructor(sessions: MacSessions) : ViewModel() {
    private val config: ProjectConfigStore? = sessions[SessionKey.Live]?.store()
    private val mode = MutableStateFlow(AddProjectMode.Local)
    private val draft = MutableStateFlow(AddProjectDraft())

    // The done-token at the last Create; a higher one means the Mac added it.
    private var pendingToken: Int? = null

    val uiState: StateFlow<AddProjectUiState> = combine(
        mode,
        draft,
        config?.state ?: flowOf(ProjectConfigState())
    ) { m, d, c ->
        AddProjectUiState(
            mode = m,
            draft = d,
            sshHosts = c.sshHosts,
            sshHostsLoaded = c.sshHostsLoaded,
            sshHostsError = c.sshHostsError,
            saving = c.mutationInFlight,
            mutationError = c.mutationError,
            done = pendingToken?.let { c.mutationDoneToken > it } == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddProjectUiState())

    private val hosts: List<SshHostInfo> get() = config?.state?.value?.sshHosts.orEmpty()

    fun selectMode(next: AddProjectMode) {
        if (next == mode.value) return
        mode.value = next
        draft.update { it.rederived(next) }
        if (next == AddProjectMode.Ssh) config?.loadSshHosts()
    }

    fun pickLocalRoot(path: String) = draft.update {
        it.copy(localRoot = path).rederived(mode.value)
    }

    fun editCloneUrl(value: String) = draft.update {
        it.copy(cloneUrl = value).rederived(mode.value)
    }

    fun pickCloneParent(path: String) = draft.update { it.copy(cloneParent = path) }

    fun editBranch(value: String) = draft.update { it.copy(branch = value) }

    fun pickHost(pick: String) = draft.update { it.pickingHost(pick, hosts) }

    fun editHost(value: String) = draft.update { it.editingHost(value, hosts) }

    fun editUser(value: String) = draft.update { it.copy(user = value).rederived(mode.value) }

    fun editPort(value: String) = draft.update { it.copy(port = value) }

    fun editIdentityFile(value: String) = draft.update { it.copy(identityFile = value) }

    fun editRemoteDir(value: String) = draft.update { it.copy(remoteDir = value) }

    fun editName(value: String) = draft.update { it.copy(name = value, nameEdited = true) }

    fun create() {
        val store = config ?: return
        if (!uiState.value.canCreate) return
        pendingToken = store.state.value.mutationDoneToken
        val d = draft.value
        val name = slugify(d.name)
        when (mode.value) {
            AddProjectMode.Local -> store.createProject(name, d.localRoot.trimmingWhitespace())

            AddProjectMode.Clone -> store.cloneProject(
                name = name,
                url = d.cloneUrl.trimmingWhitespace(),
                branch = d.branch.trimmingWhitespace(),
                destParent = d.cloneParent.trimmingWhitespace()
            )

            AddProjectMode.Ssh -> store.createSshProject(name, d.sshPayload())
        }
    }

    fun dismissMutationError() = config?.dismissMutationError()
}
