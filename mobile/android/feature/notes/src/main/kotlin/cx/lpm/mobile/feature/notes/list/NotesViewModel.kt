package cx.lpm.mobile.feature.notes.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.core.navigation.NotesKey
import cx.lpm.mobile.feature.notes.data.NotesStore
import cx.lpm.mobile.feature.notes.format.NotesClock
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

/** A project's notebook (iOS `NotesScreen`): its chats, newest activity first, and search. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = NotesViewModel.Factory::class)
class NotesViewModel @AssistedInject constructor(
    @Assisted val key: NotesKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    private val store: NotesStore? get() = sessions[key.session]?.store()
    private val search = MutableStateFlow("")

    private val initial = NotesUiState(project = key.project, overlay = NotesOverlay.Loading)

    val uiState: StateFlow<NotesUiState> = sessions.session(key.session)
        .flatMapLatest { s -> s?.let(::listState) ?: flowOf(initial) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private fun listState(s: MacSession) = s.store<NotesStore>().let { notes ->
        combine(notes.state, notes.search.state, search) { state, found, query ->
            notesUiState(key.project, query, state, found, NotesClock(clock.instant(), clock.zone))
        }
    }

    fun open() {
        store?.screenDidOpen(key.project)
    }

    fun close() {
        store?.screenDidClose(key.project)
    }

    fun setSearch(query: String) {
        search.value = query
    }

    /** Ask the Mac for what the field says now, or forget the results once it's empty. */
    fun runSearch() {
        val s = store ?: return
        val trimmed = search.value.trimmingWhitespace()
        if (trimmed.isEmpty()) {
            s.search.clear(
                key.project
            )
        } else {
            s.search.search(key.project, trimmed)
        }
    }

    suspend fun refresh() {
        store?.loadChats(key.project)
        delay(REFRESH_SETTLE_MS)
    }

    fun create(title: String) {
        store?.createChat(key.project, title)
    }

    fun rename(chat: NoteChat, title: String) {
        store?.renameChat(key.project, chat.id, title)
    }

    fun delete(chat: NoteChat) {
        store?.deleteChat(key.project, chat.id)
    }

    fun consumeCreated() {
        store?.consumeCreated(key.project)
    }

    fun dismissError() {
        store?.dismissChatError(key.project)
    }

    @AssistedFactory
    interface Factory {
        fun create(key: NotesKey): NotesViewModel
    }

    companion object {
        /** How long typing pauses before the Mac is asked. */
        const val SEARCH_DEBOUNCE_MS = 300L
        private const val REFRESH_SETTLE_MS = 500L
    }
}
