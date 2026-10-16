package cx.lpm.mobile.core.data.link

import cx.lpm.mobile.core.data.client.CertPinning
import cx.lpm.mobile.core.data.client.NoTailnet
import cx.lpm.mobile.core.data.client.OkHttpTransport
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** The probe against a real TLS WebSocket server, pinned like the live link. */
class OkHttpHostProberTest {
    private val held = HeldCertificate.Builder().commonName("lpm desktop").build()
    private val fingerprint = CertPinning.sha256Hex(held.certificate.encoded)
    private val server = MockWebServer()
    private val prober = OkHttpHostProber(OkHttpTransport.baseClient(), NoTailnet)

    @Before
    fun setUp() {
        server.useHttps(
            HandshakeCertificates.Builder().heldCertificate(held).build().sslSocketFactory()
        )
        server.start()
        // One upgrade per probe that gets as far as asking for it.
        repeat(2) {
            server.enqueue(
                MockResponse.Builder().webSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, null)
                        }
                    }
                ).build()
            )
        }
    }

    @After
    fun tearDown() = server.close()

    private fun probe(identity: HostProber.Identity) = runBlocking {
        prober.open("127.0.0.1", server.port, identity, timeoutMs = 5_000)
    }

    @Test
    fun `the Mac's own certificate answers`() {
        assertEquals(
            HostProber.Outcome("127.0.0.1", true, "open"),
            probe(HostProber.Identity(pin = fingerprint))
        )
        assertEquals(true, probe(HostProber.Identity.FirstUse).reachable)
    }

    @Test
    fun `a certificate that doesn't match the pin still counts as reachable`() {
        val outcome = probe(HostProber.Identity(pin = "00".repeat(32)))
        assertEquals(HostProber.Outcome("127.0.0.1", true, "open"), outcome)
    }

    @Test
    fun `a QR fingerprint that doesn't match still counts as reachable`() {
        assertEquals(true, probe(HostProber.Identity(expected = "ff".repeat(32))).reachable)
    }

    @Test
    fun `nothing listening is refused`() {
        val closed = ServerSocket(0).use { it.localPort }
        val outcome =
            runBlocking { prober.open("127.0.0.1", closed, HostProber.Identity.FirstUse, 5_000) }
        assertFalse(outcome.reachable)
        assertEquals("refused", outcome.detail)
    }
}
