package cx.lpm.mobile.feature.terminal.history

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.historyCreateFolder
import cx.lpm.mobile.core.data.client.historyDelete
import cx.lpm.mobile.core.data.client.historyDeleteFolder
import cx.lpm.mobile.core.data.client.historySaveDraft
import cx.lpm.mobile.core.data.client.historySetFolder
import cx.lpm.mobile.core.data.client.historyToggleFavorite
import cx.lpm.mobile.core.data.client.requestHistoryFolders
import cx.lpm.mobile.core.data.client.requestHistoryQuery
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.HistoryCursor
import cx.lpm.mobile.core.model.HistoryFolder
import cx.lpm.mobile.core.model.HistoryItem
import java.text.Collator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HistoryState(
    val items: List<HistoryItem> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val folders: List<HistoryFolder> = emptyList()
)

/**
 * The message-history screen's data (paged `historyQuery`). A reply carries no echo
 * of its parameters, so each request queues a generation saying whether its reply
 * replaces (first page) or appends (next page); replies come back in request
 * order, and only the newest generation's may change the list.
 */
class HistoryStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = mutable.asStateFlow()

    private data class Query(
        val project: String? = null,
        val search: String? = null,
        val favoritesOnly: Boolean = false,
        val folder: String? = null
    )

    private data class Pending(val generation: Int, val paging: Boolean)

    private var query = Query()
    private var generation = 0
    private val pending = ArrayDeque<Pending>()

    // Whether the screen is open, so a reconnect re-issues its query.
    private var active = false

    private val client: LpmClient? get() = session.client

    override fun wire(client: LpmClient) {
        client.onHistoryQuery = ::applyPage
        client.onHistorySaveDraft = {}
        client.onHistoryToggleFavorite = { id, favorite, error ->
            if (error == null) updateItem(id) { it.copy(favorite = favorite) }
        }
        client.onHistoryMutated = { _, _ -> this.client?.requestHistoryFolders() }
        client.onHistoryFolders = { folders -> mutable.update { it.copy(folders = folders) } }
        client.onHistoryCreateFolder = { folder, _ ->
            if (folder != null) {
                mutable.update { s ->
                    if (s.folders.any { it.id == folder.id }) return@update s
                    s.copy(folders = (s.folders + folder).sortedWith(compareBy(byName) { it.name }))
                }
            }
        }
    }

    override fun reset() {
        mutable.value = HistoryState()
        query = Query()
        generation = 0
        pending.clear()
        active = false
    }

    /** The socket was replaced: a lost reply can't wedge the paging queue; re-ask what's open. */
    override fun onConnectionReset() {
        pending.clear()
        mutable.update { it.copy(loadingMore = false) }
        if (active) {
            loadFirst(query.project, query.search, query.favoritesOnly, query.folder)
        } else {
            mutable.update { it.copy(loading = false) }
        }
    }

    fun loadFirst(project: String?, search: String?, favoritesOnly: Boolean, folder: String?) {
        active = true
        mutable.update { it.copy(loading = true, loadingMore = false) }
        query = Query(project, search, favoritesOnly, folder)
        pending.addLast(Pending(++generation, paging = false))
        client?.requestHistoryQuery(project, search, favoritesOnly, folder, before = null)
    }

    fun loadMore() {
        val s = state.value
        val last = s.items.lastOrNull() ?: return
        if (!s.hasMore || s.loading || s.loadingMore) return
        mutable.update { it.copy(loadingMore = true) }
        pending.addLast(Pending(++generation, paging = true))
        client?.requestHistoryQuery(
            query.project,
            query.search,
            query.favoritesOnly,
            query.folder,
            HistoryCursor(last.at, last.seq)
        )
    }

    /** The screen closed: its query is no longer live, and a straggler reply can't spin it. */
    fun screenDidClose() {
        active = false
        pending.clear()
        mutable.update { it.copy(loading = false, loadingMore = false) }
    }

    fun loadFolders() {
        client?.requestHistoryFolders()
    }

    fun saveDraft(message: String, project: String?, id: String?, label: String?) {
        client?.historySaveDraft(message, project, id, label, images = null)
    }

    fun toggleFavorite(id: String) {
        val item = state.value.items.firstOrNull { it.id == id }
        if (item != null) {
            val nowFavorite = !item.favorite
            // Unfavoriting while viewing Favorites drops the row.
            if (query.favoritesOnly && !nowFavorite) {
                removeItem(id)
            } else {
                updateItem(id) { it.copy(favorite = nowFavorite) }
            }
        }
        client?.historyToggleFavorite(id)
    }

    fun delete(id: String) {
        removeItem(id)
        client?.historyDelete(id)
    }

    fun setFolder(id: String, folder: String?) {
        if (state.value.items.any { it.id == id }) {
            val viewing = query.folder
            // Moving a message out of the folder being viewed drops the row.
            if (viewing != null && folder != viewing) {
                removeItem(id)
            } else {
                updateItem(id) { it.copy(folder = folder) }
            }
        }
        client?.historySetFolder(id, folder)
    }

    fun createFolder(name: String) {
        client?.historyCreateFolder(name)
    }

    fun deleteFolder(id: String) {
        mutable.update { s -> s.copy(folders = s.folders.filterNot { it.id == id }) }
        client?.historyDeleteFolder(id, name = null)
    }

    private fun applyPage(items: List<HistoryItem>, hasMore: Boolean) {
        val request = pending.removeFirstOrNull() ?: return
        // A superseded filter's reply is dropped; the newest one, after it in order,
        // clears the loading flags.
        if (request.generation != generation) return
        mutable.update {
            it.copy(
                items = if (request.paging) it.items + items else items,
                hasMore = hasMore,
                loading = false,
                loadingMore = false
            )
        }
    }

    private fun updateItem(id: String, change: (HistoryItem) -> HistoryItem) = mutable.update { s ->
        s.copy(items = s.items.map { if (it.id == id) change(it) else it })
    }

    private fun removeItem(id: String) = mutable.update { s ->
        s.copy(
            items = s.items.filterNot {
                it.id ==
                    id
            }
        )
    }

    private companion object {
        val byName: Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    }
}
