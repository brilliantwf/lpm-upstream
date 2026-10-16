package cx.lpm.mobile.feature.notes.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.PlainListDefaults
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.model.NoteSearchHit
import cx.lpm.mobile.feature.notes.ui.NotesErrorBanner

/** What the list's rows, field and bar do. */
@Immutable
class NotesActions(
    val back: () -> Unit = {},
    val newChat: () -> Unit = {},
    val search: (String) -> Unit = {},
    val beginSearch: () -> Unit = {},
    val endSearch: () -> Unit = {},
    val open: (NoteChat) -> Unit = {},
    val openHit: (NoteSearchHit) -> Unit = {},
    val rename: (NoteChat) -> Unit = {},
    val delete: (NoteChat) -> Unit = {},
    val dismissError: () -> Unit = {}
)

/**
 * A project's notebook (iOS `NotesScreen`): its chats, newest activity first, and a
 * search across every note in them. A row or a hit opens that chat's thread.
 * [searchActive] gives the bar's place to the search field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NotesUiState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    searchActive: Boolean = false,
    actions: NotesActions = NotesActions()
) {
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemBackground)
    ) {
        NotesTopBar(state, searchActive, actions)
        state.chatError?.let { NotesErrorBanner(it, actions.dismissError) }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = PlainListDefaults.TopInset, bottom = 24.dp)
            ) {
                if (state.searching) {
                    itemsIndexed(state.hits, key = { _, it -> "hit-" + it.hit.id }) { i, row ->
                        NoteHitRow(row, first = i == 0, onOpen = { actions.openHit(row.hit) })
                    }
                } else {
                    itemsIndexed(state.chats, key = { _, it -> "chat-" + it.chat.id }) { i, row ->
                        NoteChatRow(
                            row = row,
                            first = i == 0,
                            onOpen = { actions.open(row.chat) },
                            onRename = { actions.rename(row.chat) },
                            onDelete = { actions.delete(row.chat) }
                        )
                    }
                }
            }
            NotesOverlayView(state.overlay)
        }
    }
}
