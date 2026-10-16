package cx.lpm.mobile.core.data.link

import kotlinx.coroutines.flow.StateFlow

/**
 * Browses the local network for Macs running lpm (`_lpm._tcp`). Each advertises its
 * stable identity, name and a dev-build flag in TXT records; resolving one yields an
 * address the phone can connect to. Browsing is scoped: [start] only while the
 * pairing screen is up or a recovery attempt runs, [stop] the moment it isn't.
 */
interface MacBrowser {
    /**
     * One advertised Mac. `serverId` is matched against saved records; `requestPair`
     * is false for a headless host (Linux) that can't show the approve-on-Mac dialog.
     */
    data class DiscoveredMac(
        val serverId: String?,
        val name: String,
        val isDev: Boolean,
        val requestPair: Boolean,
        /** The service instance name — opaque, passed back to [resolve]. */
        val serviceName: String
    ) {
        val id: String get() = serverId ?: serviceName

        /** A dev build's name already ends "(dev)"; the row says so separately. */
        val displayName: String
            get() {
                val trimmed = name.trim(' ', '\t')
                if (!isDev || !trimmed.lowercase().endsWith("(dev)")) return trimmed
                return trimmed.dropLast("(dev)".length).trim(' ', '\t')
            }
    }

    data class Resolved(val host: String, val port: Int)

    data class Browse(
        val found: List<DiscoveredMac> = emptyList(),
        /** The system refused the browse (local network access off). */
        val denied: Boolean = false,
        /** Long enough without a result to say nothing was found. */
        val searchedAWhile: Boolean = false
    )

    val browse: StateFlow<Browse>

    /** Called on every change to `found`, for an owner that isn't a screen. */
    var onChange: ((List<DiscoveredMac>) -> Unit)?

    /** Idempotent: a second call while browsing is a no-op. */
    fun start()

    /** Stop browsing and drop the results. */
    fun stop()

    /** A concrete address for a found Mac, preferring IPv4; null on timeout. */
    suspend fun resolve(mac: DiscoveredMac, timeoutMs: Long = 5_000): Resolved?
}
