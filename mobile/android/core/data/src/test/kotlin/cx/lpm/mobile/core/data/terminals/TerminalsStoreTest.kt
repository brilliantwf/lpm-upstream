package cx.lpm.mobile.core.data.terminals

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.TerminalInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalsStoreTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun FakeSocket.terminals(vararg ids: String) {
        val list = ids.joinToString(",") { """{"id":"$it","label":"$it","project":"storefront"}""" }
        receive("""{"t":"terminals","project":"storefront","terminals":[$list]}""")
    }

    private fun TerminalsStore.ids() = state.value.terminals["storefront"].orEmpty().map { it.id }

    @Test
    fun `a new terminal shows a placeholder until a new id lands`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.terminals("t1")
        settle()
        val store = h.terminals
        val spawned = mutableListOf<TerminalInfo>()
        store.newTerminal("storefront")
        assertTrue("storefront" in store.state.value.creatingTerminals)
        assertEquals(1, socket.sentOf("newTerminal").size)
        store.markTerminalCreating("storefront") { spawned += it }

        socket.terminals("t1")
        settle()
        assertTrue("storefront" in store.state.value.creatingTerminals)

        socket.terminals("t1", "t2")
        settle()
        assertFalse("storefront" in store.state.value.creatingTerminals)
        assertEquals(listOf("t2"), spawned.map { it.id })
    }

    @Test
    fun `a create that never lands gives up and reloads the list`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.terminals("t1")
        settle()
        val store = h.terminals
        store.newTerminal("storefront")
        // The polls of the list run until 12 s; the give-up comes at 14 s.
        settle(13_000)
        val polled = socket.sentOf("terminals").size
        assertTrue("storefront" in store.state.value.creatingTerminals)
        settle(1_001)
        assertFalse("storefront" in store.state.value.creatingTerminals)
        assertEquals(polled + 1, socket.sentOf("terminals").size)
    }

    @Test
    fun `a failed run stops the placeholder and surfaces the message`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.terminals
        store.newTerminal("storefront")
        socket.receive(
            """{"t":"newTerminal","ok":false,"project":"storefront","error":"No shell"}"""
        )
        settle()
        assertFalse("storefront" in store.state.value.creatingTerminals)
        assertEquals("No shell", h.session.alerts.alerts.value.actionError)
    }

    @Test
    fun `a closed terminal stays hidden until the Mac confirms, or a timeout reloads`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.terminals("t1", "t2")
        settle()
        val store = h.terminals
        store.closeTerminal("storefront", "t2")
        assertEquals(listOf("t1"), store.ids())
        assertEquals(1, socket.sentOf("closeTerminal").size)

        // A list from before the close can't flash the row back.
        socket.terminals("t1", "t2")
        settle()
        assertEquals(listOf("t1"), store.ids())

        // The close never happened: the timeout stops hiding it and reloads.
        settle(10_001)
        socket.terminals("t1", "t2")
        settle()
        assertEquals(listOf("t1", "t2"), store.ids())
    }

    @Test
    fun `a confirmed close ends the hiding`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.terminals("t1", "t2")
        settle()
        val store = h.terminals
        store.closeTerminal("storefront", "t2")
        socket.terminals("t1")
        settle()
        socket.terminals("t1", "t2")
        settle()
        assertEquals(listOf("t1", "t2"), store.ids())
    }

    @Test
    fun `control, endings and the stream go to the newest screen`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.terminals
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        fun hooks(sink: MutableList<String>) = TerminalHooks(
            seed = { _, _, data, _ -> sink += "seed:$data" },
            output = { sink += "out:$it" },
            submit = { sink += "submit:$it" },
            capture = { _, done -> done("") }
        )
        val keyA = Any()
        val keyB = Any()
        store.openTerminalScreen("t1", keyA, hooks(first))
        store.openTerminalScreen("t1", keyB, hooks(second))
        assertEquals(2, socket.sentOf("sub").size)

        socket.receive(
            """{"t":"seed","id":"t1","data":"screen",""" +
                """"owner":{"kind":"window","id":"w1","label":"lpm"}}"""
        )
        socket.receive("""{"t":"o","id":"t1","d":"more"}""")
        settle()
        assertEquals(listOf("seed:screen", "out:more"), second)
        assertTrue(first.isEmpty())
        assertFalse(store.isControlled("t1"))
        assertEquals("Studio Mac", store.controlOwnerLabel("t1"))

        assertTrue(store.submit("t1", "hello"))
        assertEquals("submit:hello", second.last())

        socket.receive("""{"t":"exit","id":"t1","code":2}""")
        settle()
        assertEquals("Process exited with code 2", store.state.value.endedTerminals["t1"]?.message)
        assertFalse(store.submit("t1", "again"))

        store.closeTerminalScreen("t1", keyB)
        assertEquals(3, socket.sentOf("sub").size)
        store.closeTerminalScreen("t1", keyA)
        assertEquals(1, socket.sentOf("unsub").size)
        assertNull(store.screens.current("t1"))
    }

    @Test
    fun `a queued prompt goes in once the terminal's screen shows content`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.terminals
        val sink = mutableListOf<String>()
        store.queuePrompt("t1", "explain this")
        store.openTerminalScreen(
            "t1",
            Any(),
            TerminalHooks(
                seed = { _, _, data, _ -> sink += "seed:$data" },
                output = { sink += "out:$it" },
                submit = { sink += "submit:$it" },
                capture = { _, done -> done("") }
            )
        )
        socket.receive("""{"t":"seed","id":"t1","data":"screen"}""")
        settle(1_000)
        assertEquals(listOf("seed:screen"), sink)
        store.contentShown("t1")
        settle(499)
        assertEquals(listOf("seed:screen"), sink)
        settle(1)
        assertEquals(listOf("seed:screen", "submit:explain this"), sink)

        store.contentShown("t1")
        settle(1_000)
        assertEquals("a prompt goes in once", listOf("seed:screen", "submit:explain this"), sink)
    }
}
