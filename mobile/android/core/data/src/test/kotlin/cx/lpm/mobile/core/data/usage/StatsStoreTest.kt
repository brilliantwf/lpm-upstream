package cx.lpm.mobile.core.data.usage

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsStoreTest {
    private fun stats(days: Int, sessions: Int = 3) =
        """{"t":"stats","ok":true,"stats":{"days":$days,"sessions":$sessions,""" +
            """"totals":{"totalTokens":1200}}}"""

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
    fun `a period loads and its reply lands`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val store = h.session.store<StatsStore>()
        assertEquals(30, store.state.value.days)

        store.load(7)
        assertEquals(7, socket.sentOf("stats").last()["days"].toString().toInt())
        assertTrue(store.state.value.loading)

        socket.receive(stats(days = 7))
        settle()
        val state = store.state.value
        assertFalse(state.loading)
        assertEquals(7, state.stats?.days)
        assertEquals(3, state.stats?.sessions)
    }

    @Test
    fun `a reply for a period no longer selected is dropped`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val store = h.session.store<StatsStore>()
        store.load(7)
        store.load(30)

        socket.receive(stats(days = 7))
        settle()
        assertNull(store.state.value.stats)
        assertTrue(store.state.value.loading)

        socket.receive(stats(days = 30))
        settle()
        assertEquals(30, store.state.value.stats?.days)
    }

    @Test
    fun `today's answer feeds the usage rows whatever period is showing`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val usage = h.session.store<UsageStore>()
        socket.receive(stats(days = 1, sessions = 2))
        settle()
        assertEquals(2, usage.state.value.today?.sessions)
        assertNull(h.session.store<StatsStore>().state.value.stats)
    }

    @Test
    fun `a first load that never answers fails after 30 seconds, a reload just stops`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val store = h.session.store<StatsStore>()
        store.load(30)
        settle(30_000)
        assertFalse(store.state.value.loading)
        assertEquals("Couldn't load stats. Pull to refresh.", store.state.value.error)

        store.load(30)
        socket.receive(stats(days = 30))
        settle()
        store.load(30)
        settle(30_000)
        assertFalse(store.state.value.loading)
        assertNull(store.state.value.error)
    }

    @Test
    fun `a refusal lands only on a screen that is waiting`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val store = h.session.store<StatsStore>()
        socket.receive("""{"t":"stats","ok":false,"error":"No history"}""")
        settle()
        assertNull(store.state.value.error)

        store.load(30)
        socket.receive("""{"t":"stats","ok":false,"error":"No history"}""")
        settle()
        assertEquals("No history", store.state.value.error)
        assertFalse(store.state.value.loading)
    }

    @Test
    fun `a dropped link reissues the open screen's query`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val store = h.session.store<StatsStore>()
        store.load(7)
        socket.receive(stats(days = 7))
        settle()

        socket.fail()
        settle()
        assertTrue(store.state.value.loading)
        assertEquals(7, store.state.value.days)
    }

    @Test
    fun `a dropped link only drops a closed screen's spinner`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady().also { link = it }
        val store = h.session.store<StatsStore>()
        store.load(7)
        store.screenDidClose()

        socket.fail()
        settle()
        assertFalse(store.state.value.loading)
    }

    private companion object {
        const val HEARTBEAT_STEP_MS = 5_000L
    }
}
