package cx.lpm.mobile.core.data.session

import cx.lpm.mobile.core.data.client.LpmClient

/**
 * One area of a session's state (an iOS `AppModel` area or `*Store`). Every
 * [MacSession] — the live one and each machine visit — builds its own instance of
 * every store from the [Factory]s contributed with Hilt `@IntoSet`, wires each new
 * client into it and resets it when the session starts over.
 */
interface SessionStore {
    /** Assign the client callbacks this area owns (`c.onX = …`). */
    fun wire(client: LpmClient)

    /** Back to the initial values: the session switched Macs, paired, or left Demo Mode. */
    fun reset()

    /** The link reached `ready` (after the session's own first requests went out). */
    fun onReady(client: LpmClient) = Unit

    /**
     * The live socket dropped: a request awaiting a reply is dead (the reply can't
     * survive the reconnect), so drop spinners or re-issue what an open screen needs.
     * Requests made now queue on the client until it reconnects.
     */
    fun onConnectionReset() = Unit

    /** Builds this store for one session. */
    fun interface Factory {
        fun create(session: MacSession): SessionStore
    }
}
