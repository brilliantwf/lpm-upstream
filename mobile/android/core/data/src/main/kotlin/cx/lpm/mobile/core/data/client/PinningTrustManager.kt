package cx.lpm.mobile.core.data.client

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * TLS trust for the wss:// link with trust-on-first-use pinning. The desktop serves
 * a self-signed certificate, so no CA is ever consulted: the identity is the SHA-256
 * of the leaf certificate's DER bytes, matched against a per-Mac pin.
 *
 * - `expected` (QR pairing): the fingerprint the QR advertised — accept iff it matches.
 * - `pinProvider` (reconnect): the stored pin, read fresh each handshake — accept iff it
 *   matches; no pin yet is a first connect, accepted so the session can pin what it saw.
 */
@SuppressLint("CustomX509TrustManager")
class PinningTrustManager(
    private val pinProvider: (() -> String?)?,
    private val expected: String?
) : X509TrustManager {
    /** Called on the TLS thread when the certificate doesn't match; `pairing` for a QR pin. */
    @Volatile var onMismatch: ((pairing: Boolean) -> Unit)? = null

    /** The leaf fingerprint of the last handshake, for pinning after `paired`/`ready`. */
    @Volatile var observed: String? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no server certificate")
        val fingerprint = CertPinning.sha256Hex(leaf.encoded)
        observed = fingerprint
        if (expected != null) {
            if (fingerprint == expected) return
            onMismatch?.invoke(true)
            throw CertificateException(LpmClient.PAIR_MISMATCH_ERROR)
        }
        val pin = pinProvider?.invoke() ?: return
        if (fingerprint == pin) return
        onMismatch?.invoke(false)
        throw CertificateException(LpmClient.IDENTITY_CHANGED_ERROR)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit =
        throw CertificateException("client certificates are not used")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** The pinned identity is the lowercase hex SHA-256 of the leaf certificate's DER. */
object CertPinning {
    fun sha256Hex(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }
}
