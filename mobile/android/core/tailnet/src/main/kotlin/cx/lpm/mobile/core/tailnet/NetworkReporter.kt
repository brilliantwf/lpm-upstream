package cx.lpm.mobile.core.tailnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** The network the phone's traffic leaves through: its interface and IPv4 gateway. */
data class DefaultNetwork(val interfaceName: String = "", val gateway: String = "")

/**
 * The phone's networks as the node needs them (iOS's NWPathMonitor). Android 11
 * and later refuse apps the netlink queries Go would use to find them itself.
 */
interface NetworkReporter {
    /** Reports the default network now, then on every switch; "" once it's lost. */
    fun start(onChange: (DefaultNetwork) -> Unit)

    fun stop()

    /** Every interface, in tailnet/android's snapshot format. Blocks: it asks the OS. */
    fun interfaces(): String
}

class ConnectivityNetworkReporter @Inject constructor(@ApplicationContext context: Context) :
    NetworkReporter {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun start(onChange: (DefaultNetwork) -> Unit) {
        if (callback != null) return
        connectivity.activeNetwork?.let { active ->
            val vpn = connectivity.getNetworkCapabilities(active)?.isVpn() ?: false
            val links = connectivity.getLinkProperties(active)
            if (!vpn && links != null) onChange(links.toDefaultNetwork())
        }
        val vpns = ConcurrentHashMap<Network, Boolean>()
        val cb = object : ConnectivityManager.NetworkCallback() {
            @Volatile private var current: Network? = null

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                vpns[network] = caps.isVpn()
            }

            // Another VPN's tunnel isn't where the node's own traffic should go.
            override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) {
                if (vpns[network] == true) return
                current = network
                onChange(links.toDefaultNetwork())
            }

            // A network that already stopped being the default says nothing about now.
            override fun onLost(network: Network) {
                vpns.remove(network)
                if (network != current) return
                current = null
                onChange(DefaultNetwork())
            }
        }
        callback = cb
        connectivity.registerDefaultNetworkCallback(cb)
    }

    override fun stop() {
        callback?.let(connectivity::unregisterNetworkCallback)
        callback = null
    }

    override fun interfaces(): String {
        val all = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: SocketException) {
            emptyList()
        }
        return all.mapNotNull { it.snapshotLine() }.joinToString("\n")
    }

    private fun NetworkCapabilities.isVpn() = hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    private fun LinkProperties.toDefaultNetwork() = DefaultNetwork(
        interfaceName = interfaceName.orEmpty(),
        gateway = routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress.orEmpty()
    )

    private fun NetworkInterface.snapshotLine(): String? = try {
        snapshotLine(
            name = name,
            index = index,
            mtu = mtu,
            up = isUp,
            broadcast = interfaceAddresses.any { it.broadcast != null },
            loopback = isLoopback,
            pointToPoint = isPointToPoint,
            multicast = supportsMulticast(),
            addresses = interfaceAddresses.map { it.address to it.networkPrefixLength.toInt() }
        )
    } catch (_: SocketException) {
        null
    }
}

/**
 * One interface as tailnet/android reads it: name, index, MTU, the up, broadcast,
 * loopback, point-to-point and multicast flags, a bar, then each address as a
 * prefix. Zones are dropped: Go's prefix parser rejects `fe80::1%wlan0/64`.
 */
internal fun snapshotLine(
    name: String,
    index: Int,
    mtu: Int,
    up: Boolean,
    broadcast: Boolean,
    loopback: Boolean,
    pointToPoint: Boolean,
    multicast: Boolean,
    addresses: List<Pair<InetAddress, Int>>
): String {
    val prefixes = addresses.joinToString(" ") { (address, bits) ->
        address.hostAddress.orEmpty().substringBefore('%') + "/" + bits
    }
    return "$name $index $mtu $up $broadcast $loopback $pointToPoint $multicast | $prefixes"
}
