package cx.lpm.mobile.feature.notes.data

import cx.lpm.mobile.core.data.client.notesSearch
import cx.lpm.mobile.core.data.session.GenerationTimeout
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.model.NoteSearchHit
import cx.lpm.mobile.core.model.trimmingWhitespace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NotesSearchState(
    val hits: Map<String, List<NoteSearchHit>> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val error: Map<String, String> = emptyMap(),
    /** The query each project's results answer, so "no matches" differs from "not searched". */
    val query: Map<String, String> = emptyMap()
)

/**
 * The notes search half of [NotesStore]. A fast typist has several searches in flight
 * at once and they answer off a worker, so a reply is dropped unless it echoes the
 * query still being asked.
 */
class NotesSearch internal constructor(private val session: MacSession) {
    private val mutable = MutableStateFlow(NotesSearchState())
    val state: StateFlow<NotesSearchState> = mutable.asStateFlow()

    private val timeout = GenerationTimeout<String>(session.scope)

    fun search(project: String, query: String, limit: Int = DEFAULT_LIMIT) {
        val trimmed = query.trimmingWhitespace()
        if (trimmed.isEmpty()) {
            clear(project)
            return
        }
        mutable.update {
            it.copy(
                query = it.query + (project to trimmed),
                loading = it.loading + project,
                error = it.error - project
            )
        }
        session.client?.notesSearch(project, trimmed, limit)
        timeout.arm(project, TIMEOUT_MS) {
            if (project !in state.value.loading) return@arm
            mutable.update {
                it.copy(
                    loading = it.loading - project,
                    error = it.error + (project to "Search timed out. Try again.")
                )
            }
        }
    }

    fun clear(project: String) {
        timeout.cancel(project)
        mutable.update {
            it.copy(
                query = it.query - project,
                loading = it.loading - project,
                hits = it.hits - project,
                error = it.error - project
            )
        }
    }

    /** Drop a hit whose note was deleted, so results don't offer a dead row. */
    internal fun removeHit(project: String, id: String) = mutable.update { s ->
        val hits = s.hits[project] ?: return@update s
        s.copy(hits = s.hits + (project to hits.filterNot { it.id == id }))
    }

    internal fun apply(project: String, query: String, hits: List<NoteSearchHit>, error: String?) {
        if (state.value.query[project] != query) return
        timeout.cancel(project)
        mutable.update {
            val loading = it.loading - project
            if (error != null) {
                it.copy(loading = loading, error = it.error + (project to error))
            } else {
                val found = it.hits + (project to hits)
                it.copy(loading = loading, hits = found, error = it.error - project)
            }
        }
    }

    /** The socket was replaced: re-issue the open search, or drop its spinner. */
    internal fun handleConnectionReset(active: String?) {
        timeout.cancelAll()
        mutable.update { it.copy(loading = emptySet()) }
        val project = active ?: return
        val query = state.value.query[project] ?: return
        mutable.update { it.copy(query = it.query - project) }
        search(project, query)
    }

    internal fun reset() {
        timeout.cancelAll()
        mutable.value = NotesSearchState()
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val TIMEOUT_MS = 25_000L
    }
}
