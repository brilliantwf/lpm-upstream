package cx.lpm.mobile.core.data.link

import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's own network: whether it has one, whether that's Wi-Fi or cellular,
 * and which local subnets the Wi-Fi puts it on. Lets the app react the moment the
 * phone changes networks instead of waiting for a dead socket, and tell "the Mac is
 * asleep" apart from "this isn't the Mac's network".
 */
interface NetworkMonitor {
    enum class Medium { None, Wifi, Cellular, Other }

    data class Subnet(val address: Long, val mask: Long) {
        fun contains(ip: Long): Boolean = ip and mask == address and mask
    }

    data class Network(val medium: Medium = Medium.Other, val subnets: List<Subnet> = emptyList()) {
        val isOnline: Boolean get() = medium != Medium.None

        /** `host` is an IPv4 on a subnet this phone's Wi-Fi is on. */
        fun sharesSubnet(host: String): Boolean {
            if (medium != Medium.Wifi) return false
            val ip = Hosts.ipv4(host) ?: return false
            return subnets.any { it.contains(ip) }
        }
    }

    val network: StateFlow<Network>

    /**
     * Called after the phone settles on a different network (debounced, since a
     * handover reports several in a row). One owner: the live session.
     */
    var onChange: ((from: Medium, to: Medium) -> Unit)?

    fun start()
}
