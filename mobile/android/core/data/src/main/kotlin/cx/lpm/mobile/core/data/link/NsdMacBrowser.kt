package cx.lpm.mobile.core.data.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import cx.lpm.mobile.core.data.link.MacBrowser.Browse
import cx.lpm.mobile.core.data.link.MacBrowser.DiscoveredMac
import cx.lpm.mobile.core.data.link.MacBrowser.Resolved
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [MacBrowser] over `NsdManager`, holding a Wi-Fi multicast lock while it browses.
 * NSD reports TXT records only on resolve, so each found service is resolved (one
 * at a time — older NsdManagers refuse concurrent resolves) before it's listed.
 */
class NsdMacBrowser(private val context: Context, private val scope: CoroutineScope) :
    MacBrowser {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val state = MutableStateFlow(Browse())
    override val browse: StateFlow<Browse> = state.asStateFlow()
    override var onChange: ((List<DiscoveredMac>) -> Unit)? = null

    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    private var patience: Job? = null
    private val resolved = mutableMapOf<String, Pair<DiscoveredMac, Resolved?>>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    override fun start() {
        if (listener != null || nsd == null) return
        // Browsing without it would only bring up the system's one-Mac picker.
        if (!LocalNetwork.granted(context)) {
            state.update { it.copy(denied = true) }
            return
        }
        state.update { it.copy(denied = false) }
        lock = wifi?.createMulticastLock("lpm-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                scope.launch { restart() }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                scope.launch { enqueue(info) }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                scope.launch { lost(info.serviceName) }
            }
        }
        listener = l
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        patience?.cancel()
        patience = scope.launch {
            delay(PATIENCE_MS)
            state.update { it.copy(searchedAWhile = true) }
        }
    }

    override fun stop() {
        listener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        listener = null
        lock?.release()
        lock = null
        patience?.cancel()
        patience = null
        queue.clear()
        resolved.clear()
        val hadResults = state.value.found.isNotEmpty()
        state.value = Browse()
        if (hadResults) onChange?.invoke(emptyList())
    }

    override suspend fun resolve(mac: DiscoveredMac, timeoutMs: Long): Resolved? {
        val known = resolved[mac.serviceName]?.second
        val info = NsdServiceInfo().apply {
            serviceName = mac.serviceName
            serviceType = SERVICE_TYPE
        }
        val fresh = withTimeoutOrNull(timeoutMs) { resolveService(info) }
        return fresh?.let(::address) ?: known
    }

    private fun restart() {
        stop()
        start()
    }

    private fun enqueue(info: NsdServiceInfo) {
        queue.addLast(info)
        if (!resolving) resolveNext()
    }

    private fun resolveNext() {
        val next = queue.removeFirstOrNull() ?: run {
            resolving = false
            return
        }
        resolving = true
        scope.launch {
            val info = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveService(next) }
            if (listener != null && info != null) found(next.serviceName, info)
            resolveNext()
        }
    }

    private fun found(serviceName: String, info: NsdServiceInfo) {
        val txt = info.attributes.mapValues { it.value?.decodeToString() }
        val mac = DiscoveredMac(
            serverId = txt["id"],
            name = txt["name"]?.ifEmpty { null } ?: serviceName,
            isDev = txt["dev"] == "1",
            requestPair = txt["rp"] != "0",
            serviceName = serviceName
        )
        resolved[serviceName] = mac to address(info)
        publish()
    }

    private fun lost(serviceName: String) {
        if (resolved.remove(serviceName) != null) publish()
    }

    private fun publish() {
        val macs = resolved.values.map { it.first }.sortedBy { it.name.lowercase() }
        if (macs == state.value.found) return
        state.update { it.copy(found = macs) }
        onChange?.invoke(macs)
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveService(info: NsdServiceInfo): NsdServiceInfo? {
        val manager = nsd ?: return null
        return suspendCancellableCoroutine { cont ->
            manager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                }
            )
        }
    }

    // IPv4 first (LAN Macs advertise an A record). Zone ids are stripped so the
    // address forms a plain wss://host:port/.
    private fun address(info: NsdServiceInfo): Resolved? {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(info.host)
        }
        val pick: InetAddress =
            addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return null
        val host = pick.hostAddress?.substringBefore('%') ?: return null
        return Resolved(host, info.port)
    }

    private companion object {
        const val SERVICE_TYPE = "_lpm._tcp"
        const val PATIENCE_MS = 5_000L
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
