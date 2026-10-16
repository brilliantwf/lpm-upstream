package cx.lpm.mobile.core.data.client

/**
 * Requests made while the link is down or half-dead, delivered on the next `ready`.
 * The app's network is cut soon after it backgrounds and the heartbeat can take a
 * while to notice, so the tap right after reopening (often the very send that
 * exposes the dead socket) would otherwise vanish. Live traffic (keystrokes,
 * resizes, subscriptions) is never queued: replaying stale input after a reconnect
 * is worse than dropping it.
 */
internal class PendingSends(private val onFull: () -> Unit) {
    private val queue = ArrayDeque<String>()

    val isEmpty: Boolean get() = queue.isEmpty()

    /** At the cap, drop the new send rather than evict an older (maybe important) one. */
    fun add(text: String) {
        if (queue.size >= MAX) return onFull()
        queue.addLast(text)
    }

    fun drain(): List<String> = queue.toList().also { queue.clear() }

    fun clear() = queue.clear()

    private companion object {
        const val MAX = 32
    }
}
