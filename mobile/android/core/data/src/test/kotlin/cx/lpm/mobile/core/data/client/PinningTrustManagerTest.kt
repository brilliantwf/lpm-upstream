package cx.lpm.mobile.core.data.client

import java.security.cert.CertificateException
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PinningTrustManagerTest {
    private val mac = HeldCertificate.Builder().commonName("lpm").build().certificate
    private val impostor = HeldCertificate.Builder().commonName("lpm").build().certificate
    private val fingerprint = CertPinning.sha256Hex(mac.encoded)
    private val mismatches = mutableListOf<Boolean>()

    private fun trust(pin: String? = null, expected: String? = null) =
        PinningTrustManager({ pin }, expected).also {
            it.onMismatch =
                { pairing -> mismatches += pairing }
        }

    @Test
    fun `the fingerprint is the lowercase hex SHA-256 of the leaf DER`() {
        assertEquals(64, fingerprint.length)
        assertEquals(fingerprint.lowercase(), fingerprint)
    }

    @Test
    fun `no pin yet is trusted on first use and remembered`() {
        val tm = trust()
        tm.checkServerTrusted(arrayOf(mac), "ECDHE_RSA")
        assertEquals(fingerprint, tm.observed)
        assertEquals(emptyList<Boolean>(), mismatches)
    }

    @Test
    fun `the stored pin accepts only the same certificate`() {
        trust(pin = fingerprint).checkServerTrusted(arrayOf(mac), "ECDHE_RSA")

        val tm = trust(pin = fingerprint)
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(impostor), "ECDHE_RSA")
        }
        assertEquals(CertPinning.sha256Hex(impostor.encoded), tm.observed)
        assertEquals(listOf(false), mismatches)
    }

    @Test
    fun `a QR fingerprint must match exactly, and wins over any stored pin`() {
        trust(expected = fingerprint).checkServerTrusted(arrayOf(mac), "ECDHE_RSA")

        assertThrows(CertificateException::class.java) {
            trust(pin = CertPinning.sha256Hex(impostor.encoded), expected = fingerprint)
                .checkServerTrusted(arrayOf(impostor), "ECDHE_RSA")
        }
        assertEquals(listOf(true), mismatches)
    }

    @Test
    fun `no certificate at all is refused`() {
        val tm = trust()
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "ECDHE_RSA")
        }
        assertNull(tm.observed)
    }
}
