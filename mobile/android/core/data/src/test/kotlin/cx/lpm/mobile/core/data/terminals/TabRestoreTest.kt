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
class TabRestoreTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun tab(id: String, label: String) =
        TerminalInfo(id, label, "storefront", 80, 24, false, false, "", "")

    private fun FakeSocket.terminals(vararg tabs: Pair<String, String>) {
        val list = tabs.joinToString(",") { (id, label) ->
            """{"id":"$id","label":"$label","project":"storefront"}"""
        }
        receive("""{"t":"terminals","project":"storefront","terminals":[$list]}""")
    }

    @Test
    fun `an ending remembers the tab's name and place, and a later gone only adds to it`() {
        val listed =
            TerminalsState(
                terminals = mapOf("storefront" to listOf(tab("a", "web"), tab("b", "api")))
            )
        val ended = listed.recordingEnding("b", code = 0, gone = false, now = 5)
        assertEquals(
            TerminalEnding(0, false, label = "api", index = 1, at = 5),
            ended.endedTerminals["b"]
        )
        val again = ended.recordingEnding("b", code = -1, gone = true, now = 9)
        assertEquals(
            TerminalEnding(0, true, label = "api", index = 1, at = 5),
            again.endedTerminals["b"]
        )
        assertTrue(again.endedTerminals.getValue("b").replaceable)
    }

    @Test
    fun `only endings just before the link dropped count as lpm quitting`() {
        val s = TerminalsState(
            endedTerminals = mapOf(
                "recent" to TerminalEnding(0, false, at = 95_000),
                "old" to TerminalEnding(1, false, at = 80_000)
            )
        ).lostWithLink(now = 100_000)
        assertTrue(s.endedTerminals.getValue("recent").restarted)
        assertEquals("Closed when lpm restarted", s.endedTerminals.getValue("recent").message)
        assertFalse(s.endedTerminals.getValue("old").restarted)
        assertEquals("Process exited with code 1", s.endedTerminals.getValue("old").message)
    }

    @Test
    fun `the reopened tab is the one by the same name, in the old place when names repeat`() {
        val old = tab("old", "claude")
        val ending = TerminalEnding(0, gone = true, label = "claude", index = 2)
        fun restored(vararg list: TerminalInfo, end: TerminalEnding = ending) = TerminalsState(
            terminals = mapOf("storefront" to list.toList()),
            endedTerminals = mapOf("old" to end)
        ).restoredTab(old)
        assertEquals("n1", restored(tab("x", "web"), tab("n1", "claude"))?.id)
        assertEquals(
            "n3",
            restored(tab("n1", "claude"), tab("x", "web"), tab("n3", "claude"))?.id
        )
        assertNull(restored(old, tab("n1", "claude")))
        assertNull(restored(tab("n1", "claude"), end = TerminalEnding(0, gone = false)))
        assertNull(restored(tab("n1", "web")))
    }

    @Test
    fun `an exit right before the link drops reads as lpm restarting`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.terminals("t1" to "Terminal 1")
        settle()
        socket.receive("""{"t":"exit","id":"t1","code":143}""")
        settle(2_000)
        socket.fail()
        settle()
        val ending = h.terminals.state.value.endedTerminals.getValue("t1")
        assertTrue(ending.restarted)
        assertEquals("Terminal 1", ending.label)
    }

    @Test
    fun `an empty list from before a drop reads as loading again`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.terminals()
        settle()
        val store = h.terminals
        assertEquals(emptyList<TerminalInfo>(), store.state.value.terminals["storefront"])
        store.reloadTerminalsAfterReconnect("storefront")
        assertNull(store.state.value.terminals["storefront"])
        settle()
        assertEquals(1, socket.sentOf("terminals").size)

        socket.terminals("t1" to "Terminal 1")
        settle()
        store.reloadTerminalsAfterReconnect("storefront")
        assertEquals(1, store.state.value.terminals["storefront"]?.size)
    }

    @Test
    fun `a new tab tapped while the Mac is away waits for it`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.fail()
        settle()
        h.terminals.newTerminal("storefront")
        assertEquals(listOf("New tab"), h.session.link.state.value.queued.map { it.verb })
        assertFalse("storefront" in h.terminals.state.value.creatingTerminals)
    }
}
