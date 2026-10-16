package cx.lpm.mobile.feature.config.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigState
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.Profile
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.navigation.ProjectConfigKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

data class ProjectConfigUiState(
    val services: List<Service> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val actions: List<Action> = emptyList(),
    val mutationError: String? = null
)

/**
 * "Configure…" (iOS `ProjectConfigView`): the project's services, profiles and
 * actions, each with an editor, and the raw configuration file. The lists follow the
 * projects push, so an edit shows the moment the Mac has written it.
 */
@HiltViewModel(assistedFactory = ProjectConfigViewModel.Factory::class)
class ProjectConfigViewModel @AssistedInject constructor(
    @Assisted val key: ProjectConfigKey,
    sessions: MacSessions
) : ViewModel() {
    private val session: MacSession? = sessions[key.session]
    private val config: ProjectConfigStore? = session?.store()

    // The project as last seen, so the lists stay put if it drops out of a push.
    private val live = (session?.store<ProjectsStore>()?.state ?: flowOf(null))
        .map { it?.project(key.project) }
        .runningFold(null as Project?) { last, next -> next ?: last }

    val uiState: StateFlow<ProjectConfigUiState> = combine(
        live,
        config?.state ?: flowOf(ProjectConfigState())
    ) { p, c ->
        ProjectConfigUiState(
            services = p?.allServices.orEmpty(),
            profiles = p?.profiles.orEmpty(),
            actions = p?.actions.orEmpty(),
            mutationError = c.mutationError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectConfigUiState())

    fun deleteService(name: String) = config?.deleteService(key.project, name)

    fun deleteProfile(name: String) = config?.deleteProfile(key.project, name)

    fun deleteAction(name: String) = config?.deleteAction(key.project, name)

    fun dismissMutationError() = config?.dismissMutationError()

    @AssistedFactory
    interface Factory {
        fun create(key: ProjectConfigKey): ProjectConfigViewModel
    }
}
