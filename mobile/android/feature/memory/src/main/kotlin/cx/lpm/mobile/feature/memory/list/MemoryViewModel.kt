package cx.lpm.mobile.feature.memory.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.navigation.MemoryKey
import cx.lpm.mobile.feature.memory.data.MemoryStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** A project's session memory (iOS `MemoryScreen`), newest first. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemoryViewModel.Factory::class)
class MemoryViewModel @AssistedInject constructor(
    @Assisted val key: MemoryKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    private val store: MemoryStore? get() = sessions[key.session]?.store()

    // The session a delete was asked for, so its failure can be shown.
    private val deleting = MutableStateFlow<String?>(null)

    private val initial = MemoryUiState(project = key.project)

    val uiState: StateFlow<MemoryUiState> = sessions.session(key.session)
        .flatMapLatest { s -> s?.let(::listState) ?: flowOf(initial) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private fun listState(s: MacSession) = combine(
        s.store<MemoryStore>().state,
        s.store<ProjectsStore>().state,
        deleting
    ) { memory, projects, name ->
        memory.listState(key.project, projects, name, clock.instant())
    }

    fun open() {
        store?.screenDidOpen(key.project)
    }

    fun close() {
        store?.screenDidClose(key.project)
    }

    fun retry() {
        store?.load(key.project)
    }

    suspend fun refresh() {
        store?.load(key.project)
        delay(REFRESH_SETTLE_MS)
    }

    fun delete(name: String) {
        deleting.value = name
        store?.delete(key.project, name)
    }

    fun dismissDeleteError() {
        deleting.value?.let { store?.consumeDeleteError(key.project, it) }
        deleting.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(key: MemoryKey): MemoryViewModel
    }

    private companion object {
        const val REFRESH_SETTLE_MS = 600L
    }
}
