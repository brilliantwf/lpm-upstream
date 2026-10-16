package cx.lpm.mobile.feature.mac.data

import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.model.BusyAgentPlace
import cx.lpm.mobile.core.model.bool
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.mac.checkAnswer
import cx.lpm.mobile.feature.mac.macSession
import cx.lpm.mobile.feature.mac.progress
import cx.lpm.mobile.feature.mac.readyOn
import cx.lpm.mobile.feature.mac.reconnectOn
import cx.lpm.mobile.feature.mac.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacUpdateStoreTest {
    private val TestSession.update: MacUpdateStore get() = store()

    @Test
    fun `a Mac that updates itself is asked for a newer release, which brings the bar`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        assertEquals(1, socket.sentOf("updateCheck").size)
        assertNull(socket.sentOf("updateCheck").single().bool("force"))

        socket.checkAnswer()
        t.settle()
        val state = t.update.state.value
        assertEquals("0.6.10", state.version)
        assertEquals("0.6.12", state.newer)
        assertTrue(state.canInstall)
        assertTrue(state.showsBar)
    }

    @Test
    fun `a Mac that can't update itself shows no bar and isn't asked`() = runTest {
        val t = macSession()
        val socket = t.readyOn(selfUpdate = false)
        assertTrue(socket.sentOf("updateCheck").isEmpty())
        val state = t.update.state.value
        assertEquals("0.6.10", state.version)
        assertFalse(state.canUpdate)
        assertFalse(state.showsBar)
        assertFalse(state.canCheck)
    }

    @Test
    fun `closing the bar keeps it away for that release until a check finds one`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer()
        t.settle()
        t.update.dismiss()
        assertFalse(t.update.state.value.showsBar)
        assertEquals("0.6.12", t.prefs.string(PreferenceKeys.macUpdateDismissed("srv-1")))

        t.update.checkNow()
        t.settle()
        assertEquals(MacUpdateLookup.Checking, t.update.state.value.lookup)
        assertEquals(true, socket.sentOf("updateCheck").last().bool("force"))
        socket.checkAnswer()
        t.settle()
        val state = t.update.state.value
        assertNull(state.lookup)
        assertNull(state.dismissed)
        assertTrue(state.showsBar)
        assertNull(t.prefs.string(PreferenceKeys.macUpdateDismissed("srv-1")))
    }

    @Test
    fun `a check that finds nothing says so for a few seconds`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer(latest = "0.6.10", available = false)
        t.settle()
        assertFalse(t.update.state.value.showsBar)

        t.update.checkNow()
        socket.checkAnswer(latest = "0.6.10", available = false)
        t.settle()
        assertEquals(MacUpdateLookup.UpToDate, t.update.state.value.lookup)
        assertTrue(t.update.state.value.showsBar)
        t.settle(5_001)
        assertNull(t.update.state.value.lookup)
        assertFalse(t.update.state.value.showsBar)
    }

    @Test
    fun `a failed check keeps its reason until closed`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer(latest = "0.6.10", available = false)
        t.settle()
        t.update.checkNow()
        socket.receive(
            """{"t":"updateCheck","ok":false,"error":"GitHub didn't answer.","phase":"idle"}"""
        )
        t.settle()
        assertEquals(MacUpdateLookup.Failed("GitHub didn't answer."), t.update.state.value.lookup)
        t.update.dismiss()
        assertNull(t.update.state.value.lookup)
    }

    @Test
    fun `an install follows the Mac to the restart and lands on the new release`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer()
        t.settle()
        t.update.install()
        t.settle()
        assertEquals(1, socket.sentOf("updateInstall").size)
        assertEquals(MacUpdatePhase.Checking, t.update.state.value.phase)
        assertFalse(t.update.state.value.canInstall)

        socket.progress("downloading", 40)
        t.settle()
        assertEquals(MacUpdatePhase.Downloading(40), t.update.state.value.phase)
        socket.progress("installing")
        t.settle()
        assertEquals(MacUpdatePhase.Installing, t.update.state.value.phase)
        assertFalse(t.update.state.value.phase!!.cancellable)

        socket.fail()
        t.settle()
        assertEquals(MacUpdatePhase.Restarting, t.update.state.value.phase)

        t.reconnectOn("0.6.12")
        val state = t.update.state.value
        assertEquals(MacUpdatePhase.Updated("0.6.12"), state.phase)
        assertNull(state.newer)
        assertEquals("0.6.12", state.version)
        t.settle(8_001)
        assertNull(t.update.state.value.phase)
    }

    @Test
    fun `back on the old release after the restart, the install didn't finish`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer()
        t.settle()
        t.update.install()
        socket.progress("installing")
        t.settle()
        socket.fail()
        t.settle()

        t.reconnectOn("0.6.10").checkAnswer()
        t.settle()
        val state = t.update.state.value
        assertNull(state.phase)
        assertEquals("The update didn't finish on Studio Mac. Try again.", state.error)
        assertEquals("Studio Mac", t.session.state.value.activeRecord?.displayName)
        assertTrue(state.showsBar)
        assertEquals("The update didn't finish on Studio Mac. Try again.", state.failure)
    }

    @Test
    fun `a restart that never comes back stops being reported`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer()
        t.settle()
        t.update.install()
        socket.progress("installing")
        t.settle()
        t.session.client?.disconnect()
        t.settle()
        assertEquals(MacUpdatePhase.Restarting, t.update.state.value.phase)
        t.settle(180_001)
        assertNull(t.update.state.value.phase)
    }

    @Test
    fun `cancelling waits for the Mac and ends with the install`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer()
        t.settle()
        t.update.install()
        socket.progress("downloading", 10)
        t.settle()
        t.update.cancel()
        t.update.cancel()
        assertEquals(1, socket.sentOf("updateCancel").size)
        assertTrue(t.update.state.value.cancelling)

        socket.receive("""{"t":"updateCancel","ok":true,"stopping":true}""")
        socket.receive("""{"t":"updateInstall","ok":true,"cancelled":true}""")
        t.settle()
        val state = t.update.state.value
        assertNull(state.phase)
        assertFalse(state.cancelling)
        assertTrue(state.canInstall)
    }

    @Test
    fun `an install the Mac refuses shows why`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer()
        t.settle()
        t.update.install()
        socket.receive("""{"t":"updateInstall","ok":false,"error":"Another install is running."}""")
        t.settle()
        val state = t.update.state.value
        assertNull(state.phase)
        assertEquals("Another install is running.", state.failure)
        t.update.dismiss()
        assertNull(t.update.state.value.error)
        assertTrue(t.update.state.value.showsBar)
    }

    @Test
    fun `an install started elsewhere is followed too`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.checkAnswer(phase = "downloading", progress = 70)
        t.settle()
        assertEquals(MacUpdatePhase.Downloading(70), t.update.state.value.phase)
        socket.progress("idle")
        t.settle()
        assertNull(t.update.state.value.phase)
        assertNull(t.update.state.value.error)
    }

    @Test
    fun `Demo Mode has no lpm to update`() = runTest {
        val t = macSession()
        t.enterDemo()
        t.settle(1_000)
        assertNull(t.update.state.value.version)
        assertFalse(t.update.state.value.showsBar)
    }

    @Test
    fun `busy places are the projects with agents mid-task`() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        socket.receive(
            """{"t":"projects","projects":[{"name":"web","label":"Web","statusEntries":[""" +
                """{"key":"claude_code_1","value":"Running"},""" +
                """{"key":"codex_1","value":"Waiting"},{"key":"gemini_1","value":"Done"}]},""" +
                """{"name":"api","statusEntries":[{"key":"claude_code_2","value":"Done"}]}]}"""
        )
        t.settle()
        assertEquals(listOf(BusyAgentPlace("web", "Web", 2)), t.update.busyPlaces)
    }
}
