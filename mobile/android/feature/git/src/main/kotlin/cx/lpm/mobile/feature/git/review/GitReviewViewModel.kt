package cx.lpm.mobile.feature.git.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.git.GitDiffKey
import cx.lpm.mobile.core.data.git.GitState
import cx.lpm.mobile.core.data.git.GitStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.terminals.TerminalsState
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.navigation.GitReviewKey
import cx.lpm.mobile.feature.git.di.DiffDispatcher
import cx.lpm.mobile.feature.git.diff.ParsedDiff
import cx.lpm.mobile.feature.git.diff.promptDiff
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the review screen keeps itself, as SwiftUI `@State`. */
private data class ReviewLocal(
    val message: String = "",
    // Files the user unchecked: everything else stays selected, so new changes are
    // picked up and deselections survive a refresh.
    val deselected: Set<String> = emptySet(),
    val collapsed: Map<String, Boolean> = emptyMap(),
    val expanded: Set<String> = emptySet()
)

/**
 * A project's uncommitted changes (iOS `GitReviewView`): the snapshot and every
 * file's diff from [GitStore], parsed here off the main thread, the commit form,
 * and "Ask agent…" into one of the project's terminals.
 */
@HiltViewModel(assistedFactory = GitReviewViewModel.Factory::class)
class GitReviewViewModel @AssistedInject constructor(
    @Assisted key: GitReviewKey,
    sessions: MacSessions,
    @DiffDispatcher parseDispatcher: CoroutineDispatcher
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: GitReviewKey): GitReviewViewModel
    }

    private val project = key.project
    private val session = sessions[key.session]
    private val git: GitStore? = session?.store()
    private val terminals: TerminalsStore? = session?.store()
    private val local = MutableStateFlow(ReviewLocal())
    private val parses = DiffParses(viewModelScope, parseDispatcher)

    val uiState: StateFlow<GitReviewUiState> = combine(
        git?.state ?: flowOf(GitState()),
        terminals?.state ?: flowOf(TerminalsState()),
        local,
        parses.parsed
    ) { g, t, l, p -> build(g, t, l, p) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GitReviewUiState(project = project)
        )

    init {
        git?.let { store ->
            viewModelScope.launch {
                store.state.map { s ->
                    s.diffs.filterKeys { it.project == project }.mapKeys { it.key.path }
                }.distinctUntilChanged().collect(parses::update)
            }
            viewModelScope.launch {
                store.state.map { it.generatedMessage[project] }.distinctUntilChanged()
                    .filterNotNull().collect { message ->
                        local.update { it.copy(message = message) }
                        store.consumeGeneratedMessage(project)
                    }
            }
            viewModelScope.launch {
                store.state.map { it.commitTick[project] }.distinctUntilChanged().drop(1).collect {
                    local.update { it.copy(message = "", deselected = emptySet()) }
                }
            }
        }
    }

    private fun build(
        g: GitState,
        t: TerminalsState,
        l: ReviewLocal,
        parsed: Map<String, ParsedDiff>
    ): GitReviewUiState {
        val snapshot = g.snapshots[project]
        val files = snapshot?.files.orEmpty()
        val review = files.map { file ->
            val key = GitDiffKey(project, file.path)
            val viewed = g.isViewed(project, file.path)
            ReviewFile(
                file = file,
                result = g.diffs[key],
                error = g.diffError[key],
                parsed = parsed[file.path],
                selected = file.path !in l.deselected,
                viewed = viewed,
                collapsed = l.collapsed[file.path] ?: viewed,
                expanded = file.path in l.expanded
            )
        }
        val selected = review.count { it.selected }
        val committing = project in g.committing
        val loading = project in g.loading
        val overlay = when {
            snapshot != null -> if (snapshot.isRepo) null else ReviewOverlay.NotRepo
            g.loadError[project] != null && !loading -> ReviewOverlay.Unreachable
            else -> ReviewOverlay.Skeleton
        }
        return GitReviewUiState(
            project = project,
            overlay = overlay,
            loadError = g.loadError[project],
            rows = if (snapshot?.isRepo ==
                true
            ) {
                reviewRows(review, snapshot.ghCli)
            } else {
                emptyList()
            },
            files = if (snapshot?.isRepo == true) files else emptyList(),
            added = review.sumOf { it.parsed?.addedCount ?: 0 },
            removed = review.sumOf { it.parsed?.removedCount ?: 0 },
            message = l.message,
            selectedCount = selected,
            generating = project in g.generatingMessage,
            committing = committing,
            canCommit = selected > 0 && l.message.isNotBlank() && !committing &&
                project !in g.pushing,
            opError = g.opError[project],
            notice = g.notice[project],
            terminals = t.terminals[project].orEmpty()
        )
    }

    private val selectedPaths: List<String>
        get() {
            val deselected = local.value.deselected
            val files = git?.state?.value?.snapshots?.get(project)?.files.orEmpty()
            return files.map { it.path }.filterNot { it in deselected }
        }

    /** The screen came up: load if nothing has, and follow the working tree while it's open. */
    fun appear() {
        val store = git ?: return
        if (store.state.value.snapshots[project] == null) store.load(project)
        store.watch(project)
    }

    fun disappear() {
        git?.unwatch(project)
    }

    /** Back in the foreground: catch up on what changed meanwhile. */
    fun resume() {
        git?.refreshWatched(project)
    }

    fun load() {
        git?.load(project)
    }

    /** Pull to refresh, held long enough for the spinner to read. */
    suspend fun refresh() {
        git?.load(project)
        delay(REFRESH_HOLD_MS)
    }

    /** A file came into view: fetch its diff if nothing has yet. */
    fun fileAppeared(path: String) {
        val store = git ?: return
        val key = GitDiffKey(project, path)
        val s = store.state.value
        if (s.diffs[key] == null && key !in s.diffLoading) store.loadDiff(project, path)
    }

    fun retryDiff(path: String) {
        git?.loadDiff(project, path)
    }

    fun toggleSelected(path: String) = local.update {
        it.copy(
            deselected = if (path in
                it.deselected
            ) {
                it.deselected - path
            } else {
                it.deselected + path
            }
        )
    }

    fun toggleCollapsed(path: String) {
        val viewed = git?.state?.value?.isViewed(project, path) == true
        local.update {
            it.copy(collapsed = it.collapsed + (path to !(it.collapsed[path] ?: viewed)))
        }
    }

    /** Marking a file viewed collapses it; unmarking opens it again. */
    fun toggleViewed(path: String) {
        val store = git ?: return
        val nowViewed = !store.state.value.isViewed(project, path)
        store.toggleViewed(project, path)
        local.update { it.copy(collapsed = it.collapsed + (path to nowViewed)) }
    }

    fun showAll(path: String) = local.update { it.copy(expanded = it.expanded + path) }

    fun setMessage(message: String) = local.update { it.copy(message = message) }

    fun generateMessage() {
        git?.genMessage(project, selectedPaths)
    }

    fun commit() {
        git?.commit(project, local.value.message.trim(), selectedPaths)
    }

    fun dismissOpError() {
        git?.dismissOpError(project)
    }

    /** "Ask agent…" about one file: its diff without the file-header noise. */
    fun askContext(path: String): AskContext {
        val result = git?.state?.value?.diffs?.get(GitDiffKey(project, path))
        val diff = if (result == null || result.binary) "" else promptDiff(result.diff)
        return AskContext(path, diff)
    }

    /** Hand [prompt] to [terminal]: it goes in once the terminal's screen has seeded. */
    fun sendToAgent(terminal: TerminalInfo, prompt: String) {
        terminals?.recordHistory(project, terminal.id, terminal.label, prompt)
        terminals?.queuePrompt(terminal.id, prompt)
    }

    private companion object {
        const val REFRESH_HOLD_MS = 600L
    }
}
