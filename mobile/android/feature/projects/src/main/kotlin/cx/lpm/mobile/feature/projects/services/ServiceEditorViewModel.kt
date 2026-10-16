package cx.lpm.mobile.feature.projects.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigState
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ServiceEditorUiState(
    val editing: Boolean = false,
    val draft: ServiceDraft = ServiceDraft(),
    /** The project's other services: names it can't take, and what it may depend on. */
    val otherServices: List<String> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val mutationError: String? = null,
    val bodyError: String? = null,
    /** The Mac wrote it: the sheet goes. */
    val done: Boolean = false
) {
    val canSave: Boolean
        get() = draft.validationError(otherServices) == null && !loading && !saving
}

/**
 * Create or edit one of a project's services (iOS `ServiceEditorSheet`); the Mac
 * writes it to the same layer file a desktop edit would.
 */
@HiltViewModel(assistedFactory = ServiceEditorViewModel.Factory::class)
class ServiceEditorViewModel @AssistedInject constructor(
    @Assisted("project") private val project: String,
    @Assisted("key") private val editingKey: String?,
    @Assisted private val sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val session: MacSession? = sessions[sessionKey]
    private val config: ProjectConfigStore? = session?.store()
    private val draft = MutableStateFlow(ServiceDraft())
    private val seeded = MutableStateFlow(editingKey == null)

    // The done-token at the last Save/Delete; a higher one means that write landed.
    private var pendingToken: Int? = null

    val uiState: StateFlow<ServiceEditorUiState> = combine(
        draft,
        seeded,
        config?.state ?: flowOf(ProjectConfigState()),
        session?.store<ProjectsStore>()?.state ?: flowOf(null)
    ) { d, isSeeded, c, p ->
        val services = p?.project(project)?.allServices.orEmpty().map { it.name }
        ServiceEditorUiState(
            editing = editingKey != null,
            draft = d,
            otherServices = services.filter { it != editingKey },
            loading = !isSeeded && c.serviceBodyError == null,
            saving = c.mutationInFlight,
            mutationError = c.mutationError,
            bodyError = c.serviceBodyError,
            done = pendingToken?.let { c.mutationDoneToken > it } == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceEditorUiState())

    init {
        val store = config
        if (store != null && editingKey != null) {
            // A body left from an earlier edit would belong to another service.
            store.loadServiceBody(project, editingKey)
            store.state
                .onEach { c ->
                    val body = c.serviceBody
                    if (!seeded.value && !c.serviceBodyLoading && body != null) {
                        draft.value = ServiceDraft.from(body, editingKey)
                        seeded.value = true
                    }
                }
                .launchIn(viewModelScope)
        } else {
            store?.clearServiceBody()
        }
    }

    fun edit(change: (ServiceDraft) -> ServiceDraft) = draft.update(change)

    fun save() {
        val state = uiState.value
        val store = config ?: return
        if (!state.canSave) return
        pendingToken = store.state.value.mutationDoneToken
        store.saveService(
            project = project,
            key = state.draft.name.trimmingWhitespace(),
            payload = state.draft.payload(state.otherServices),
            previousKey = editingKey
        )
    }

    fun delete() {
        val store = config ?: return
        val key = editingKey ?: return
        pendingToken = store.state.value.mutationDoneToken
        store.deleteService(project, key)
    }

    fun dismissMutationError() = config?.dismissMutationError()

    fun dismissBodyError() = config?.dismissServiceBodyError()

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("project") project: String,
            @Assisted("key") editingKey: String?,
            sessionKey: SessionKey
        ): ServiceEditorViewModel
    }
}
