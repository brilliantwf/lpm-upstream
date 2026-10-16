package cx.lpm.mobile.core.data.client

import java.io.IOException

// Getting frames to the Mac: reliable requests queue while the link is down and
// flush on the next `ready`; live traffic is dropped instead of replayed stale.

/**
 * Reliable request send: queued while the link isn't ready, re-queued when the
 * socket turns out to be dead, flushed on the next `ready`.
 */
internal fun LpmClient.send(text: String) {
    demoServer?.let { return it.receive(text) }
    if (socket == null || state != LpmClient.State.Ready) return enqueue(text)
    transmit(text, requeueOnFailure = true)
}

/** Fire-and-forget live traffic (keystrokes, resizes, sub/unsub): never replayed. */
internal fun LpmClient.sendLive(text: String) {
    demoServer?.let { return it.receive(text) }
    if (socket == null || state != LpmClient.State.Ready) return
    transmit(text, requeueOnFailure = false)
}

internal fun LpmClient.transmit(text: String, requeueOnFailure: Boolean) {
    demoServer?.let { return it.receive(text) }
    val s = socket
    if (s == null) {
        if (requeueOnFailure) enqueue(text)
        return
    }
    if (s.send(text)) return
    post {
        if (requeueOnFailure) enqueue(text)
        if (s === socket) transientFailure(IOException("send failed"))
    }
}

private fun LpmClient.enqueue(text: String) {
    if (wantConnected) pendingSends.add(text)
}

internal fun LpmClient.flushPending() {
    if (state != LpmClient.State.Ready || pendingSends.isEmpty) return
    pendingSends.drain().forEach { transmit(it, requeueOnFailure = true) }
}
