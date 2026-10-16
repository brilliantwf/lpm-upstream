package cx.lpm.mobile.core.data.terminals

import cx.lpm.mobile.core.model.ControlOwner
import cx.lpm.mobile.core.model.HistoryRow
import cx.lpm.mobile.core.model.MentionEntry
import cx.lpm.mobile.core.model.SlashCommand
import cx.lpm.mobile.core.model.TerminalInfo

/**
 * How a terminal ended: its process exited with `code`, or the Mac no longer has it
 * at all (lpm restarted, or the exit itself never reached the phone).
 */
data class TerminalEnding(val code: Int, val gone: Boolean) {
    val message: String
        get() = when {
            gone -> "This terminal is no longer running"
            code == 0 -> "Process exited"
            else -> "Process exited with code $code"
        }
}

data class TerminalsState(
    /** project → its terminals, in the desktop's tab order. */
    val terminals: Map<String, List<TerminalInfo>> = emptyMap(),
    /** terminal id → its slash commands. */
    val slashCommands: Map<String, List<SlashCommand>> = emptyMap(),
    /** project → @-mention targets. */
    val mentions: Map<String, List<MentionEntry>> = emptyMap(),
    /** project → recently sent prompts (drafts left out). */
    val history: Map<String, List<HistoryRow>> = emptyMap(),
    /**
     * terminal id → who renders it live. Absent = nobody/unknown, so this phone may
     * show it; when the desktop or another phone owns it, this one shows a
     * "take control" placeholder.
     */
    val controlOwner: Map<String, ControlOwner> = emptyMap(),
    /** terminal id → how it ended, for screens still showing its last output. */
    val endedTerminals: Map<String, TerminalEnding> = emptyMap(),
    /** Projects with a new terminal or run action in flight: show a placeholder row. */
    val creatingTerminals: Set<String> = emptySet()
) {
    /** project → terminal id → tab name, for naming agent rows on Activity. */
    val terminalTitles: Map<String, Map<String, String>>
        get() = terminals.mapValues { (_, list) -> list.associate { it.id to it.label } }
}
