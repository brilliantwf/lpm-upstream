package cx.lpm.mobile.feature.actions.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.actions.ActionsState
import cx.lpm.mobile.core.data.actions.ActionsStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BackgroundRunUiState(
    val text: String = "",
    val status: RunStatus = RunStatus.Starting,
    /** Until its first poll a just-started run counts as running; a reaped one doesn't. */
    val running: Boolean = true
)

/** One background run's live output (iOS `BackgroundRunSheet`), polled while it runs. */
@HiltViewModel(assistedFactory = BackgroundRunViewModel.Factory::class)
class BackgroundRunViewModel @AssistedInject constructor(
    @Assisted("project") private val project: String,
    @Assisted("runId") private val runId: String,
    @Assisted private val sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val store: ActionsStore? = sessions[sessionKey]?.store()

    val uiState: StateFlow<BackgroundRunUiState> = (store?.state ?: flowOf(ActionsState()))
        .map { s ->
            BackgroundRunUiState(
                text = s.outputs[runId]?.text.orEmpty(),
                status = RunStatus.of(s, runId),
                running = s.isRunning(runId)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackgroundRunUiState())

    /** Poll the Mac every 2 s while the run goes; returns once it ends. */
    suspend fun follow() {
        store?.follow(project, runId)
    }

    fun stop() {
        store?.cancel(runId)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("project") project: String,
            @Assisted("runId") runId: String,
            sessionKey: SessionKey
        ): BackgroundRunViewModel
    }
}
