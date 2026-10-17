package cx.lpm.mobile.core.data.projects

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRestartTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun FakeSocket.projects(services: String) = receive(
        """{"t":"projects","projects":[{"name":"storefront","running":true,"services":[$services]}]}"""
    )

    private val ended = """{"name":"web"},{"name":"api","exited":true,"exitCode":1}"""

    @Test
    fun `a Mac that restarts services gets restart and stop, spinning until they land`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady(features = listOf(LpmClient.SERVICE_RESTART_FEATURE))
        socket.projects(ended)
        settle()
        val store = h.projects
        assertTrue(store.canRestartServices)
        assertFalse(store.state.value.serviceRunning("storefront", "api"))

        store.restartService("storefront", "api")
        assertEquals("api", socket.sentOf("restartService").single().string("service"))
        assertEquals(mapOf("api" to true), store.state.value.pendingServiceToggle["storefront"])
        socket.projects("""{"name":"web"},{"name":"api"}""")
        settle()
        assertEquals(
            emptyMap<String, Boolean>(),
            store.state.value.pendingServiceToggle["storefront"]
        )

        store.stopService("storefront", "web")
        assertEquals("web", socket.sentOf("stopService").single().string("service"))
        // Turning a running service off goes out as a stop, not the guessing toggle.
        store.toggleService("storefront", "api")
        assertEquals(2, socket.sentOf("stopService").size)
        assertTrue(socket.sentOf("toggleService").isEmpty())
    }

    @Test
    fun `an older Mac starts an ended service through the toggle and can't stop one`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(ended)
        settle()
        val store = h.projects
        assertFalse(store.canRestartServices)
        store.restartService("storefront", "api")
        assertEquals("api", socket.sentOf("toggleService").single().string("service"))
        assertTrue(socket.sentOf("restartService").isEmpty())

        store.restartService("storefront", "web")
        assertEquals(1, socket.sentOf("toggleService").size)
        store.stopService("storefront", "web")
        assertTrue(socket.sentOf("stopService").isEmpty())
        settle(12_001)
        assertEquals(
            emptyMap<String, Boolean>(),
            store.state.value.pendingServiceToggle["storefront"]
        )
    }

    @Test
    fun `a refused restart surfaces its error, a taken one re-asks for the list`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady(features = listOf(LpmClient.SERVICE_RESTART_FEATURE))
        socket.projects(ended)
        settle()
        val before = socket.sentOf("projects").size
        socket.receive("""{"t":"restartService","ok":true}""")
        settle()
        assertEquals(before + 1, socket.sentOf("projects").size)
        socket.receive("""{"t":"stopService","ok":false,"error":"No such service"}""")
        settle()
        assertEquals("No such service", h.session.alerts.alerts.value.actionError)
    }
}
