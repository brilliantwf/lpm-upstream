package cx.lpm.mobile.feature.memory.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.navigation.MemorySessionKey
import cx.lpm.mobile.feature.memory.data.MemoryStore
import cx.lpm.mobile.feature.memory.data.memoryKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One session memory document (iOS `MemorySessionView`). The list carries only a
 * preview, so this fetches the full markdown on open and follows it live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemorySessionViewModel.Factory::class)
class MemorySessionViewModel @AssistedInject constructor(
    @Assisted val key: MemorySessionKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    private val store: MemoryStore? get() = sessions[key.session]?.store()

    // A delete before this page opened isn't this page's.
    private val deletedTickAtOpen = store?.state?.value?.deletedTick?.get(
        memoryKey(key.project, key.name)
    )

    private val initial = MemorySessionUiState(name = key.name, title = key.title, loading = true)

    val uiState: StateFlow<MemorySessionUiState> = sessions.session(key.session)
        .flatMapLatest { s ->
            s?.store<MemoryStore>()?.state?.map {
                it.sessionState(
                    key.project,
                    key.name,
                    key.title,
                    deletedTickAtOpen,
                    clock.instant()
                )
            } ?: flowOf(initial)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** Claims the project too: a reconnect replays whatever the store holds as active. */
    fun open() {
        store?.screenDidOpen(key.project)
        store?.openSession(key.project, key.name)
    }

    fun close() {
        store?.sessionDidClose(key.project, key.name)
        store?.screenDidClose(key.project)
    }

    fun retry() {
        store?.openSession(key.project, key.name)
    }

    suspend fun refresh() {
        store?.openSession(key.project, key.name)
        delay(REFRESH_SETTLE_MS)
    }

    fun delete() {
        store?.delete(key.project, key.name)
    }

    fun dismissConflict() {
        store?.consumeConflict(key.project, key.name)
    }

    fun dismissDeleteError() {
        store?.consumeDeleteError(key.project, key.name)
    }

    @AssistedFactory
    interface Factory {
        fun create(key: MemorySessionKey): MemorySessionViewModel
    }

    private companion object {
        const val REFRESH_SETTLE_MS = 600L
    }
}
