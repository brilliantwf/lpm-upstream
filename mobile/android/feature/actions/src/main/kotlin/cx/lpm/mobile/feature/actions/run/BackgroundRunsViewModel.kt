package cx.lpm.mobile.feature.actions.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.actions.ActionsState
import cx.lpm.mobile.core.data.actions.ActionsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.BackgroundRunInfo
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of a project's "Background runs": the run and where it stands. */
data class BackgroundRunRow(val run: BackgroundRunInfo, val status: RunStatus, val running: Boolean)

/**
 * A project's background runs on its screen (iOS `ProjectDetail`'s section): the runs
 * this phone started or re-attached to, newest first, kept current while shown.
 */
@HiltViewModel(assistedFactory = BackgroundRunsViewModel.Factory::class)
class BackgroundRunsViewModel @AssistedInject constructor(
    @Assisted private val project: String,
    @Assisted private val sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val session: MacSession? = sessions[sessionKey]
    private val store: ActionsStore? = session?.store()

    val uiState: StateFlow<List<BackgroundRunRow>> = (store?.state ?: flowOf(ActionsState()))
        .map { s ->
            s.runs(project).map {
                BackgroundRunRow(it, RunStatus.of(s, it.runId), s.isRunning(it.runId))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * While the section shows: ask the Mac for the project's runs (again after every
     * reconnect, to re-attach to one started before), and poll each running one.
     */
    suspend fun keepCurrent() {
        val s = session ?: return
        val actions = store ?: return
        actions.loadRuns(project)
        coroutineScope {
            launch {
                s.state.map { it.isReady }.distinctUntilChanged().drop(1).filter { it }
                    .collect { actions.loadRuns(project) }
            }
            actions.watch(project)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(project: String, sessionKey: SessionKey): BackgroundRunsViewModel
    }
}
