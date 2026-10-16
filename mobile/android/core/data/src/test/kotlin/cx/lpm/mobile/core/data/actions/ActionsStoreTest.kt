package cx.lpm.mobile.core.data.actions

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.model.bool
import cx.lpm.mobile.core.model.obj
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionsStoreTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun FakeSocket.output(
        runId: String,
        running: Boolean,
        text: String = "",
        error: String = ""
    ) = receive(
        """{"t":"actionBgOutput","ok":true,"found":true,"runId":"$runId",""" +
            """"project":"storefront",""" +
            """"label":"Test","startedAt":0,"text":"$text","running":$running,""" +
            """"success":${!running && error.isEmpty()},"error":"$error"}"""
    )

    private fun FakeSocket.notFound(runId: String) =
        receive("""{"t":"actionBgOutput","ok":true,"found":false,"runId":"$runId"}""")

    @Test
    fun `a background run starts on the Mac and shows before its first poll`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val runId = store.startBackground("storefront", "test", "Test", mapOf("env" to "prod"))
        val sent = socket.sentOf("runActionBackground").single()
        assertEquals(runId, sent.string("runId"))
        assertEquals("test", sent.string("action"))
        assertEquals("prod", sent.obj("inputValues")?.string("env"))
        val state = store.state.value
        assertEquals(listOf("Test"), state.runs("storefront").map { it.label })
        assertTrue(state.isRunning(runId))

        socket.output(runId, running = false, text = "ok")
        settle()
        assertFalse(store.state.value.isRunning(runId))
        assertEquals("ok", store.state.value.outputs[runId]?.text)
    }

    @Test
    fun `an open run is polled every 2 s while it runs and no longer once it ends`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val runId = store.startBackground("storefront", "test", "Test", emptyMap())
        val follow = backgroundScope.launch { store.follow("storefront", runId) }
        settle()
        assertEquals(1, socket.sentOf("actionBgOutput").size)
        settle(1_999)
        assertEquals(1, socket.sentOf("actionBgOutput").size)
        settle(1)
        assertEquals(2, socket.sentOf("actionBgOutput").size)
        socket.output(runId, running = true, text = "half")
        settle(2_000)
        assertEquals(3, socket.sentOf("actionBgOutput").size)

        socket.output(runId, running = false, text = "done")
        settle(10_000)
        assertEquals(3, socket.sentOf("actionBgOutput").size)
        assertTrue(follow.isCompleted)
    }

    @Test
    fun `a run the Mac reaped is dropped, but not one it may not have registered yet`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val runId = store.startBackground("storefront", "test", "Test", emptyMap())
        settle(3_000)
        socket.notFound(runId)
        settle()
        assertTrue(runId in store.state.value.runs)

        settle(8_000)
        socket.notFound(runId)
        settle()
        assertTrue(store.state.value.runs("storefront").isEmpty())
        assertFalse(store.state.value.isRunning(runId))
    }

    @Test
    fun `a refused start keeps its error up and isn't running`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val runId = store.startBackground("storefront", "test", "Test", emptyMap())
        socket.receive(
            """{"t":"runActionBackground","ok":false,"runId":"$runId",""" +
                """"error":"That action isn't available."}"""
        )
        settle(11_000)
        socket.notFound(runId)
        settle()
        assertEquals("That action isn't available.", store.state.value.startErrors[runId])
        assertTrue(runId in store.state.value.runs)
        assertFalse(store.state.value.isRunning(runId))
    }

    @Test
    fun `the Mac's list re-attaches runs started before and drops ones it reaped`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val mine = store.startBackground("storefront", "lint", "Lint", emptyMap())
        socket.output(mine, running = false)
        settle(1_000)
        store.loadRuns("storefront")
        assertEquals("storefront", socket.sentOf("backgroundRuns").single().string("project"))
        socket.receive(
            """{"t":"backgroundRuns","ok":true,"project":"storefront","runs":[""" +
                """{"runId":"older","label":"Deploy","startedAt":5,"running":true,""" +
                """"success":false,"error":""}]}"""
        )
        settle()
        val runs = store.state.value.runs("storefront")
        assertEquals(listOf("older"), runs.map { it.runId })
        assertEquals("Deploy", runs.single().label)
        assertNull(store.state.value.outputs[mine])
    }

    @Test
    fun `a shown project's running runs are polled every 3 s while the link is up`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val running = store.startBackground("storefront", "test", "Test", emptyMap())
        val finished = store.startBackground("storefront", "lint", "Lint", emptyMap())
        socket.output(finished, running = false)
        settle()
        backgroundScope.launch { store.watch("storefront") }
        settle()
        assertEquals(listOf(running), socket.sentOf("actionBgOutput").map { it.string("runId") })
        settle(3_000)
        assertEquals(2, socket.sentOf("actionBgOutput").size)

        socket.fail()
        settle()
        val before = socket.sentOf("actionBgOutput").size
        settle(9_000)
        assertEquals(before, socket.sentOf("actionBgOutput").size)
    }

    @Test
    fun `a terminal action runs confirmed and reports the terminal it spawned`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ActionsStore>()
        val spawned = mutableListOf<TerminalInfo>()
        socket.receive(
            """{"t":"terminals","project":"storefront","terminals":[{"id":"t1","label":"Shell"}]}"""
        )
        settle()
        store.runAction("storefront", "lint", mapOf("a" to "b"), confirmed = true) { spawned += it }
        val sent = socket.sentOf("runAction").single()
        assertEquals(true, sent.bool("confirmed"))
        assertEquals("b", sent.obj("inputValues")?.string("a"))
        assertTrue("storefront" in h.terminals.state.value.creatingTerminals)

        socket.receive(
            """{"t":"terminals","project":"storefront","terminals":[{"id":"t1",""" +
                """"label":"Shell"},{"id":"t2","label":"Lint"}]}"""
        )
        settle()
        assertEquals(listOf("t2"), spawned.map { it.id })
    }

    @Test
    fun `switching Macs forgets every run`() = runTest {
        val h = SessionHarness(this)
        h.connectReady()
        val store = h.session.store<ActionsStore>()
        store.startBackground("storefront", "test", "Test", emptyMap())
        store.reset()
        assertTrue(store.state.value.runs.isEmpty())
    }
}
