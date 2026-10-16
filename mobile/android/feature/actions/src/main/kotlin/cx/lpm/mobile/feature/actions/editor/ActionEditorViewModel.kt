package cx.lpm.mobile.feature.actions.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigState
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.Zone
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
import kotlinx.serialization.json.JsonObject

data class ActionEditorUiState(
    val editing: Boolean = false,
    val draft: ActionDraft = ActionDraft(),
    val zones: List<Zone> = emptyList(),
    /** The file the action lives in: which zones it may go in. */
    val source: String = "project",
    /** The project's other actions: names it can't take. */
    val otherKeys: List<String> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val mutationError: String? = null,
    val bodyError: String? = null,
    /** The Mac wrote it: the sheet goes. */
    val done: Boolean = false
) {
    val canSave: Boolean get() = draft.validationError(otherKeys) == null && !loading && !saving

    val placementOptions: List<ActionPlacement>
        get() = ActionPlacement.options(zones, source, draft.seededPlacement)
}

/**
 * Create or edit a project action (iOS `ActionEditorSheet`). The Mac writes the
 * mapping to the same layer file a desktop edit would; an edit starts from the
 * action's body on disk so fields the form doesn't show survive.
 */
@HiltViewModel(assistedFactory = ActionEditorViewModel.Factory::class)
class ActionEditorViewModel @AssistedInject constructor(
    @Assisted("project") private val project: String,
    @Assisted("key") private val editingKey: String?,
    @Assisted private val sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val session: MacSession? = sessions[sessionKey]
    private val config: ProjectConfigStore? = session?.store()
    private val draft = MutableStateFlow(ActionDraft())
    private val seeded = MutableStateFlow(editingKey == null)
    private var seedBody: JsonObject? = null
    private var seedSection: String? = null
    private val seedSource = MutableStateFlow("project")

    // The done-token at the last Save/Delete; a higher one means that write landed.
    private var pendingToken: Int? = null

    val uiState: StateFlow<ActionEditorUiState> = combine(
        draft,
        seeded,
        seedSource,
        config?.state ?: flowOf(ProjectConfigState()),
        session?.store<ProjectsStore>()?.state ?: flowOf(null)
    ) { d, isSeeded, source, c, p ->
        val live = p?.project(project)
        ActionEditorUiState(
            editing = editingKey != null,
            draft = d,
            zones = live?.zones.orEmpty(),
            source = source,
            otherKeys = live?.actions.orEmpty().map { it.name }.filter { it != editingKey },
            loading = !isSeeded && c.actionBodyError == null,
            saving = c.mutationInFlight,
            mutationError = c.mutationError,
            bodyError = c.actionBodyError,
            done = pendingToken?.let { c.mutationDoneToken > it } == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionEditorUiState())

    init {
        val store = config
        if (store != null && editingKey != null) {
            store.loadActionBody(project, editingKey)
            store.state
                .onEach { c ->
                    val body = c.actionBody
                    if (!seeded.value && !c.actionBodyLoading && body != null) seed(body, c)
                }
                .launchIn(viewModelScope)
        } else {
            store?.clearActionBody()
        }
    }

    private fun seed(body: JsonObject, c: ProjectConfigState) {
        val key = editingKey ?: return
        seedBody = body
        seedSection = c.actionBodySection
        if (c.actionBodySource.isNotEmpty()) seedSource.value = c.actionBodySource
        val zones = session?.store<ProjectsStore>()?.state?.value?.project(project)?.zones.orEmpty()
        draft.value = ActionDraft.from(body, key, zones)
        seeded.value = true
    }

    fun edit(change: (ActionDraft) -> ActionDraft) = draft.update(change)

    fun save() {
        val state = uiState.value
        val store = config ?: return
        if (!state.canSave) return
        pendingToken = store.state.value.mutationDoneToken
        store.saveAction(
            project = project,
            key = state.draft.name.trimmingWhitespace(),
            payload = state.draft.payload(editing = editingKey != null, seedBody = seedBody),
            previousKey = editingKey,
            section = seedSection
        )
    }

    fun delete() {
        val store = config ?: return
        val key = editingKey ?: return
        pendingToken = store.state.value.mutationDoneToken
        store.deleteAction(project, key)
    }

    fun dismissMutationError() = config?.dismissMutationError()

    fun dismissBodyError() = config?.dismissActionBodyError()

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("project") project: String,
            @Assisted("key") editingKey: String?,
            sessionKey: SessionKey
        ): ActionEditorViewModel
    }
}
