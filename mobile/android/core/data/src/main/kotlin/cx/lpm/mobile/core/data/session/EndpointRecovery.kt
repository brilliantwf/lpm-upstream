package cx.lpm.mobile.core.data.session

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.link.Hosts
import cx.lpm.mobile.core.data.link.MacBrowser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Automatic endpoint recovery: when the active Mac's saved addresses stop
 * answering, browse the local network for it — a Mac whose LAN address changed
 * reappears under the same identity — and reconnect on the fresh address. Each slow
 * retry extends a short window, so the browse spans the wait between retries but
 * never outlives them.
 */
internal class EndpointRecovery(private val s: MacSession) {
    private var browser: MacBrowser? = null
    private var recovering = false
    private var window: Job? = null

    // Dedupes resolves within one browse, so each Mac is chased once.
    private val handled = mutableSetOf<String>()

    fun startIfStale(state: LpmClient.State, c: LpmClient) {
        if (state !is LpmClient.State.Failed || !LpmClient.isOfflineHint(state.message)) return
        if (c !== s.client || s.isVisit) return
        if (s.state.value.linkRecord?.serverId.isNullOrEmpty()) return
        if (!recovering) {
            recovering = true
            handled.clear()
            val b = browser ?: s.env.newBrowser().also { browser = it }
            b.onChange = ::handleDiscovered
            b.start()
        }
        window?.cancel()
        window = s.scope.launch {
            delay(WINDOW_MS)
            stopBrowsing()
            s.mutableState.update { it.copy(recoveryStatus = null) }
        }
    }

    /** Stop the browse; the status stays until the link returns or the window ends. */
    fun stopBrowsing() {
        window?.cancel()
        window = null
        browser?.onChange = null
        browser?.stop()
        recovering = false
        handled.clear()
    }

    fun suspendDiscovery() {
        stopBrowsing()
        s.mutableState.update { it.copy(recoveryStatus = null) }
    }

    // For any found Mac matching a saved record by identity, resolve a fresh address
    // and heal the record — reconnecting only for the active Mac.
    private fun handleDiscovered(found: List<MacBrowser.DiscoveredMac>) {
        for (mac in found) {
            val sid = mac.serverId?.ifEmpty { null } ?: continue
            if (sid in handled) continue
            val record =
                s.state.value.macs.firstOrNull { it.serverId == sid && it.via == null } ?: continue
            handled += sid
            val isActive = record.localId == s.state.value.linkRecord?.localId
            val b = browser ?: return
            s.scope.launch {
                val resolved = b.resolve(mac)
                if (resolved == null) {
                    // Resolution failed — a later batch may retry this Mac.
                    handled -= sid
                    return@launch
                }
                applyRecovered(record.localId, resolved.host, resolved.port, mac.name, isActive)
            }
        }
    }

    private fun applyRecovered(
        localId: String,
        host: String,
        port: Int,
        name: String,
        reconnect: Boolean
    ) {
        if (s.state.value.macs.none { it.localId == localId }) return
        s.updateMac(localId) { r ->
            val moved = r.copy(hosts = Hosts.merged(r.hosts, listOf(host)), port = port)
            if (name.isNotEmpty() && moved.isAddressName) moved.copy(name = name) else moved
        }
        s.persistMacs()
        if (!reconnect) return
        // A parallel path (repick, a manual retry) may have reconnected already.
        if (s.state.value.isReady) return suspendDiscovery()
        stopBrowsing()
        val record = s.state.value.macs.first { it.localId == localId }
        s.mutableState.update {
            it.copy(recoveryStatus = "Found ${record.displayName}, reconnecting…")
        }
        s.activeCredential()?.let { s.connect(host, port, it) }
    }

    private companion object {
        const val WINDOW_MS = 25_000L
    }
}
