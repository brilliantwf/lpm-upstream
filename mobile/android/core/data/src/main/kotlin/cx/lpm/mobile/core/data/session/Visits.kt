package cx.lpm.mobile.core.data.session

import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.model.RemoteMachine
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A project of one of the live Mac's machines, or one of its terminals, opened in place. */
data class MachineVisit(val slug: String, val project: String, val terminal: String? = null) {
    val id: String get() = listOf(slug, project, terminal.orEmpty()).joinToString("\n")

    /** The visit's session, for the screens it opens. */
    val session: SessionKey get() = SessionKey.Visit(slug)
}

/**
 * Opening a machine's project keeps the live Mac connected: the machine gets a
 * session of its own — on its own saved entry, or through the live Mac — that lasts
 * while its screens are open and a little after, so hopping between its projects
 * doesn't reconnect each time.
 */
internal class Visits(private val s: MacSession, val host: MacSession?) {
    private var linger: Job? = null

    fun beginVisit(
        machine: RemoteMachine,
        record: MacRecord?,
        project: String,
        terminal: String?
    ): MachineVisit? {
        if (s.isVisit) return null
        val target =
            record ?: s.savedMacs.recordThroughLiveMac(machine, saving = false) ?: return null
        linger?.cancel()
        linger = null
        val key = SessionKey.Visit(machine.slug)
        val current = s.visitFlow.value
        if (current != null &&
            (current.key != key || !current.visits.isVisiting(target))
        ) {
            endVisit()
        }
        if (s.visitFlow.value == null) s.visitFlow.value = visit(target, key)
        return MachineVisit(machine.slug, project, terminal)
    }

    /** The visit's screens closed: end its session unless one opens again soon. */
    fun leaveVisit() {
        if (s.visitFlow.value == null) return
        linger?.cancel()
        linger = s.scope.launch {
            delay(LINGER_MS)
            endVisit()
        }
    }

    fun endVisit() {
        linger?.cancel()
        linger = null
        val session = s.visitFlow.value ?: return
        s.visitFlow.value = null
        session.resetSessionState()
    }

    private fun isVisiting(record: MacRecord): Boolean {
        val current = s.state.value.activeRecord ?: return false
        return current.localId == record.localId ||
            (record.via != null && current.via == record.via)
    }

    private fun visit(record: MacRecord, key: SessionKey): MacSession {
        val session = MacSession(s.env, visitHost = s, key = key)
        val macs = s.state.value.macs
        val saved = macs.any { it.localId == record.localId }
        session.mutableState.update {
            it.copy(macs = if (saved) macs else macs + record, activeMacId = record.localId)
        }
        session.setInForeground(s.inForeground)
        session.activeCredential()?.let(session::connectBest)
        return session
    }

    private companion object {
        const val LINGER_MS = 60_000L
    }
}
