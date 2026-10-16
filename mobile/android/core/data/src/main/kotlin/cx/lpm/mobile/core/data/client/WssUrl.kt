package cx.lpm.mobile.core.data.client

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Forms `wss://` URLs from any saved host form, shared by the live socket and the
 * reachability probe. A bare IPv6 literal is bracketed (a zone id's `%` encoded per
 * RFC 6874); hostnames, IPv4 literals and bracketed input pass through. Null when
 * the result isn't a usable URL, which callers treat as an unreachable address.
 */
object WssUrl {
    fun make(host: String, port: Int): String? {
        val h = urlHost(host)
        if (h.isEmpty() || port !in 1..65535) return null
        "https://$h:$port/".toHttpUrlOrNull() ?: return null
        return "wss://$h:$port/"
    }

    /** The host part of a URL `make` built. */
    fun hostOf(url: String): String? =
        url.replaceFirst("wss://", "https://").toHttpUrlOrNull()?.host

    private fun urlHost(host: String): String {
        val trimmed = host.trim(' ', '\t')
        if (':' !in trimmed || trimmed.startsWith("[")) return trimmed
        return "[" + trimmed.replace("%", "%25") + "]"
    }
}
