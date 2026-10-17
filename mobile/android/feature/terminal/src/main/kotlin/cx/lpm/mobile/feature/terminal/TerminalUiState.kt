package cx.lpm.mobile.feature.terminal

import cx.lpm.mobile.core.data.link.LinkStatusLine
import cx.lpm.mobile.core.data.terminals.TerminalEnding
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.feature.terminal.settings.TerminalLook

data class TerminalUiState(
    val terminal: TerminalInfo? = null,
    val project: Project? = null,
    /** The project's terminal list has arrived: a missing terminal is gone, not loading. */
    val listLoaded: Boolean = false,
    /** This phone renders the terminal live; otherwise "take control" shows. */
    val controlled: Boolean = true,
    val ownerLabel: String = "",
    val ended: TerminalEnding? = null,
    /** lpm restarted and this is the tab it reopened in the old one's place: say so for a moment. */
    val reopened: Boolean = false,
    val connectionReady: Boolean = true,
    /** Replaces the agent's timing under the title while the Mac isn't connected. */
    val statusLine: LinkStatusLine? = null,
    val look: TerminalLook = TerminalLook(),
    /** How long the tab's agent has been at it; null for a plain shell. */
    val agent: AgentTiming? = null,
    /** Changes on a Mac switch, when a missing terminal is asked for again. */
    val activeMacId: String? = null
) {
    /** The composer shows for a terminal this phone drives and whose process lives. */
    val showsComposer: Boolean get() = controlled && ended == null
}

/** Millis: when the agent's reading counts from, and when it froze once the turn ended. */
data class AgentTiming(val since: Long, val until: Long?)
