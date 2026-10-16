package cx.lpm.mobile.feature.config.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigState
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.trimmingWhitespace
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditorUiState(
    val editing: Boolean = false,
    val name: String = "",
    /** The project's services in its own order, each one a row to pick. */
    val services: List<String> = emptyList(),
    val selected: List<String> = emptyList(),
    /** Names the profile can't take. */
    val otherProfiles: List<String> = emptyList(),
    val saving: Boolean = false,
    val mutationError: String? = null,
    /** The Mac wrote it: the sheet goes. */
    val done: Boolean = false
) {
    val canSave: Boolean
        get() {
            val trimmed = name.trimmingWhitespace()
            return trimmed.isNotEmpty() &&
                trimmed !in otherProfiles &&
                selected.isNotEmpty() &&
                !saving
        }
}

/**
 * Create or edit a profile, a named set of services started together (iOS
 * `ProfileEditorSheet`). Profiles ride in the projects push, so editing needs no read.
 */
@HiltViewModel(assistedFactory = ProfileEditorViewModel.Factory::class)
class ProfileEditorViewModel @AssistedInject constructor(
    @Assisted("project") private val project: String,
    @Assisted("name") private val editingName: String?,
    @Assisted private val sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val session: MacSession? = sessions[sessionKey]
    private val config: ProjectConfigStore? = session?.store()
    private val name = MutableStateFlow("")
    private val selected = MutableStateFlow(emptyList<String>())
    private val live = session?.store<ProjectsStore>()?.state?.map { it.project(project) }
        ?: flowOf(null)

    // The done-token at the last Save/Delete; a higher one means that write landed.
    private var pendingToken: Int? = null

    val uiState: StateFlow<ProfileEditorUiState> = combine(
        name,
        selected,
        live,
        config?.state ?: flowOf(ProjectConfigState())
    ) { n, picked, p, c ->
        ProfileEditorUiState(
            editing = editingName != null,
            name = n,
            services = p.serviceNames(),
            selected = picked,
            otherProfiles = p?.profiles.orEmpty().map { it.name }.filter { it != editingName },
            saving = c.mutationInFlight,
            mutationError = c.mutationError,
            done = pendingToken?.let { c.mutationDoneToken > it } == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEditorUiState())

    init {
        if (editingName != null) {
            viewModelScope.launch {
                val p = live.filterNotNull().first()
                val profile = p.profiles.firstOrNull { it.name == editingName } ?: return@launch
                name.value = profile.name
                selected.value = p.serviceNames().filter { it in profile.services }
            }
        }
    }

    fun editName(value: String) {
        name.value = value
    }

    fun toggle(service: String) = selected.update {
        if (service in it) it - service else it + service
    }

    fun save() {
        val state = uiState.value
        val store = config ?: return
        if (!state.canSave) return
        pendingToken = store.state.value.mutationDoneToken
        store.saveProfile(
            project = project,
            name = state.name.trimmingWhitespace(),
            services = state.services.filter { it in state.selected },
            previousName = editingName
        )
    }

    fun delete() {
        val store = config ?: return
        val profile = editingName ?: return
        pendingToken = store.state.value.mutationDoneToken
        store.deleteProfile(project, profile)
    }

    fun dismissMutationError() = config?.dismissMutationError()

    private fun Project?.serviceNames(): List<String> = this?.allServices.orEmpty().map { it.name }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("project") project: String,
            @Assisted("name") editingName: String?,
            sessionKey: SessionKey
        ): ProfileEditorViewModel
    }
}
