package cx.lpm.mobile.core.data.session

import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.RemoteMachine
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private val linux = RemoteMachine(
        slug = "build-box",
        name = "Build Box",
        platform = "linux",
        serverId = "srv-linux",
        state = RemoteMachine.State.Ready,
        projects = null,
        relay = true
    )

    @Test
    fun `a machine opened through the live Mac gets a session and stores of its own`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        val visit = h.session.beginVisit(linux, record = null, project = "api")
        settle()

        assertEquals(MachineVisit("build-box", "api"), visit)
        val sessions = MacSessions(h.session)
        assertEquals(SessionKey.Visit("build-box"), visit?.session)
        val session = requireNotNull(sessions[SessionKey.Visit("build-box")])
        assertSame(h.session, sessions[SessionKey.Live])
        assertNull(sessions[SessionKey.Visit("another-box")])
        assertEquals(listOf(h.session, session), sessions.all)
        assertTrue(session.isVisit)
        assertNotSame(h.projects, session.store<ProjectsStore>())
        // It connects as the live Mac and asks to be carried on to the machine.
        val relay = h.transport.last.also { it.open() }
        settle()
        assertNotSame(live, relay)
        assertEquals("build-box", relay.sentOf("relay").single().string("slug"))
        // Nothing about it is saved on this phone.
        assertEquals(1, h.macStore.loadRecords().size)

        relay.receive("""{"t":"projects","projects":[{"name":"api"}]}""")
        settle()
        assertEquals(
            listOf("api"),
            session.store<ProjectsStore>().state.value.projects.map {
                it.name
            }
        )
        assertTrue(h.projects.state.value.projects.isEmpty())

        h.session.leaveVisit()
        settle(59_000)
        assertNotNull(h.session.visitSession.value)
        settle(1_001)
        assertNull(h.session.visitSession.value)
        assertNull(sessions[SessionKey.Visit("build-box")])
        assertTrue(relay.closed)
    }

    @Test
    fun `a visit is reachable through the registry by its key and gone once it ends`() = runTest {
        val h = SessionHarness(this)
        h.connectReady()
        val sessions = MacSessions(h.session)
        val key = SessionKey.Visit("build-box")
        assertNull(sessions[key])

        val visit = requireNotNull(h.session.beginVisit(linux, record = null, project = "api"))
        settle()
        val session = requireNotNull(sessions[visit.session])
        assertEquals(key, session.key)
        assertTrue(session.isVisit)

        h.session.endVisit()
        assertNull(sessions[key])
        assertSame(h.session, sessions[SessionKey.Live])
    }
}
