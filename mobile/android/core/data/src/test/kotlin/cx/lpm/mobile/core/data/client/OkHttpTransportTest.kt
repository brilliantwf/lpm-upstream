package cx.lpm.mobile.core.data.client

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The real OkHttp transport against a TLS WebSocket server with a self-signed cert. */
class OkHttpTransportTest {
    // Like the Mac's: self-signed and naming no host the phone dials.
    private val held = HeldCertificate.Builder().commonName("lpm desktop").build()
    private val server = MockWebServer()
    private val events = LinkedBlockingQueue<String>()
    private val mismatches = mutableListOf<Boolean>()

    private val listener = object : SocketListener {
        override fun onOpen(socket: Socket) {
            events += "open"
        }

        override fun onMessage(socket: Socket, text: String) {
            events += "message:$text"
        }

        override fun onFailure(socket: Socket, error: Throwable?) {
            events += "failure"
        }
    }

    @Before
    fun setUp() {
        server.useHttps(
            HandshakeCertificates.Builder().heldCertificate(held).build().sslSocketFactory()
        )
        server.start()
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        webSocket.send("echo:$text")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                    }
                }
            ).build()
        )
    }

    @After
    fun tearDown() = server.close()

    private fun open(pin: String): Socket {
        val trust = PinningTrustManager({ pin }, null).also {
            it.onMismatch =
                { p -> mismatches += p }
        }
        val url = WssUrl.make("127.0.0.1", server.port)!!
        return OkHttpTransport(OkHttpTransport.baseClient()).open(url, trust, listener)
    }

    @Test
    fun `the pinned certificate opens the socket and carries frames both ways`() {
        val socket = open(CertPinning.sha256Hex(held.certificate.encoded))
        assertEquals("open", events.poll(5, TimeUnit.SECONDS))
        assertTrue(socket.send("hi"))
        assertEquals("message:echo:hi", events.poll(5, TimeUnit.SECONDS))
        socket.close()
    }

    @Test
    fun `any other certificate is refused and reported as a changed identity`() {
        open("00".repeat(32))
        assertEquals("failure", events.poll(5, TimeUnit.SECONDS))
        assertEquals(listOf(false), mismatches.distinct())
    }
}
