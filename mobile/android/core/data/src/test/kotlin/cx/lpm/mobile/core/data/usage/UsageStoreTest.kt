package cx.lpm.mobile.core.data.usage

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.USAGE_GENERIC_ERROR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsageStoreTest {
    private fun limits(t: String = "limits", now: Long? = null, used: Int = 27) =
        """{"t":"$t","ok":true,"claudeEnabled":true,""" +
            (now?.let { "\"now\":$it," } ?: "") +
            """"limits":{"claude:default":{"provider":"claude","accountId":"default",""" +
            """"weekly":{"usedPercent":$used,"resetsAt":1792400000},"updatedAt":1792065600000}}}"""

    private fun TestScope.ready(): Triple<SessionHarness, FakeSocket, UsageStore> {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        link = socket
        return Triple(h, socket, h.session.store())
    }

    private var link: FakeSocket? = null
    private var pongs = 0

    /** Moves virtual time on with the Mac answering every heartbeat, so the link stays up. */
    private fun TestScope.settle(millis: Long = 0) {
        var left = millis
        do {
            val step = minOf(left, HEARTBEAT_STEP_MS)
            testScheduler.advanceTimeBy(step)
            testScheduler.runCurrent()
            link?.takeUnless { it.closed }?.let { socket ->
                repeat(socket.sentOf("ping").size - pongs) {
                    socket.receive("""{"t":"pong"}""")
                    pongs++
                }
                testScheduler.runCurrent()
            }
            left -= step
        } while (left > 0)
    }

    @Test
    fun `a reply settles the screen and measures the Mac's clock`() = runTest {
        val (h, socket, usage) = ready()
        val before = socket.sentOf("limits").size
        usage.load()
        assertEquals(before + 1, socket.sentOf("limits").size)
        assertTrue(usage.state.value.loading)

        socket.receive(limits(now = h.clock.millis() + 60_000))
        settle()
        val state = usage.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(27.0, state.snapshot?.entries?.get("claude:default")?.weekly?.usedPercent)
        assertEquals(60_000.0, state.clockOffsetMs, 1.0)
        assertEquals(h.clock.millis() + 60_000.0, state.macMillis(h.clock.millis()), 1.0)
    }

    @Test
    fun `a Mac that never answers gives up after 20 seconds`() = runTest {
        val (_, _, usage) = ready()
        usage.load()
        settle(19_999)
        assertTrue(usage.state.value.loading)
        settle(1)
        assertFalse(usage.state.value.loading)
        assertEquals(USAGE_GENERIC_ERROR, usage.state.value.error)
    }

    @Test
    fun `a push replaces the snapshot and clears the spinner and any failure`() = runTest {
        val (_, socket, usage) = ready()
        usage.load()
        settle(20_000)
        assertEquals(USAGE_GENERIC_ERROR, usage.state.value.error)

        usage.load()
        socket.receive(limits(t = "limits-changed", used = 40))
        settle()
        val state = usage.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(40.0, state.snapshot?.entries?.get("claude:default")?.weekly?.usedPercent)
        // The push settled the request, so its timeout no longer fires.
        settle(20_000)
        assertNull(usage.state.value.error)
    }

    @Test
    fun `a refused reload keeps the numbers on screen and says why`() = runTest {
        val (_, socket, usage) = ready()
        socket.receive(limits())
        settle()
        usage.load()
        socket.receive("""{"t":"limits","ok":false,"error":"Usage is busy."}""")
        settle()
        val state = usage.state.value
        assertNotNull(state.snapshot)
        assertEquals("Usage is busy.", state.error)
        assertFalse(state.loading)
    }

    @Test
    fun `today's spend is read at most every 30 seconds, the accounts until they arrive`() =
        runTest {
            val (_, socket, usage) = ready()
            usage.rowsDidAppear()
            assertEquals(listOf(1), socket.sentOf("stats").map { it["days"].toString().toInt() })
            assertEquals(1, socket.sentOf("claudeAccounts").size)

            settle(10_000)
            usage.refreshExtras()
            assertEquals(1, socket.sentOf("stats").size)
            assertEquals(2, socket.sentOf("claudeAccounts").size)

            socket.receive("""{"t":"claudeAccounts","ok":true,"accounts":[],"switching":{}}""")
            settle(10_000)
            usage.refreshExtras()
            assertEquals(1, socket.sentOf("stats").size)
            assertEquals(2, socket.sentOf("claudeAccounts").size)

            settle(10_000)
            usage.refreshExtras()
            assertEquals(2, socket.sentOf("stats").size)
            assertEquals(3, socket.sentOf("claudeAccounts").size)
        }

    @Test
    fun `a push refreshes today's spend only while rows are showing`() = runTest {
        val (_, socket, usage) = ready()
        usage.rowsDidAppear()
        settle(30_000)
        socket.receive(limits(t = "limits-changed"))
        settle()
        assertEquals(2, socket.sentOf("stats").size)

        usage.rowsDidDisappear()
        settle(30_000)
        socket.receive(limits(t = "limits-changed"))
        settle()
        assertEquals(2, socket.sentOf("stats").size)
    }

    @Test
    fun `a dropped link reloads the open screen`() = runTest {
        val (_, socket, usage) = ready()
        usage.screenDidOpen()
        socket.receive(limits())
        settle()
        assertFalse(usage.state.value.loading)

        socket.fail()
        settle()
        assertTrue(usage.state.value.loading)
    }

    @Test
    fun `a dropped link leaves a closed screen alone`() = runTest {
        val (_, socket, usage) = ready()
        usage.screenDidOpen()
        usage.screenDidClose()

        socket.fail()
        settle()
        assertFalse(usage.state.value.loading)
    }

    private companion object {
        const val HEARTBEAT_STEP_MS = 5_000L
    }
}
