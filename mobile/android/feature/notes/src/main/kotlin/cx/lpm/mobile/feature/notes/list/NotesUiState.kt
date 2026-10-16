package cx.lpm.mobile.feature.notes.list

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.model.NoteSearchHit
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.notes.data.NotesSearchState
import cx.lpm.mobile.feature.notes.data.NotesState
import cx.lpm.mobile.feature.notes.data.notesKey
import cx.lpm.mobile.feature.notes.format.NotesClock

/** One chat in the list; an empty [title] reads "Untitled". */
@Immutable
data class NoteChatRowUi(val chat: NoteChat, val time: String, val busy: Boolean) {
    val title: String get() = chat.title
}

/** One search result; the hit's id is the note's, its chat is where it opens. */
@Immutable
data class NoteHitRowUi(val hit: NoteSearchHit, val time: String)

/** What covers the list when it has nothing to show. */
sealed interface NotesOverlay {
    data object None : NotesOverlay

    data object Loading : NotesOverlay

    data class SearchFailed(val message: String) : NotesOverlay

    data class NoResults(val query: String) : NotesOverlay

    data class Unavailable(val message: String) : NotesOverlay

    data object Empty : NotesOverlay
}

@Immutable
data class NotesUiState(
    val project: String,
    val search: String = "",
    val chats: List<NoteChatRowUi> = emptyList(),
    val hits: List<NoteHitRowUi> = emptyList(),
    val overlay: NotesOverlay = NotesOverlay.None,
    /** A create in flight disables the new-chat button. */
    val creating: Boolean = false,
    /** A create, rename or delete that failed, shown in the banner. */
    val chatError: String? = null,
    /** A chat the user just created, to open. */
    val created: NoteChat? = null
) {
    val searching: Boolean get() = search.trimmingWhitespace().isNotEmpty()
}

internal fun notesUiState(
    project: String,
    search: String,
    notes: NotesState,
    found: NotesSearchState,
    clock: NotesClock
): NotesUiState {
    val trimmed = search.trimmingWhitespace()
    val chats = notes.chatList(project)
    val hits = found.hits[project].orEmpty()
    val overlay = when {
        trimmed.isNotEmpty() -> when {
            hits.isNotEmpty() -> NotesOverlay.None

            project in found.loading -> NotesOverlay.Loading

            else -> found.error[project]?.let(NotesOverlay::SearchFailed)
                ?: NotesOverlay.NoResults(trimmed)
        }

        chats.isNotEmpty() -> NotesOverlay.None

        else -> notes.chatsError[project]?.let(NotesOverlay::Unavailable)
            ?: if (project !in notes.chatsLoaded) NotesOverlay.Loading else NotesOverlay.Empty
    }
    val createdId = notes.createdChatId[project]
    return NotesUiState(
        project = project,
        search = search,
        chats = chats.map { chat ->
            NoteChatRowUi(
                chat = chat,
                time = clock.relative(chat.updatedAt),
                busy = notesKey(project, chat.id) in notes.chatMutating
            )
        },
        hits = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            hits.map {
                NoteHitRowUi(it, clock.relative(it.ts))
            }
        },
        overlay = overlay,
        creating = project in notes.chatMutating,
        chatError = notes.chatError[project],
        created = createdId?.let { id -> chats.firstOrNull { it.id == id } }
    )
}
