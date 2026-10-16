package cx.lpm.mobile.core.testing

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.demo.DemoServer
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSessionTest {
    @Test
    fun connectsToASavedMac() = runTest {
        val t = TestSession(this)
        val socket = t.connectReady()
        assertEquals(LpmClient.State.Ready, t.session.state.value.connection)
        assertEquals("auth", socket.sentTypes.first())
        assertSame(t.session, t.sessions[SessionKey.Live])
    }

    @Test
    fun entersDemoMode() = runTest {
        val t = TestSession(this)
        t.enterDemo()
        val s = t.session.state.value
        assertTrue(s.demoMode)
        assertEquals(DemoServer.SERVER_NAME, s.activeRecord?.name)
    }
}
