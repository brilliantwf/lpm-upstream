package cx.lpm.mobile.feature.git.auto

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.git.GitAutoState
import cx.lpm.mobile.core.data.git.GitAutoStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.GitAutoKind
import cx.lpm.mobile.core.model.GitAutoRun
import cx.lpm.mobile.core.navigation.GitAutoKey
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
data class GitAutoUiState(
    val run: GitAutoRun? = null,
    val switching: Boolean = false,
    val switchError: String? = null,
    /** "Switch to <base> and pull" went through: the sheet closes. */
    val switched: Boolean = false
)

/**
 * An Auto Commit / Auto Commit and Push / Auto Create PR run (iOS `GitAutoSheet`).
 * The Mac owns the run, so closing the sheet leaves it going; Stop cancels it.
 */
@HiltViewModel(assistedFactory = GitAutoViewModel.Factory::class)
class GitAutoViewModel @AssistedInject constructor(
    @Assisted private val key: GitAutoKey,
    sessions: MacSessions
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: GitAutoKey): GitAutoViewModel
    }

    private val project = key.project
    private val store: GitAutoStore? = sessions[key.session]?.store()
    private val switched = MutableStateFlow(false)

    val uiState: StateFlow<GitAutoUiState> = combine(
        store?.state ?: flowOf(GitAutoState()),
        switched
    ) { s, done ->
        GitAutoUiState(
            run = s.runs[project],
            switching = project in s.switching,
            switchError = s.switchError[project],
            switched = done
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GitAutoUiState(run = store?.state?.value?.runs?.get(project))
    )

    init {
        store?.let { s ->
            viewModelScope.launch {
                s.state.map { it.switchedTick[project] }.distinctUntilChanged().drop(1)
                    .collect { switched.value = true }
            }
        }
    }

    /** The run the menu asked for, or the one already going for the project. */
    fun start() {
        GitAutoKind.of(key.start)?.let { store?.start(project, it) }
    }

    fun appeared() {
        store?.sheetAppeared(project)
    }

    fun disappeared() {
        store?.sheetDisappeared(project)
    }

    fun stop() {
        store?.cancel(project)
    }

    fun retry() {
        val s = store ?: return
        s.state.value.runs[project]?.let { s.start(project, it.kind) }
    }

    fun switchToBase() {
        val s = store ?: return
        s.state.value.runs[project]?.let { s.switchToBase(project, it.base) }
    }

    fun dismissSwitchError() {
        store?.dismissSwitchError(project)
    }
}
