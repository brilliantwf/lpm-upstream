package cx.lpm.mobile.feature.memory.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.navigation.MemorySessionKey
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
data class MemoryEditorUiState(
    val draft: String = "",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val conflictNotice: String? = null,
    val saveError: String? = null,
    /** The Mac accepted the save: the sheet goes. */
    val saved: Boolean = false
)

/**
 * The full-document editor for one session memory (iOS `MemoryEditorSheet`). Saving
 * is compare-and-swap against the text this editor loaded, so an agent that wrote the
 * same session first is caught: the store reloads the newer version and the draft
 * reseeds from it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemoryEditorViewModel.Factory::class)
class MemoryEditorViewModel @AssistedInject constructor(
    @Assisted private val key: MemorySessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val store: MemoryStore? = sessions[key.session]?.store()
    private val k = memoryKey(key.project, key.name)
    private val draft = MutableStateFlow("")
    private val baseline = MutableStateFlow("")
    private var seededRevision = -1
    private val savedTickAtOpen = store?.state?.value?.savedTick?.get(k)

    private val memory: StateFlow<MemoryState> = sessions.session(key.session)
        .flatMapLatest { it?.store<MemoryStore>()?.state ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MemoryState())

    val uiState: StateFlow<MemoryEditorUiState> =
        combine(memory, draft, baseline) { s, text, base ->
            MemoryEditorUiState(
                draft = text,
                dirty = text != base,
                saving = k in s.saving,
                conflictNotice = s.conflictNotice[k],
                saveError = s.saveError[k],
                saved = s.savedTick[k] != savedTickAtOpen
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryEditorUiState())

    init {
        store?.beginEditing(key.project, key.name)
        memory.onEach(::seed).launchIn(viewModelScope)
    }

    fun edit(text: String) {
        draft.value = text
    }

    fun save() {
        store?.save(key.project, key.name, draft.value, baseline.value)
    }

    fun dismissConflict() {
        store?.consumeConflict(key.project, key.name)
    }

    fun dismissSaveError() {
        store?.consumeSaveError(key.project, key.name)
    }

    override fun onCleared() {
        store?.endEditing(key.project, key.name)
    }

    /**
     * Reseed whenever a newer document arrives — the first load, a live refresh, or
     * the reload that follows a lost compare-and-swap.
     */
    private fun seed(s: MemoryState) {
        val revision = s.documentRevision[k] ?: 0
        val content = s.documents[k]?.content ?: return
        if (revision == seededRevision) return
        seededRevision = revision
        draft.value = content
        baseline.value = content
    }

    @AssistedFactory
    interface Factory {
        fun create(key: MemorySessionKey): MemoryEditorViewModel
    }
}
