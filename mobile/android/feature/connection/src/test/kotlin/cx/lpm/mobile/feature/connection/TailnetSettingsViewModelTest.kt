package cx.lpm.mobile.feature.connection

import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import cx.lpm.mobile.core.tailnet.TailnetStatus
import cx.lpm.mobile.core.testing.FakeTailscale
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.keepCollecting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TailnetSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun theSwitchTurnsTheNodeOnAndClearsTheLastFailure() = runTest {
        val tailscale = FakeTailscale()
        val vm = TailnetSettingsViewModel(tailscale)
        keepCollecting(vm.uiState)
        assertEquals("lpm-phone", vm.uiState.value.deviceName)
        tailscale.signInFailure = "No network"
        vm.signIn()
        assertEquals("No network", vm.uiState.value.failure)
        assertFalse(vm.uiState.value.busy)

        vm.setEnabled(false)
        assertFalse(vm.uiState.value.enabled)
        assertNull(vm.uiState.value.failure)
    }

    @Test
    fun signingInOpensThePageUntilItsConnected() = runTest {
        val tailscale = FakeTailscale()
        tailscale.signInPage = "https://login.tailscale.com/a/123"
        val vm = TailnetSettingsViewModel(tailscale)
        keepCollecting(vm.uiState)
        vm.signIn()
        assertTrue(vm.uiState.value.enabled)
        assertEquals("https://login.tailscale.com/a/123", vm.uiState.value.page)
        tailscale.show(TailnetStatus(state = "running", ip = "100.64.9.9"))
        assertNull(vm.uiState.value.page)
        assertEquals("100.64.9.9", vm.uiState.value.status.ip)
    }

    @Test
    fun theNodesOwnErrorShowsWhenThereIsNoNewerOne() = runTest {
        val tailscale = FakeTailscale()
        val vm = TailnetSettingsViewModel(tailscale)
        keepCollecting(vm.uiState)
        tailscale.setEnabled(true)
        tailscale.show(TailnetStatus(state = "error", error = "Couldn't start"))
        assertEquals("Couldn't start", vm.uiState.value.error)
        vm.openAdmin()
        assertEquals(BuiltInTailscale.ADMIN_URL, vm.uiState.value.page)
        vm.pageShown()
        assertNull(vm.uiState.value.page)
        vm.signOut()
        assertEquals(1, tailscale.signOuts)
    }

    @Test
    fun aSummaryWordForEachState() {
        assertEquals(TailscaleSummary.Off, tailscaleSummary(false, "running"))
        assertEquals(TailscaleSummary.Connected, tailscaleSummary(true, "running"))
        assertEquals(TailscaleSummary.Waiting, tailscaleSummary(true, "needsApproval"))
        assertEquals(TailscaleSummary.Problem, tailscaleSummary(true, "stopped"))
        assertEquals(TailscaleSummary.Connecting, tailscaleSummary(true, "starting"))
    }
}
