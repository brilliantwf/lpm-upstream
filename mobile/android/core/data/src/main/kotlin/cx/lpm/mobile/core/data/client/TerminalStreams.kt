package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.protocol.Wire
import okio.utf8Size

/**
 * The terminals a client is subscribed to and how far into each output stream the
 * phone has applied, so a re-subscribe (reconnect, unlock, dropped frames) asks for
 * the missed slice instead of a full screen replay — which resets the emulator and
 * can only approximate what a running full-screen program thinks it drew. A
 * terminal whose Mac doesn't stamp offsets (older desktop, Demo Mode) has none.
 */
internal class TerminalStreams(private val sendLive: (String) -> Unit) {
    private val subscribed = linkedSetOf<String>()
    private val streamOffset = mutableMapOf<String, Long>()

    // Terminals with a `sub` in flight. Its reply carries everything the desktop had
    // queued for us, so output arriving before it would apply twice; hold it until
    // the seed lands. This also collapses a burst of gap-triggered resyncs.
    private val awaitingSeed = mutableSetOf<String>()

    fun subscribe(id: String) {
        subscribed += id
        streamOffset.remove(id)
        awaitingSeed += id
        sendLive(Wire.sub(id))
    }

    fun resync(id: String) {
        if (id !in subscribed || id in awaitingSeed) return
        awaitingSeed += id
        sendLive(Wire.sub(id, streamOffset[id]))
    }

    fun unsubscribe(id: String) {
        subscribed -= id
        streamOffset.remove(id)
        awaitingSeed -= id
        sendLive(Wire.unsub(id))
    }

    /** A new connection: resume every terminal from where its screen got to. */
    fun resumeAll() {
        awaitingSeed.clear()
        subscribed.toList().forEach(::resync)
    }

    fun seeded(id: String, off: Long?) {
        if (off != null) streamOffset[id] = off else streamOffset.remove(id)
        awaitingSeed -= id
    }

    fun ended(id: String) {
        streamOffset.remove(id)
        awaitingSeed -= id
    }

    /**
     * Whether an output chunk applies. Anything the pending seed will carry is
     * dropped, and a chunk starting past where we are means output went missing —
     * the desktop drops queued output for a phone that falls behind — so ask for
     * the gap instead of applying an update the screen isn't ready for.
     */
    fun accept(id: String, data: String, off: Long?): Boolean {
        if (id in awaitingSeed) return false
        val known = streamOffset[id]
        if (off != null && known != null) {
            if (off <= known) return false
            if (off - data.utf8Size() != known) {
                resync(id)
                return false
            }
        }
        if (off != null) streamOffset[id] = off
        return true
    }
}
