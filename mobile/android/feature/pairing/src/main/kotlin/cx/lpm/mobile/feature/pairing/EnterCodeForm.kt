package cx.lpm.mobile.feature.pairing

import cx.lpm.mobile.core.data.link.AddressKind
import cx.lpm.mobile.core.model.DEFAULT_MAC_PORT

/**
 * Typed pairing, as lpm on the Mac shows it: the code, then the address under it.
 * The address may carry its own port ("address:port"), in which case the port
 * field doesn't count.
 */
data class EnterCodeForm(
    val code: String = "",
    val host: String = "",
    val port: String = DEFAULT_MAC_PORT.toString()
) {
    /** What the address hint says, so a home address isn't mistaken for one that works away. */
    enum class Hint { Tailscale, Home }

    /** The address as typed, accepting the "address:port" lpm shows on the Mac. */
    val target: Pair<String, Int>
        get() {
            val typed = host.trim()
            val parts = typed.split(':').filter { it.isNotEmpty() }
            val inline = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && inline != null &&
                inline in 1..MAX_PORT
            ) {
                return parts[0] to inline
            }
            return typed to (port.toIntOrNull() ?: DEFAULT_MAC_PORT)
        }

    val portIsValid: Boolean
        get() {
            if (host.trim().split(':').filter { it.isNotEmpty() }.size == 2) return true
            return port.toIntOrNull()?.let { it in 1..MAX_PORT } ?: false
        }

    /** The code without spaces, in capitals: "ab12 cd34" pairs as "AB12CD34". */
    val cleanCode: String get() = code.filterNot(Char::isWhitespace).uppercase()

    val canPair: Boolean get() = cleanCode.isNotEmpty() && target.first.isNotEmpty() && portIsValid

    val hint: Hint?
        get() {
            val h = target.first
            if (h.isEmpty()) return null
            return when (AddressKind.of(h)) {
                AddressKind.Tailscale -> Hint.Tailscale
                AddressKind.Home, AddressKind.LocalName -> Hint.Home
                else -> null
            }
        }

    private companion object {
        const val MAX_PORT = 65_535
    }
}
