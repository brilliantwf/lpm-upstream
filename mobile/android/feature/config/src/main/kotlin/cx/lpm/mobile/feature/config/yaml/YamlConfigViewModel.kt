package cx.lpm.mobile.feature.config.yaml

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigState
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
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

data class YamlConfigUiState(
    val layer: ConfigLayer = ConfigLayer.Project,
    val text: String = "",
    val loading: Boolean = false,
    val loadError: String? = null,
    /** False for a layer that doesn't apply here: the repo layer of an SSH project. */
    val available: Boolean = true,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val mutationError: String? = null,
    /** The Mac wrote it: the sheet goes. */
    val done: Boolean = false
) {
    val canSave: Boolean get() = dirty && !loading && !saving
}

/**
 * "Edit Configuration File" (iOS `YamlConfigEditorSheet`): a layer's raw text, saved
 * exactly as typed, so comments and formatting survive where the structured editors
 * would reflow them.
 */
@HiltViewModel(assistedFactory = YamlConfigViewModel.Factory::class)
class YamlConfigViewModel @AssistedInject constructor(
    @Assisted private val project: String,
    @Assisted private val sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val config: ProjectConfigStore? = sessions[sessionKey]?.store()
    private val layer = MutableStateFlow(ConfigLayer.Project)
    private val edited = MutableStateFlow("")
    private val original = MutableStateFlow("")
    private val loaded = MutableStateFlow(false)

    // The done-token at the last Save; a higher one means the Mac wrote it.
    private var pendingToken: Int? = null

    val uiState: StateFlow<YamlConfigUiState> = combine(
        combine(layer, edited, original, loaded, ::Draft),
        config?.state ?: flowOf(ProjectConfigState())
    ) { d, c ->
        YamlConfigUiState(
            layer = d.layer,
            text = d.edited,
            loading = c.configTextLoading,
            loadError = c.configTextError,
            available = c.configAvailable,
            dirty = d.loaded && c.configAvailable && d.edited != d.original,
            saving = c.mutationInFlight,
            mutationError = c.mutationError,
            done = pendingToken?.let { c.mutationDoneToken > it } == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YamlConfigUiState())

    init {
        config?.state
            ?.onEach { c ->
                val text = c.configText
                if (!loaded.value && !c.configTextLoading && text != null) {
                    edited.value = text
                    original.value = text
                    loaded.value = true
                }
            }
            ?.launchIn(viewModelScope)
        load()
    }

    fun selectLayer(next: ConfigLayer) {
        if (next == layer.value) return
        layer.value = next
        load()
    }

    fun edit(text: String) {
        edited.value = text
    }

    fun load() {
        loaded.value = false
        config?.readConfig(project, layer.value.wire)
    }

    fun save() {
        val store = config ?: return
        if (!uiState.value.canSave) return
        pendingToken = store.state.value.mutationDoneToken
        store.saveConfig(project, layer.value.wire, edited.value)
    }

    fun dismissMutationError() = config?.dismissMutationError()

    private data class Draft(
        val layer: ConfigLayer,
        val edited: String,
        val original: String,
        val loaded: Boolean
    )

    @AssistedFactory
    interface Factory {
        fun create(project: String, sessionKey: SessionKey): YamlConfigViewModel
    }
}
