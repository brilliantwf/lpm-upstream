package cx.lpm.mobile.feature.terminal.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.HistoryFolder
import cx.lpm.mobile.core.model.HistoryItem
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryFilter {
    data object All : HistoryFilter

    data object Favorites : HistoryFilter

    data object Drafts : HistoryFilter

    data class Folder(val id: String) : HistoryFilter
}

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val folders: List<HistoryFolder> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.All,
    val search: String = "",
    val loading: Boolean = false,
    /** More pages may still come from the server. */
    val stillPaging: Boolean = false
) {
    /** "Empty" only once the server has no more pages — a page can hold no matching rows. */
    val showsEmpty: Boolean get() = items.isEmpty() && !stillPaging
}

/**
 * The message history sheet's data, one per opening of the sheet. A paged, searchable list of sent prompts and drafts with
 * favorites and folders (`historyQuery`, keyset-paginated).
 */
@HiltViewModel(assistedFactory = HistoryViewModel.Factory::class)
class HistoryViewModel @AssistedInject constructor(
    @Assisted session: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(session: SessionKey): HistoryViewModel
    }

    private val store = sessions[session]?.store<HistoryStore>()
    private val filter = MutableStateFlow<HistoryFilter>(HistoryFilter.All)
    private val search = MutableStateFlow("")
    private var searchReload: Job? = null

    val uiState: StateFlow<HistoryUiState> = combine(
        store?.state ?: flowOf(HistoryState()),
        filter,
        search
    ) { s, f, q ->
        HistoryUiState(
            // Drafts have no server-side flag: they're filtered from the unscoped query.
            items = if (f == HistoryFilter.Drafts) s.items.filter { it.isDraft } else s.items,
            folders = s.folders,
            filter = f,
            search = q,
            loading = s.loading,
            stillPaging = s.loading || s.loadingMore || s.hasMore
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        store?.loadFolders()
        scheduleReload()
    }

    fun setFilter(next: HistoryFilter) {
        if (filter.value == next) return
        filter.value = next
        reload()
    }

    /** Typing is debounced before re-querying. */
    fun setSearch(text: String) {
        search.value = text
        scheduleReload()
    }

    private fun scheduleReload() {
        searchReload?.cancel()
        searchReload = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload()
        }
    }

    private fun reload() {
        val text = search.value.trim()
        when (val f = filter.value) {
            HistoryFilter.All, HistoryFilter.Drafts -> store?.loadFirst(
                null,
                text,
                favoritesOnly = false,
                folder = null
            )

            HistoryFilter.Favorites -> store?.loadFirst(
                null,
                text,
                favoritesOnly = true,
                folder = null
            )

            is HistoryFilter.Folder -> store?.loadFirst(
                null,
                text,
                favoritesOnly = false,
                folder = f.id
            )
        }
    }

    fun loadMore() {
        store?.loadMore()
    }

    /**
     * A page with no visible rows (Drafts over a page of sent messages) still pages
     * on, since an empty list shows no row to trigger the next page.
     */
    fun pageIfEmpty() {
        val s = store?.state?.value ?: return
        if (uiState.value.items.isEmpty() && s.hasMore && !s.loading &&
            !s.loadingMore
        ) {
            store.loadMore()
        }
    }

    fun toggleFavorite(id: String) = store?.toggleFavorite(id)

    fun delete(id: String) = store?.delete(id)

    fun setFolder(id: String, folder: String?) = store?.setFolder(id, folder)

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) store?.createFolder(trimmed)
    }

    fun deleteFolder(id: String) {
        if (filter.value == HistoryFilter.Folder(id)) setFilter(HistoryFilter.All)
        store?.deleteFolder(id)
    }

    /** The sheet closed: its query is no longer live. */
    override fun onCleared() {
        store?.screenDidClose()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** The text to load or send: the message plus the paths of any images it carries. */
internal fun HistoryItem.displayText(): String {
    if (images.isEmpty()) return text
    val paths = images.values.sorted().joinToString(" ")
    val base = text.trim()
    return if (base.isEmpty()) paths else text + (if (text.endsWith(" ")) "" else " ") + paths
}
