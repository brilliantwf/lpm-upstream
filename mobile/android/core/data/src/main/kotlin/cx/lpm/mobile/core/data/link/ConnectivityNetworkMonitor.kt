package cx.lpm.mobile.core.data.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network as AndroidNetwork
import android.net.NetworkCapabilities
import cx.lpm.mobile.core.data.link.NetworkMonitor.Medium
import cx.lpm.mobile.core.data.link.NetworkMonitor.Network
import cx.lpm.mobile.core.data.link.NetworkMonitor.Subnet
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [NetworkMonitor] over the default network's `ConnectivityManager` callbacks. */
class ConnectivityNetworkMonitor(private val context: Context, private val scope: CoroutineScope) :
    NetworkMonitor {
    private val state = MutableStateFlow(Network())
    override val network: StateFlow<Network> = state.asStateFlow()
    override var onChange: ((from: Medium, to: Medium) -> Unit)? = null

    private var started = false
    private var settle: Job? = null

    // What was last reported, so a change can be told from a repeat; null until the
    // first network, which is only a baseline.
    private var reported: Network? = null

    override fun start() {
        if (started) return
        started = true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val current = cm.activeNetwork?.let(cm::getNetworkCapabilities)
        apply(medium(current))
        cm.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: AndroidNetwork,
                    caps: NetworkCapabilities
                ) {
                    val medium = medium(caps)
                    scope.launch { apply(medium) }
                }

                override fun onLost(network: AndroidNetwork) {
                    scope.launch { apply(Medium.None) }
                }
            }
        )
    }

    private fun apply(medium: Medium) {
        state.value = Network(medium, if (medium == Medium.Wifi) wifiSubnets() else emptyList())
        settle?.cancel()
        settle = scope.launch {
            delay(SETTLE_MS)
            val now = state.value
            val from = reported
            reported = now
            if (from != null && from != now) onChange?.invoke(from.medium, now.medium)
        }
    }

    private fun medium(caps: NetworkCapabilities?): Medium = when {
        caps == null -> Medium.None

        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Medium.Wifi

        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Medium.Cellular

        else -> Medium.Other
    }

    // The IPv4 subnets of the Wi-Fi and wired interfaces, whatever the default
    // network is — a VPN on top doesn't move the phone off its home network.
    private fun wifiSubnets(): List<Subnet> {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: SocketException) {
            emptyList()
        }
        return interfaces
            .filter {
                it.name.startsWith("wlan") || it.name.startsWith("eth") ||
                    it.name.startsWith("en")
            }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { a ->
                val ip = (a.address as? Inet4Address)?.address ?: return@mapNotNull null
                val value = ip.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
                val prefix = a.networkPrefixLength.toInt().coerceIn(0, 32)
                val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
                Subnet(value, mask).takeIf { value != 0L }
            }
    }

    private companion object {
        const val SETTLE_MS = 600L
    }
}
