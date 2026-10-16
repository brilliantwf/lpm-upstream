package cx.lpm.mobile.core.data.link

/**
 * What a saved address is, in the terms people set them up in. Drives the labels on
 * the address list and whether a connection counts as away from home.
 */
enum class AddressKind(val label: String) {
    Tailscale("Tailscale"),
    Home("Home network"),
    LocalName("Name on your network"),
    ThisDevice("This device"),
    Internet("Internet address");

    /** Reachable from any network, not just the Mac's own. */
    val worksAway: Boolean get() = this == Tailscale || this == Internet

    companion object {
        fun of(raw: String): AddressKind {
            val host = raw.trim(' ', '\t').lowercase()
            if (host == "localhost" || host.startsWith("127.") || host == "::1") return ThisDevice
            if (Hosts.isTailnetHost(host) || host.startsWith("fd7a:115c:a1e0:")) return Tailscale
            Hosts.ipv4(host)?.let { ip ->
                val a = (ip shr 24).toInt()
                val b = ((ip shr 16) and 0xff).toInt()
                val private = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                    (a == 169 && b == 254)
                return if (private) Home else Internet
            }
            if (':' in host) {
                val local =
                    host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd")
                return if (local) Home else Internet
            }
            if (host.endsWith(".local") || '.' !in host) return LocalName
            return Internet
        }
    }
}
