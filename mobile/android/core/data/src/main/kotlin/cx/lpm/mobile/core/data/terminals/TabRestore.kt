package cx.lpm.mobile.core.data.terminals

import cx.lpm.mobile.core.model.TerminalInfo

// lpm reopens a Mac's tabs under new ids when it starts again. A terminal open on
// the phone across that restart ends, its process gone with the old lpm, while the
// same tab is back in the project's list. These tell that apart from a real exit
// and find the tab that took the old one's place.

/** How long before the link dropped an ending still counts as lpm quitting. */
private const val QUIT_WINDOW_MS = 10_000L

/** A terminal ended at [now]; a second report only adds that the Mac no longer has it. */
internal fun TerminalsState.recordingEnding(
    id: String,
    code: Int,
    gone: Boolean,
    now: Long
): TerminalsState {
    val earlier = endedTerminals[id]
    val ending = if (earlier != null) {
        earlier.copy(gone = earlier.gone || gone)
    } else {
        val listed = terminals.values.firstNotNullOfOrNull { list ->
            list.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { list[it].label to it }
        }
        TerminalEnding(code, gone, label = listed?.first, index = listed?.second, at = now)
    }
    return copy(endedTerminals = endedTerminals + (id to ending))
}

/** The link dropped at [now]: terminals that ended just before it went down with lpm. */
internal fun TerminalsState.lostWithLink(now: Long): TerminalsState = copy(
    endedTerminals = endedTerminals.mapValues { (_, ending) ->
        if (!ending.restarted && now - ending.at < QUIT_WINDOW_MS) {
            ending.copy(restarted = true)
        } else {
            ending
        }
    }
)

/**
 * The tab lpm reopened in place of [term], once the project's list shows it: same
 * name, a new id, and when several share the name, the one in the old tab's place.
 */
fun TerminalsState.restoredTab(term: TerminalInfo): TerminalInfo? {
    val ending = endedTerminals[term.id]?.takeIf { it.replaceable } ?: return null
    val list = terminals[term.project] ?: return null
    if (list.any { it.id == term.id }) return null
    val label = ending.label ?: term.label
    val named = list.filter { it.label == label }
    val index = ending.index
    if (named.size > 1 && index != null && index in list.indices && list[index].label == label) {
        return list[index]
    }
    return named.firstOrNull()
}
