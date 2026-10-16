package cx.lpm.mobile.feature.pairing

import cx.lpm.mobile.core.model.DEFAULT_MAC_PORT
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * A pairing payload decoded from the desktop's QR code:
 * `lpm://pair?p=<port>&c=<code>&h=<host>&h=<host>…&f=<fingerprint>`. The Mac
 * advertises every address it can be reached at (LAN, Tailscale) as repeated `h`
 * params; the phone probes them and keeps the reachable one. `f` is the hex SHA-256
 * of the Mac's TLS leaf cert, verified during the pairing handshake so the phone
 * pins the right identity; absent from Macs paired before TLS.
 */
data class PairPayload(
    val hosts: List<String>,
    val port: Int,
    val code: String,
    val fingerprint: String?
) {
    val host: String get() = hosts.firstOrNull().orEmpty()

    companion object {
        /** Parses like iOS `URLComponents`: query values percent-decoded, `+` kept as is. */
        fun parse(raw: String): PairPayload? {
            val uri = try {
                URI(raw)
            } catch (_: URISyntaxException) {
                return null
            }
            if (uri.scheme != "lpm" || uri.host != "pair") return null
            val items = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { item ->
                val name = item.substringBefore('=')
                val value = if ('=' in item) item.substringAfter('=') else null
                percentDecode(name) to value?.let(::percentDecode)
            }

            fun q(name: String): String? = items.firstOrNull { it.first == name }?.second
            val hosts = items.filter {
                it.first == "h"
            }.mapNotNull { it.second }.filter { it.isNotEmpty() }
            val code = q("c")
            if (hosts.isEmpty() || code == null) return null
            val fingerprint = q("f")?.lowercase()?.trim()?.ifEmpty { null }
            return PairPayload(
                hosts = hosts,
                port = (q("p") ?: DEFAULT_MAC_PORT.toString()).toIntOrNull() ?: DEFAULT_MAC_PORT,
                code = code,
                fingerprint = fingerprint
            )
        }

        private fun percentDecode(s: String): String {
            if ('%' !in s) return s
            val bytes = ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                val hex = if (c == '%' && i + 2 < s.length) {
                    s.substring(i + 1, i + 3).toIntOrNull(HEX)
                } else {
                    null
                }
                if (hex != null) {
                    bytes.write(hex)
                    i += 3
                } else {
                    bytes.write(c.toString().toByteArray())
                    i++
                }
            }
            return bytes.toString(Charsets.UTF_8.name())
        }

        private const val HEX = 16
    }
}
