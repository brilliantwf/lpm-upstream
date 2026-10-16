package cx.lpm.mobile.feature.pairing

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddMacViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun cancellingReconnectsTheMacTheListShowed() = runTest {
        val t = TestSession(this)
        t.connectReady()
        val vm = AddMacViewModel(t.session)
        t.session.beginAddMac()
        assertTrue(vm.adding.value)

        // Pairing another Mac tears the live link down.
        t.session.pair(listOf("192.168.1.40"), 8765, "AB12")
        settle()
        vm.close()
        settle(5_000)
        assertFalse(vm.adding.value)
        assertFalse(t.session.link.state.value.pairing)
        assertEquals(LpmClient.State.Connecting, t.session.state.value.connection)
    }

    @Test
    fun theSheetGoingAwayOnItsOwnEndsItToo() = runTest {
        val t = TestSession(this)
        val vm = AddMacViewModel(t.session)
        t.session.beginAddMac()
        t.session.pair(listOf("192.168.1.40"), 8765, "AB12")
        settle()
        assertTrue(t.session.link.state.value.pairing)
        // A pairing that lands closes the sheet from the session's side.
        t.session.cancelAddMac()
        assertFalse(vm.adding.value)
        assertFalse(t.session.link.state.value.pairing)
    }
}
