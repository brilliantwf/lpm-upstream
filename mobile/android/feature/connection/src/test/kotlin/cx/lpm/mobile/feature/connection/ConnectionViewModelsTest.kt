package cx.lpm.mobile.feature.connection

import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.link.PairedSummary
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.session.RepairReason
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.tailnet.TailnetStatus
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.core.testing.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectionViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun theSheetsFollowTheLinkAndRunWhatWaitsForThemToClose() = runTest {
        val t = TestSession(this)
        val vm = ConnectionSheetsViewModel(SessionKey.Live, t.sessions)
        keepCollecting(vm.uiState)
        t.session.link.update { it.copy(sheetOpen = true, summary = PairedSummary("mac")) }
        assertTrue(vm.uiState.value.details)
        assertEquals("mac", vm.uiState.value.summary?.macId)

        var ran = false
        t.session.link.afterSheetCloses { ran = true }
        vm.closed { it.copy(sheetOpen = false) }
        assertFalse(vm.uiState.value.details)
        assertTrue(ran)

        vm.closeSummary()
        assertNull(vm.uiState.value.summary)
    }

    @Test
    fun theConnectionSheetListsTheTailscaleAddressFirst() = runTest {
        val t = TestSession(this)
        val record = t.savedMac(host = "192.168.1.20")
        t.macStore.saveRecords(
            listOf(record.copy(hosts = listOf("192.168.1.20", "studio.local", "100.64.1.2")))
        )
        t.prober.reachable += "192.168.1.20"
        t.connectReady(record)
        val vm = ConnectionSheetViewModel(SessionKey.Live, t.sessions, t.tailscale, t.device)
        keepCollecting(vm.uiState)
        val state = vm.uiState.value
        assertEquals(listOf("100.64.1.2", "192.168.1.20", "studio.local"), state.hosts)
        assertTrue(state.hasAwayAddress)
        assertEquals(TailscaleSummary.Off, state.tailscale)
        assertTrue(state.link?.isReady == true)

        t.tailscale.setEnabled(true)
        t.tailscale.show(TailnetStatus(state = "needsLogin"))
        assertEquals(TailscaleSummary.SignIn, vm.uiState.value.tailscale)
    }

    @Test
    fun testingTheAddressesRecordsWhichAnswer() = runTest {
        val t = TestSession(this)
        val record = t.savedMac()
        t.macStore.saveRecords(listOf(record.copy(hosts = listOf("192.168.1.20", "100.64.1.2"))))
        t.prober.reachable += "100.64.1.2"
        t.connectReady(record)
        val vm = ConnectionSheetViewModel(SessionKey.Live, t.sessions, t.tailscale, t.device)
        keepCollecting(vm.uiState)
        vm.testAll()
        settle()
        val checks = vm.uiState.value.link?.link?.checks.orEmpty()
        assertTrue(checks.getValue("100.64.1.2").reachable)
        assertFalse(checks.getValue("192.168.1.20").reachable)
    }

    @Test
    fun renamingAndPairingAgainGoThroughTheSession() = runTest {
        val t = TestSession(this)
        val record = t.connectReady().let { t.session.state.value.activeRecord!! }
        val vm = ConnectionSheetViewModel(SessionKey.Live, t.sessions, t.tailscale, t.device)
        keepCollecting(vm.uiState)
        vm.rename(record.localId, "Office")
        assertEquals("Office", vm.uiState.value.record?.displayName)

        vm.pairAgainAfterClose()
        assertNull(t.session.link.state.value.repairMacId)
        t.session.link.runAfterSheet()
        assertEquals(record.localId, t.session.link.state.value.repairMacId)
        assertEquals(RepairReason.Chosen, t.session.link.state.value.repairReason)
        assertTrue(t.session.state.value.addingMac)
    }

    @Test
    fun onlyActionsThatOpenASheetWaitForThisOne() {
        assertTrue(ConnectionSheetViewModel.opensSheet(IssueAction.CompareCode))
        assertTrue(ConnectionSheetViewModel.opensSheet(IssueAction.TailscaleSettings))
        assertFalse(ConnectionSheetViewModel.opensSheet(IssueAction.Retry))
        assertFalse(ConnectionSheetViewModel.opensSheet(IssueAction.OpenAdmin))
    }

    @Test
    fun theIdentityCheckTrustsOrRefusesTheNewIdentity() = runTest {
        val t = TestSession(this)
        t.connectReady()
        t.session.link.update {
            it.copy(newFingerprint = "a1b2c3d4e5f6a7b8", identityCheckOpen = true)
        }
        val vm = IdentityCheckViewModel(SessionKey.Live, t.sessions)
        keepCollecting(vm.uiState)
        assertEquals("A1B2 · C3D4 · E5F6", vm.uiState.value.code)
        assertEquals("Studio Mac", vm.uiState.value.macLabel)

        vm.reject()
        assertTrue(t.session.link.state.value.identityRejected)
        assertFalse(t.session.link.state.value.identityCheckOpen)
    }

    @Test
    fun theSummarySaysWhatAwayFromHomeNeeds() {
        val home =
            MacRecord(localId = "a", name = "Studio", hosts = listOf("192.168.1.20"), port = 8765)
        val away = home.copy(hosts = listOf("192.168.1.20", "100.64.1.2"))
        val off = TailnetStatus()
        val running = TailnetStatus(state = "running")
        assertEquals(AwayState.MacNotSetUp, awayState(null, false, off, false))
        assertEquals(AwayState.MacNotSetUp, awayState(home, true, running, false))
        assertEquals(AwayState.TurnOnHere, awayState(away, false, off, false))
        assertEquals(AwayState.App, awayState(away, false, off, true))
        assertEquals(
            AwayState.FinishHere,
            awayState(away, true, TailnetStatus(state = "needsLogin"), false)
        )
        assertEquals(AwayState.Ready, awayState(away, true, running, false))
    }

    @Test
    fun machinesSayWhichAnswerAndSwitch() = runTest {
        val t = TestSession(this)
        val first = t.savedMac(serverId = "srv-1", name = "Studio")
        val second = t.savedMac(host = "192.168.1.21", serverId = "srv-2", name = "Office")
        t.connectReady(second)
        val vm = ManageMacsViewModel(t.session)
        keepCollecting(vm.uiState)
        settle()
        assertEquals(listOf("Studio", "Office"), vm.uiState.value.macs.map { it.displayName })
        assertEquals(second.localId, vm.uiState.value.activeMacId)

        vm.switchTo(vm.uiState.value.record(first.localId)!!)
        assertEquals(first.localId, t.session.state.value.activeMacId)
        assertFalse(t.session.link.state.value.manageOpen)
    }
}
