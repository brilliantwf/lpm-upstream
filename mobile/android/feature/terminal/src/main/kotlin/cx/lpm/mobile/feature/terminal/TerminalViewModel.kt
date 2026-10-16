package cx.lpm.mobile.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.LinkView
import cx.lpm.mobile.core.data.link.view
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.projects.ProjectsState
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.session.SessionState
import cx.lpm.mobile.core.data.terminals.TerminalHooks
import cx.lpm.mobile.core.data.terminals.TerminalsState
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.model.AgentState
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.model.agentRowsByTerminal
import cx.lpm.mobile.core.model.projectAgentRows
import cx.lpm.mobile.core.navigation.TerminalKey
import cx.lpm.mobile.feature.terminal.settings.TerminalLook
import cx.lpm.mobile.feature.terminal.settings.terminalLook
import cx.lpm.mobile.feature.terminal.settings.terminalLooks
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * One terminal: its stream into the web view (xterm.js renders what the Mac
 * streams and posts keystrokes back), who controls it, and how it ended.
 */
@HiltViewModel(assistedFactory = TerminalViewModel.Factory::class)
class TerminalViewModel @AssistedInject constructor(
    @Assisted private val key: TerminalKey,
    sessions: MacSessions,
    prefs: AppPreferences,
    private val clock: Clock
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: TerminalKey): TerminalViewModel
    }

    private val session: MacSession? = sessions[key.session]
    private val terminals = session?.store<TerminalsStore>()
    private val projects = session?.store<ProjectsStore>()

    val uiState: StateFlow<TerminalUiState> = if (session == null || terminals == null ||
        projects == null
    ) {
        // A machine visit that has ended: nothing will load.
        MutableStateFlow(TerminalUiState(listLoaded = true))
    } else {
        combine(
            session.state,
            terminals.state,
            projects.state,
            session.link.views,
            prefs.terminalLooks()
        ) { s, t, p, link, look -> build(s, t, p, link, look) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                build(
                    session.state.value,
                    terminals.state.value,
                    projects.state.value,
                    session.link.view(),
                    prefs.terminalLook
                )
            )
    }

    private fun build(
        s: SessionState,
        t: TerminalsState,
        p: ProjectsState,
        link: LinkView,
        look: TerminalLook
    ): TerminalUiState {
        val list = t.terminals[key.project]
        val terminal = list?.firstOrNull { it.id == key.terminalId }
        val project = p.project(key.project)
        return TerminalUiState(
            terminal = terminal,
            project = project,
            listLoaded = list != null,
            controlled = terminals?.isControlled(key.terminalId) ?: true,
            ownerLabel = terminals?.controlOwnerLabel(key.terminalId).orEmpty(),
            ended = t.endedTerminals[key.terminalId],
            connectionReady = s.isReady,
            statusLine = link.statusLine.takeUnless { s.demoMode || s.isReady },
            look = look,
            agent = agentTiming(project, key.terminalId),
            activeMacId = s.activeMacId
        )
    }

    /** The tab's agent, as the desktop's sidebar reads it; a plain shell or an idle one has none. */
    private fun agentTiming(project: Project?, terminalId: String): AgentTiming? {
        project ?: return null
        val rows = projectAgentRows(project, clock.millis(), tabTitles = emptyMap())
        val agent = agentRowsByTerminal(rows)[terminalId]?.takeIf { it.state != AgentState.Idle }
        return agent?.since?.let { AgentTiming(it, agent.until) }
    }

    /** A screen showing the terminal appeared; it streams through `hooks` until [closeScreen]. */
    fun openScreen(screen: Any, hooks: TerminalHooks) {
        terminals?.openTerminalScreen(key.terminalId, screen, hooks)
    }

    fun closeScreen(screen: Any) {
        terminals?.closeTerminalScreen(key.terminalId, screen)
    }

    fun input(data: String) {
        terminals?.input(key.terminalId, data)
    }

    fun resize(cols: Int, rows: Int) {
        terminals?.resize(key.terminalId, cols, rows)
    }

    /** Back from the background: ask for the output missed meanwhile. */
    fun resync() {
        terminals?.resync(key.terminalId)
    }

    /** The terminal's first content is on screen: a prompt "Ask agent…" queued goes in. */
    fun contentShown() {
        terminals?.contentShown(key.terminalId)
    }

    /** The page restarted empty: ask for a full snapshot. */
    fun reseed() {
        terminals?.reseed(key.terminalId)
    }

    fun claimControl() {
        terminals?.claimControl(key.terminalId)
    }

    /**
     * A terminal an action spawned from this screen belongs to the desktop that
     * opened it; claim it for this phone before showing it, so it renders live.
     */
    fun claimSpawned(terminal: TerminalInfo) {
        terminals?.claimControl(terminal.id)
    }

    /** Someone is looking at the terminal: retire the finish or problem it shows. */
    fun markSeen() {
        projects?.markTerminalSeen(key.project, key.terminalId)
    }

    fun loadTerminals() {
        terminals?.loadTerminals(key.project)
    }
}
