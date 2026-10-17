package cx.lpm.mobile.core.data.push

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore

/**
 * The live session's push wiring: register when the link comes up, and note the
 * Mac's ack. A machine visit leaves push alone — the Mac it goes through is the one
 * that knows this phone.
 */
internal class PushStore(private val session: MacSession, private val registrar: PushRegistrar) :
    SessionStore {
    init {
        if (!session.isVisit) registrar.attach(session)
    }

    override fun wire(client: LpmClient) {
        if (session.isVisit) return
        client.onPushToken = registrar::liveAcknowledged
    }

    override fun reset() = Unit

    override fun onReady(client: LpmClient) {
        if (!session.isVisit) registrar.sync()
    }
}
