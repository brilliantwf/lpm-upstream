package cx.lpm.mobile.feature.projects.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.actions.ActionsStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.session.SessionAlerts
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.core.navigation.ProjectKey
import cx.lpm.mobile.feature.projects.visit.listedProject
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** A project's screen (iOS `ProjectDetail`): its tabs and services. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ProjectDetailViewModel.Factory::class)
class ProjectDetailViewModel @AssistedInject constructor(
    @Assisted val key: ProjectKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    private val session: MacSession? get() = sessions[key.session]
    private val projects: ProjectsStore? get() = session?.store()
    private val terminals: TerminalsStore? get() = session?.store()

    val uiState: StateFlow<ProjectDetailUiState> = sessions.session(key.session)
        .flatMapLatest { s -> s?.let(::detailState) ?: flowOf(ProjectDetailUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailUiState())

    private val sessionAlerts = (sessions[key.session] ?: sessions.live).alerts
    val alerts: StateFlow<SessionAlerts.Alerts> = sessionAlerts.alerts

    private fun detailState(s: MacSession) = combine(
        combine(s.state, s.link.views, ::Pair),
        s.store<ProjectsStore>().state,
        s.store<TerminalsStore>().state,
        s.store<ActionsStore>().state,
        sessions.listedProject(key)
    ) { (session, view), p, t, a, listed ->
        p.detail(
            name = key.project,
            terminals = t.terminals[key.project],
            creating = key.project in t.creatingTerminals,
            hasBackgroundRuns = a.runs(key.project).isNotEmpty(),
            subtitleWhenLive = session.demoMode || session.isReady,
            linkText = view.statusLine.text,
            now = clock.millis(),
            listed = listed
        )
    }

    /** The screen came up: ask for its tabs again (a switch or reconnect clears them). */
    fun load() {
        terminals?.loadTerminals(key.project)
    }

    fun newTerminal() {
        terminals?.newTerminal(key.project)
    }

    /** A terminal an action spawned belongs to the desktop: claim it before showing it. */
    fun claimControl(id: String) {
        terminals?.claimControl(id)
    }

    fun pin(id: String) {
        terminals?.pinTerminal(key.project, id)
    }

    fun close(id: String) {
        terminals?.closeTerminal(key.project, id)
    }

    fun rename(id: String, label: String) {
        val name = label.trimmingWhitespace()
        if (name.isNotEmpty()) terminals?.renameTerminal(key.project, id, name)
    }

    fun toggleService(service: String) {
        projects?.toggleService(key.project, service)
    }

    fun stopAll() {
        val project = uiState.value.project ?: return
        projects?.stopProject(project)
    }

    suspend fun refreshProjects() {
        projects?.refresh()
    }

    fun dismissError() = sessionAlerts.dismissError()

    fun dismissNotice() = sessionAlerts.dismissNotice()

    @AssistedFactory
    interface Factory {
        fun create(key: ProjectKey): ProjectDetailViewModel
    }
}
