package cx.lpm.mobile.feature.memory.create

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.core.protocol.Wire
import cx.lpm.mobile.feature.memory.data.MemoryState
import cx.lpm.mobile.feature.memory.data.MemoryStore
import cx.lpm.mobile.feature.memory.data.memoryKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@Immutable
data class MemoryCreateUiState(
    val title: String = "",
    /** The short name agents resume the session by; "" until the title has a usable word. */
    val slug: String = "",
    /** The project already has a session by that name: Create opens it. */
    val exists: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    /** The session to open once the sheet is gone: just created, or already there. */
    val opened: String? = null
)

/**
 * Starts a new session memory (iOS `MemoryCreateSheet`). The typed title becomes both
 * the document's heading and the short name agents resume it by, and the document is
 * seeded with the headings the memory skill expects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemoryCreateViewModel.Factory::class)
class MemoryCreateViewModel @AssistedInject constructor(
    @Assisted private val project: String,
    @Assisted session: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val store: MemoryStore? = sessions[session]?.store()
    private val title = MutableStateFlow("")
    private val opened = MutableStateFlow<String?>(null)

    private val memory: StateFlow<MemoryState> = sessions.session(session)
        .flatMapLatest { it?.store<MemoryStore>()?.state ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MemoryState())

    val uiState: StateFlow<MemoryCreateUiState> =
        combine(memory, title, opened) { s, text, open ->
            val slug = Wire.slugify(text)
            val k = memoryKey(project, slug)
            MemoryCreateUiState(
                title = text,
                slug = slug,
                exists = s.sessions(project).any { it.name == slug },
                saving = k in s.saving,
                saveError = if (slug.isEmpty()) null else s.saveError[k],
                opened = open
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryCreateUiState())

    init {
        memory.onEach { s ->
            val created = s.createdName[project]
            if (!created.isNullOrEmpty()) {
                store?.consumeCreated(project)
                opened.value = created
            }
        }.launchIn(viewModelScope)
    }

    fun setTitle(text: String) {
        title.value = text
    }

    /**
     * Reusing an existing name opens that session instead of overwriting it: the create
     * path carries no baseline, so a save here would replace the agent's work outright.
     */
    fun create() {
        val state = uiState.value
        val slug = Wire.slugify(title.value)
        if (slug.isEmpty() || state.saving) return
        if (memory.value.sessions(project).any { it.name == slug }) {
            opened.value = slug
            return
        }
        store?.create(project, slug, skeleton(title.value))
    }

    fun dismissError() {
        store?.consumeSaveError(project, Wire.slugify(title.value))
    }

    private fun skeleton(raw: String): String {
        val heading = raw.trimmingWhitespace()
        return "# $heading\n\n## Goal\n\n\n## Current state\n\n\n## Timeline\n"
    }

    @AssistedFactory
    interface Factory {
        fun create(project: String, session: SessionKey): MemoryCreateViewModel
    }
}
