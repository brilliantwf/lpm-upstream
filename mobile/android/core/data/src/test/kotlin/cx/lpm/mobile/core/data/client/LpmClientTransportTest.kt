package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.FakeTransport
import cx.lpm.mobile.core.data.testing.SchedulerClock
import cx.lpm.mobile.core.model.long
import cx.lpm.mobile.core.model.string
import java.net.ConnectException
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LpmClientTransportTest {
    private val transport = FakeTransport()

    private fun TestScope.client(credential: LpmClient.Credential? = CREDENTIAL): LpmClient {
        val clock = SchedulerClock(testScheduler)
        val env = ClientEnvironment(backgroundScope, clock, transport, NoTailnet, Random(1))
        val endpoint = LpmClient.Endpoint("192.168.1.20", 8765)
        return LpmClientFactory(env).create(endpoint, credential, "Test Phone")
    }

    private fun TestScope.ready(c: LpmClient): FakeSocket {
        c.connect()
        val socket = transport.last
        socket.open()
        testScheduler.runCurrent()
        socket.receive("""{"t":"ready","serverId":"srv-1","serverName":"Studio Mac"}""")
        testScheduler.runCurrent()
        assertEquals(LpmClient.State.Ready, c.state)
        return socket
    }

    @Test
    fun `the handshake goes out only once the socket opens`() = runTest {
        val c = client()
        c.connect()
        val socket = transport.last
        assertEquals("wss://192.168.1.20:8765/", socket.url)
        assertEquals(LpmClient.State.Connecting, c.state)
        assertTrue(socket.sent.isEmpty())

        socket.open()
        testScheduler.runCurrent()

        assertEquals(listOf("auth"), socket.sentTypes)
        assertEquals("tok-1", socket.sentOf("auth").single().string("token"))
    }

    @Test
    fun `requests wait for ready while live traffic is dropped`() = runTest {
        val c = client()
        c.connect()
        c.requestProjects()
        c.sendInput("t1", "ls")
        val socket = transport.last
        socket.open()
        testScheduler.runCurrent()
        socket.receive("""{"t":"ready"}""")
        testScheduler.runCurrent()

        assertEquals(listOf("auth", "projects"), socket.sentTypes)
    }

    @Test
    fun `a failure retries quickly, then reports the offline hint while backing off`() = runTest {
        val states = mutableListOf<LpmClient.State>()
        val c = client()
        c.onState = { states += it }
        c.connect()
        transport.last.fail()
        testScheduler.runCurrent()
        assertEquals(LpmClient.State.Connecting, c.state)
        assertTrue(transport.sockets[0].closed)

        // First retry after ~1.5 s (±15 %).
        testScheduler.advanceTimeBy(1_800)
        testScheduler.runCurrent()
        assertEquals(2, transport.sockets.size)

        transport.last.fail()
        testScheduler.runCurrent()
        assertEquals(LpmClient.State.Failed(LpmClient.OFFLINE_HINT), c.state)

        // The second wait doubles (~3 s): nothing at 2.4 s, a new attempt by 3.5 s.
        testScheduler.advanceTimeBy(2_400)
        testScheduler.runCurrent()
        assertEquals(2, transport.sockets.size)
        testScheduler.advanceTimeBy(1_100)
        testScheduler.runCurrent()
        assertEquals(3, transport.sockets.size)
        assertEquals(LpmClient.State.Connecting, states.last())
    }

    @Test
    fun `a refused port is told apart from an unreachable Mac`() = runTest {
        val c = client()
        c.connect()
        repeat(2) {
            transport.last.fail(ConnectException("connect failed: ECONNREFUSED"))
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()
        }
        assertEquals(LpmClient.State.Connecting, c.state)
        transport.last.fail(ConnectException("Connection refused"))
        testScheduler.runCurrent()
        assertEquals(LpmClient.State.Failed(LpmClient.REFUSED_ERROR), c.state)
    }

    @Test
    fun `a stale socket's callbacks are ignored`() = runTest {
        val c = client()
        val first = ready(c)
        c.move("100.100.1.2")
        val second = transport.last
        first.fail()
        first.receive("""{"t":"error","error":"unauthorized"}""")
        testScheduler.runCurrent()

        assertEquals(LpmClient.State.Connecting, c.state)
        assertEquals("wss://100.100.1.2:8765/", second.url)
    }

    @Test
    fun `a gap asks for the missed slice, and a reconnect resumes from the offset`() = runTest {
        val output = mutableListOf<String>()
        val c = client()
        c.onOutput = { _, data -> output += data }
        val socket = ready(c)
        c.subscribe("t1")
        assertNull(socket.sentOf("sub").last().long("from"))

        // Output before the seed is already in the seed.
        socket.receive("""{"t":"o","id":"t1","d":"early","off":5}""")
        socket.receive("""{"t":"seed","id":"t1","cols":80,"rows":24,"data":"screen","off":10}""")
        socket.receive("""{"t":"o","id":"t1","d":"abc","off":13}""")
        socket.receive("""{"t":"o","id":"t1","d":"abc","off":13}""")
        socket.receive("""{"t":"o","id":"t1","d":"xyz","off":20}""")
        testScheduler.runCurrent()

        assertEquals(listOf("abc"), output)
        assertEquals(13L, socket.sentOf("sub").last().long("from"))

        socket.receive("""{"t":"seed","id":"t1","data":"missed","off":20,"reset":false}""")
        socket.fail()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(2_000)
        testScheduler.runCurrent()
        val next = transport.last
        next.open()
        testScheduler.runCurrent()
        next.receive("""{"t":"ready"}""")
        testScheduler.runCurrent()

        assertEquals(20L, next.sentOf("sub").single().long("from"))
    }

    @Test
    fun `a heartbeat with no pong drops the link and reconnects`() = runTest {
        val c = client()
        val socket = ready(c)

        testScheduler.advanceTimeBy(20_001)
        testScheduler.runCurrent()
        assertEquals(listOf("ping"), socket.sentTypes.filter { it == "ping" })
        socket.receive("""{"t":"pong"}""")
        testScheduler.runCurrent()
        assertEquals(LpmClient.State.Ready, c.state)

        testScheduler.advanceTimeBy(20_000)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(10_001)
        testScheduler.runCurrent()
        assertTrue(socket.closed)
        assertEquals(LpmClient.State.Connecting, c.state)
    }

    @Test
    fun `a foreground probe that gets no answer hands over to the reconnect loop`() = runTest {
        val c = client()
        val socket = ready(c)
        c.verifyNow()
        assertTrue(c.isVerifying)
        testScheduler.advanceTimeBy(4_001)
        testScheduler.runCurrent()

        assertFalse(c.isVerifying)
        assertTrue(socket.closed)
    }

    @Test
    fun `approve-on-Mac pairing never retries a dropped socket`() = runTest {
        var denied: String? = null
        val c = client(credential = null)
        c.onPairDenied = { reason, _ -> denied = reason }
        c.pairRequest("192.168.1.20", 8765)
        val socket = transport.last
        socket.open()
        testScheduler.runCurrent()
        assertEquals(listOf("pairRequest"), socket.sentTypes)

        socket.fail()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()

        assertEquals("timeout", denied)
        assertEquals(LpmClient.State.Idle, c.state)
        assertEquals(1, transport.sockets.size)
    }

    @Test
    fun `a rejected credential is fatal`() = runTest {
        val c = client()
        val socket = ready(c)
        socket.receive("""{"t":"error","error":"unauthorized"}""")
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(60_000)

        assertEquals(LpmClient.State.Failed(LpmClient.UNAUTHORIZED_ERROR), c.state)
        assertEquals(1, transport.sockets.size)
    }

    @Test
    fun `a full offline queue drops the new request and says so`() = runTest {
        var full = 0
        val c = client()
        c.onSendQueueFull = { full++ }
        c.connect()
        repeat(33) { c.requestProjects() }
        assertEquals(1, full)
    }

    @Test
    fun `a goodbye is withdrawn when the Mac keeps talking`() = runTest {
        val farewells = mutableListOf<String>()
        var withdrawn = false
        val c = client()
        c.onFarewell = { reason, _ -> farewells += reason }
        c.onFarewellWithdrawn = { withdrawn = true }
        val socket = ready(c)
        socket.receive("""{"t":"bye","reason":"sleep","at":1760000000000}""")
        testScheduler.runCurrent()
        assertEquals(listOf("sleep"), farewells)
        assertFalse(withdrawn)

        socket.receive("""{"t":"projects-changed"}""")
        testScheduler.runCurrent()
        assertTrue(withdrawn)
    }

    private companion object {
        val CREDENTIAL = LpmClient.Credential("dev-1", "tok-1")
    }
}
