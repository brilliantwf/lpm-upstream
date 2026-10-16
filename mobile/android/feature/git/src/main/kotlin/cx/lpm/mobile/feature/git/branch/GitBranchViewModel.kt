package cx.lpm.mobile.feature.git.branch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.git.GitState
import cx.lpm.mobile.core.data.git.GitStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.GitBranch
import cx.lpm.mobile.core.navigation.GitBranchKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class GitBranchUiState(
    /** Null until the list loads. */
    val branches: List<GitBranch>? = null,
    val current: String = "",
    val error: String? = null,
    /** The branch being checked out. */
    val checkingOut: String? = null,
    val creating: Boolean = false,
    /** A switch went through: the sheet closes. */
    val switched: Boolean = false
) {
    val local: List<GitBranch> get() = branches.orEmpty().filterNot { it.isRemote }
    val remote: List<GitBranch> get() = branches.orEmpty().filter { it.isRemote }

    fun isCurrent(branch: GitBranch): Boolean = !branch.isRemote && branch.name == current
}

/**
 * "Switch Branch…" (iOS `GitBranchSheet`): the project's local branches, then
 * remote-only ones, the current one checked; tapping one checks it out, "+" creates
 * one off HEAD. A switch refreshes the snapshot and the project list.
 */
@HiltViewModel(assistedFactory = GitBranchViewModel.Factory::class)
class GitBranchViewModel @AssistedInject constructor(
    @Assisted key: GitBranchKey,
    sessions: MacSessions
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: GitBranchKey): GitBranchViewModel
    }

    private val project = key.project
    private val git: GitStore? = sessions[key.session]?.store()
    private val switched = MutableStateFlow(false)

    val uiState: StateFlow<GitBranchUiState> = combine(
        git?.state ?: flowOf(GitState()),
        switched
    ) { g, done ->
        GitBranchUiState(
            branches = g.branches[project],
            current = g.currentBranch[project] ?: g.snapshots[project]?.branch.orEmpty(),
            error = g.branchError[project],
            checkingOut = g.checkingOut[project],
            creating = project in g.creatingBranch,
            switched = done
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GitBranchUiState())

    init {
        git?.let { store ->
            if (store.state.value.branches[project] == null) store.loadBranches(project)
            viewModelScope.launch {
                store.state.map { it.checkoutTick[project] }.distinctUntilChanged().drop(1)
                    .collect { switched.value = true }
            }
        }
    }

    fun retry() {
        git?.loadBranches(project)
    }

    fun checkout(branch: GitBranch) {
        git?.checkout(project, branch.name, branch.remote)
    }

    fun createBranch(name: String) {
        git?.createBranch(project, name)
    }

    fun dismissError() {
        git?.dismissBranchError(project)
    }
}
