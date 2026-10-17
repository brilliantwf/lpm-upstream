package cx.lpm.mobile.feature.projects.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.services.ServiceLogsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.endedServices
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ServiceLogsUiState(
    val running: Boolean = false,
    /** How it ended, while its pane and so its logs are still open; null otherwise. */
    val ended: Service? = null,
    /** A Start/Stop is out for the service. */
    val pending: Boolean = false,
    /** Its pane, once the services discovery found it (null while stopped). */
    val paneIndex: Int? = null,
    val ready: Boolean = false,
    val text: String = "",
    val error: String? = null
)

/**
 * One service's recent output (iOS `ServiceLogsSheet`), while it runs or after its
 * command ended with its pane still open: watched while the sheet is open on a Mac
 * that pushes it, polled every few seconds on one that doesn't, and its pane
 * rediscovered until it turns up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ServiceLogsViewModel.Factory::class)
class ServiceLogsViewModel @AssistedInject constructor(
    @Assisted("project") private val project: String,
    @Assisted("service") private val service: String,
    @Assisted private val sessionKey: SessionKey,
    private val sessions: MacSessions
) : ViewModel() {
    private val session: MacSession? get() = sessions[sessionKey]

    val uiState: StateFlow<ServiceLogsUiState> = sessions.session(sessionKey)
        .flatMapLatest { s -> s?.let(::logsState) ?: flowOf(ServiceLogsUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceLogsUiState())

    private fun logsState(s: MacSession) = combine(
        s.state,
        s.store<ProjectsStore>().state,
        s.store<ServiceLogsStore>().state
    ) { session, p, logs ->
        val pane = p.services[project]?.firstOrNull { it.name == service }?.paneIndex
        val key = pane?.let { s.store<ServiceLogsStore>().key(project, it) }
        ServiceLogsUiState(
            running = p.serviceRunning(project, service),
            ended = p.project(project)?.endedServices?.get(service),
            pending = p.pendingServiceToggle[project]?.get(service) != null,
            paneIndex = pane,
            ready = session.isReady,
            text = key?.let(logs.results::get).orEmpty(),
            error = key?.let(logs.errors::get)
        )
    }

    /**
     * Keeps the output coming while the sheet is up: restarts as the service starts
     * or stops, its pane is found, or the link comes back — the Mac forgets a dropped
     * connection's watch.
     */
    suspend fun follow() {
        uiState.map { Feed(it.running || it.ended != null, it.paneIndex, it.ready) }
            .distinctUntilChanged()
            .collectLatest(::stream)
    }

    private suspend fun stream(feed: Feed) {
        val s = session ?: return
        if (!feed.hasPane || !feed.ready) return
        val pane = feed.paneIndex
        val logs = s.store<ServiceLogsStore>()
        when {
            pane == null -> while (true) {
                s.store<ProjectsStore>().loadServices(project)
                delay(POLL_MS)
            }

            logs.canWatch -> {
                logs.watch(project, pane, true)
                try {
                    awaitCancellation()
                } finally {
                    logs.watch(project, pane, false)
                }
            }

            else -> while (true) {
                logs.fetch(project, pane)
                delay(POLL_MS)
            }
        }
    }

    fun toggle() {
        session?.store<ProjectsStore>()?.toggleService(project, service)
    }

    fun restart() {
        session?.store<ProjectsStore>()?.restartService(project, service)
    }

    /** The sheet went: its output was a one-off. */
    fun consume() {
        val pane = uiState.value.paneIndex ?: return
        val logs = session?.store<ServiceLogsStore>() ?: return
        logs.consume(logs.key(project, pane))
    }

    private data class Feed(val hasPane: Boolean, val paneIndex: Int?, val ready: Boolean)

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("project") project: String,
            @Assisted("service") service: String,
            sessionKey: SessionKey
        ): ServiceLogsViewModel
    }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
