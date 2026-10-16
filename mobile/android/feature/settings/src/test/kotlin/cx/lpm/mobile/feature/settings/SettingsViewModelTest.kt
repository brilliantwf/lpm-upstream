package cx.lpm.mobile.feature.settings

import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.NotificationPrefs
import cx.lpm.mobile.core.model.AppearanceMode
import cx.lpm.mobile.core.tailnet.TailnetStatus
import cx.lpm.mobile.core.testing.FakeTailscale
import cx.lpm.mobile.core.testing.InMemoryDataStore
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

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the theme follows the system until one is picked, under appearanceMode`() = runTest {
        val prefs = AppPreferences(InMemoryDataStore(), backgroundScope)
        val vm = SettingsViewModel(prefs, FakeTailscale())
        keepCollecting(vm.uiState)
        assertEquals(AppearanceMode.System, vm.uiState.value.appearance)

        vm.setAppearance(AppearanceMode.Dark)
        assertEquals("dark", prefs.string("appearanceMode"))
        assertEquals(AppearanceMode.Dark, vm.uiState.value.appearance)
    }

    @Test
    fun `the Tailscale row sums up the node`() = runTest {
        val tailscale = FakeTailscale()
        val vm = SettingsViewModel(AppPreferences(InMemoryDataStore(), backgroundScope), tailscale)
        keepCollecting(vm.uiState)
        assertEquals(TailscaleSummary.Off, vm.uiState.value.tailscale)

        tailscale.setEnabled(true)
        tailscale.show(TailnetStatus(state = "starting"))
        assertEquals(TailscaleSummary.Connecting, vm.uiState.value.tailscale)
        tailscale.show(TailnetStatus(state = "running"))
        assertEquals(TailscaleSummary.Connected, vm.uiState.value.tailscale)
    }

    @Test
    fun `each node state reads as iOS words it`() {
        assertEquals(TailscaleSummary.Off, tailscaleSummary(false, "running"))
        assertEquals(TailscaleSummary.SignIn, tailscaleSummary(true, "needsLogin"))
        assertEquals(TailscaleSummary.Waiting, tailscaleSummary(true, "needsApproval"))
        assertEquals(TailscaleSummary.Problem, tailscaleSummary(true, "error"))
        assertEquals(TailscaleSummary.Problem, tailscaleSummary(true, "stopped"))
        assertEquals(TailscaleSummary.Connecting, tailscaleSummary(true, "off"))
    }

    @Test
    fun `notification changes are stored and say whether the Mac hears them now`() = runTest {
        val t = TestSession(this)
        val vm = NotificationSettingsViewModel(t.prefs, t.session)
        keepCollecting(vm.uiState)
        assertEquals(NotificationPrefs(), vm.uiState.value.prefs)
        assertFalse(vm.uiState.value.connected)

        vm.update { it.copy(enabled = false, automationStarted = true) }
        assertEquals(
            NotificationPrefs(enabled = false, automationStarted = true),
            vm.uiState.value.prefs
        )
        assertEquals(false, t.prefs.bool("lpm.notify.enabled"))
        assertEquals(true, t.prefs.bool("lpm.notify.waiting"))

        t.enterDemo()
        settle(1_000)
        assertTrue(vm.uiState.value.connected)
    }
}
