package cx.lpm.mobile.feature.pairing

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.link.MacBrowser
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.session.ApprovalPairingState
import cx.lpm.mobile.core.data.session.RepairReason
import cx.lpm.mobile.core.tailnet.TailnetStatus
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.core.testing.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PairingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val studio = MacBrowser.DiscoveredMac(
        "srv-9",
        "Studio",
        isDev = false,
        requestPair = true,
        serviceName = "Studio"
    )
    private val server = MacBrowser.DiscoveredMac(
        "srv-8",
        "Server",
        isDev = false,
        requestPair = false,
        serviceName = "Server"
    )

    private fun TestScope.pairing(adding: Boolean = false): Pair<TestSession, PairingViewModel> {
        val t = TestSession(this)
        val vm = PairingViewModel(adding, t.session, t.tailscale, t.browser)
        keepCollecting(vm.uiState)
        return t to vm
    }

    @Test
    fun waysToPairWaitForTailscaleOrAChoice() = runTest {
        val (t, vm) = pairing()
        assertEquals(AwayPhase.NotSetUp, vm.uiState.value.awayPhase)
        assertFalse(vm.uiState.value.showsWaysToPair)

        vm.pairLocalOnly()
        assertEquals(AwayPhase.LocalOnly, vm.uiState.value.awayPhase)
        assertTrue(vm.uiState.value.showsWaysToPair)

        vm.useTailscaleApp()
        assertEquals(true, t.prefs.bool(PreferenceKeys.USES_TAILSCALE_APP))
        assertEquals(AwayPhase.App, vm.uiState.value.awayPhase)

        vm.useBuiltInInstead()
        assertEquals(false, t.prefs.bool(PreferenceKeys.USES_TAILSCALE_APP))
        assertTrue(t.session.link.state.value.tailscaleSettingsOpen)
        t.tailscale.setEnabled(true)
        // Pairing for this Wi-Fi was already chosen, and it still stands.
        assertEquals(AwayPhase.LocalOnly, vm.uiState.value.awayPhase)
        t.tailscale.show(TailnetStatus(state = "running"))
        assertEquals(AwayPhase.Ready, vm.uiState.value.awayPhase)
        assertTrue(vm.uiState.value.worksAnywhere)
    }

    @Test
    fun aHomeOnlyCodeWaitsForConsentThenPairs() = runTest {
        val (t, vm) = pairing()
        val payload = PairPayload(listOf("192.168.1.20"), 8765, "AB12", null)
        vm.scanned(payload)
        assertEquals(payload, vm.uiState.value.heldScan)
        assertTrue(t.transport.sockets.isEmpty())

        vm.pairHeldScan()
        settle()
        assertNull(vm.uiState.value.heldScan)
        assertTrue(t.session.link.state.value.pairing)
        assertEquals(LpmClient.State.Connecting, t.session.state.value.connection)
        assertTrue(t.transport.last.url.contains("192.168.1.20"))
    }

    @Test
    fun aCodeThatWorksAwayPairsAtOnce() = runTest {
        val (t, vm) = pairing()
        vm.scanned(PairPayload(listOf("100.64.1.2"), 8765, "AB12", "ab"))
        settle()
        assertNull(vm.uiState.value.heldScan)
        assertTrue(t.session.link.state.value.pairing)
    }

    @Test
    fun tryAgainRepeatsTheLastScanOrOpensTheScanner() = runTest {
        val (t, vm) = pairing()
        assertTrue(vm.handle(IssueAction.Retry))
        assertTrue(vm.uiState.value.scanning)
        vm.scanClosed()

        vm.pairLocalOnly()
        vm.scanned(PairPayload(listOf("192.168.1.20"), 8765, "AB12", null))
        settle()
        val sockets = t.transport.sockets.size
        assertTrue(vm.handle(IssueAction.Retry))
        settle()
        assertEquals(sockets + 1, t.transport.sockets.size)
        assertFalse(vm.uiState.value.scanning)

        assertTrue(vm.handle(IssueAction.EnterNewCode))
        assertTrue(vm.uiState.value.scanning)
        assertTrue(vm.handle(IssueAction.Details))
        assertTrue(vm.uiState.value.showDetails)
        assertFalse(vm.handle(IssueAction.PairAgain))
    }

    @Test
    fun aNearbyMacPairsByApproval() = runTest {
        val (t, vm) = pairing()
        t.browser.addresses[studio.serviceName] = MacBrowser.Resolved("192.168.1.30", 8765)
        vm.selectNearby(studio)
        settle()
        assertEquals("Studio", vm.uiState.value.approvalMacName)
        assertEquals(ApprovalPairingState.Requesting, vm.uiState.value.approval)

        vm.enterCodeInstead()
        assertNull(vm.uiState.value.approval)
        assertEquals("192.168.1.30", vm.uiState.value.codeTarget?.host)
        vm.codeTargetShown()
        assertNull(vm.uiState.value.codeTarget)
    }

    @Test
    fun aHeadlessHostGoesToTheTypedCode() = runTest {
        val (t, vm) = pairing()
        t.browser.addresses[server.serviceName] = MacBrowser.Resolved("10.0.0.5", 9000)
        vm.selectNearby(server)
        settle()
        assertNull(vm.uiState.value.approval)
        assertEquals("10.0.0.5", vm.uiState.value.codeTarget?.host)
        assertEquals(9000, vm.uiState.value.codeTarget?.port)
    }

    @Test
    fun aNearbyMacThatDoesNotResolveSaysSo() = runTest {
        val (_, vm) = pairing()
        vm.selectNearby(studio)
        settle()
        assertEquals(studio, vm.uiState.value.unreachable)
        assertNull(vm.uiState.value.resolvingId)
        vm.dismissUnreachable()
        assertNull(vm.uiState.value.unreachable)
    }

    @Test
    fun pairingASavedMacAgainListsItFirstAndKeepsItTappable() = runTest {
        val (t, vm) = pairing(adding = true)
        val other = t.savedMac(serverId = "srv-8", name = "Server")
        val record = t.savedMac(serverId = "srv-9")
        t.session.bootstrap()
        t.session.repairMac(record.localId, RepairReason.NotRecognized)
        t.browser.browse.value = MacBrowser.Browse(found = listOf(server, studio))
        val state = vm.uiState.value
        assertEquals(record.localId, state.repair?.localId)
        assertEquals(RepairReason.NotRecognized, state.repairReason)
        assertTrue(state.showsWaysToPair)
        assertEquals(listOf(studio, server), state.nearby)
        assertEquals(setOf(other.serverId), state.pairedServerIds)
    }

    @Test
    fun discoveryRunsWhileTheScreenIsUp() = runTest {
        val (t, vm) = pairing()
        vm.startDiscovery()
        assertTrue(t.browser.browsing)
        vm.stopDiscovery()
        assertFalse(t.browser.browsing)
    }

    @Test
    fun theDemoIsOneTapAway() = runTest {
        val (t, vm) = pairing()
        vm.enterDemo()
        settle(500)
        assertTrue(t.session.state.value.demoMode)
        assertNotNull(t.session.state.value.activeRecord)
    }

    @Test
    fun cancellingAddAMachineEndsIt() = runTest {
        val (t, vm) = pairing(adding = true)
        t.session.beginAddMac()
        assertTrue(t.session.state.value.addingMac)
        vm.cancelAdding()
        assertFalse(t.session.state.value.addingMac)
    }
}
