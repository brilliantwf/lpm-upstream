package cx.lpm.mobile.core.data.link

/** The address rules shared by connecting, pairing and recovery (iOS AppModel statics). */
object Hosts {
    /** A Tailscale CGNAT address (100.64.0.0/10) from the candidates, if any. */
    fun cgnatHost(hosts: List<String>): String? = hosts.firstOrNull(::isCGNAT)

    /** An IPv4 whose first octet is 100 and second is in 64..127. */
    fun isCGNAT(host: String): Boolean {
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        return octets.size == 4 && octets[0] == 100 && octets[1] in 64..127
    }

    /** A dotted-quad IPv4 literal (four 0–255 octets). */
    fun isIPv4Literal(host: String): Boolean {
        val octets = host.split(".")
        return octets.size == 4 && octets.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    /**
     * Fold freshly discovered addresses into a record's list: the fresh ones lead,
     * everything else keeps its order — except a plain LAN IPv4 literal, which the
     * fresh address replaces as stale. CGNAT addresses, hostnames (`mac.local`, a
     * MagicDNS name) and IPv6 survive as the away-from-home fallback.
     */
    fun merged(existing: List<String>, fresh: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (h in fresh) if (h.isNotEmpty() && h !in out) out += h
        for (h in existing) {
            if (h in out || (isIPv4Literal(h) && !isCGNAT(h))) continue
            out += h
        }
        return out
    }

    /**
     * Fold a Mac's *advertised* addresses in without dropping anything: the Mac
     * advertises only what it sees of itself, so a host given here by hand (a
     * port-forwarded WAN address, a VPN that isn't Tailscale) may well be the one
     * carrying this very connection.
     */
    fun union(existing: List<String>, fresh: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (h in fresh) if (h.isNotEmpty() && h !in out) out += h
        for (h in existing) if (h !in out) out += h
        return out
    }

    /** The phone's view of an IPv4 literal as a 32-bit value; null for anything else. */
    fun ipv4(host: String): Long? {
        val parts = host.split(".")
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet =
                part.toLongOrNull()?.takeIf { it in 0..255 && part.all(Char::isDigit) }
                    ?: return null
            value = (value shl 8) or octet
        }
        return value
    }

    /** 100.64.0.0/10 or a MagicDNS name. */
    fun isTailnetHost(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 100 && b in 64..127
        }
        return host.endsWith(".ts.net") || host.endsWith(".ts.net.")
    }
}
