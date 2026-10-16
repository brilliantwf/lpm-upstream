package cx.lpm.mobile.feature.pairing

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.core.testing.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnterCodeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun opensWithTheAddressItWasGiven() = runTest {
        val t = TestSession(this)
        val vm = EnterCodeViewModel("10.0.0.5", 9000, t.session)
        assertEquals(EnterCodeForm(host = "10.0.0.5", port = "9000"), vm.form)
    }

    @Test
    fun pairsWithTheCleanedCodeAtTheTypedAddress() = runTest {
        val t = TestSession(this)
        val vm = EnterCodeViewModel("", 8765, t.session)
        keepCollecting(vm.uiState)
        assertFalse(vm.pair())

        vm.form = vm.form.copy(code = "ab12 cd34", host = "192.168.1.20:9000")
        assertTrue(vm.pair())
        settle()
        assertTrue(vm.uiState.value.pairing)
        assertEquals(LpmClient.State.Connecting, t.session.state.value.connection)
        val socket = t.transport.last
        assertTrue(socket.url.contains("192.168.1.20:9000"))
        socket.open()
        settle()
        assertEquals("AB12CD34", socket.sentOf("pair").single().string("code"))
    }

    @Test
    fun aNewCodeStartsTheCodeOver() = runTest {
        val t = TestSession(this)
        val vm = EnterCodeViewModel("a", 8765, t.session)
        keepCollecting(vm.uiState)
        vm.form = vm.form.copy(code = "OLD")
        assertTrue(vm.handle(IssueAction.EnterNewCode))
        assertEquals("", vm.form.code)
        assertTrue(vm.handle(IssueAction.Details))
        assertTrue(vm.uiState.value.showDetails)
        assertFalse(vm.handle(IssueAction.OpenAdmin))
    }
}
