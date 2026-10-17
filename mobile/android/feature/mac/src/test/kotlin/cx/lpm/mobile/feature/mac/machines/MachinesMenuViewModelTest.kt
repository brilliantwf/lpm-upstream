package cx.lpm.mobile.feature.mac.machines

import cx.lpm.mobile.core.data.machines.MachineImportStatus
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.feature.mac.macSession
import cx.lpm.mobile.feature.mac.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MachinesMenuViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun aRemovedMachineIsPendingAndOfferedToPairDirectly() = runTest {
        val t = macSession()
        t.prefs.setString(PreferenceKeys.MACHINE_IMPORTS, """{"srv-1/box":"GONE"}""")
        val socket = t.connectReady()
        val vm = MachinesMenuViewModel(t.session)
        backgroundScope.launch { vm.uiState.collect() }
        socket.receive(
            """{"t":"machines","machines":[{"slug":"box","name":"Build Box","platform":"linux",""" +
                """"state":"ready","relay":true}]}"""
        )
        t.settle()

        val state = vm.uiState.value
        assertEquals("Studio Mac", state.macName)
        assertEquals(MachineImportStatus.Removed, state.pending.single().status)
        assertEquals("box", state.pairDirectly.single().id)

        vm.add("box")
        t.settle()
        assertEquals(MachineImportStatus.Adding, vm.uiState.value.pending.single().status)
        assertEquals(1, socket.sentOf("machinePair").size)
    }

    @Test
    fun aMachineThatRemovedThisPhoneIsPairedOnTheMachineItself() = runTest {
        val t = macSession()
        t.prefs.setString(PreferenceKeys.MACHINE_IMPORTS_REVOKED, """["srv-1/box"]""")
        val socket = t.connectReady()
        val vm = MachinesMenuViewModel(t.session)
        backgroundScope.launch { vm.uiState.collect() }
        socket.receive(
            """{"t":"machines","machines":[{"slug":"box","name":"Build Box","platform":"linux",""" +
                """"state":"ready","relay":true}]}"""
        )
        t.settle()

        val state = vm.uiState.value
        assertEquals(MachineImportStatus.Revoked, state.pending.single().status)
        assertEquals("box", state.pairDirectly.single().id)
        assertTrue(socket.sentOf("machinePair").isEmpty())

        vm.add("box")
        t.settle()
        assertTrue(t.session.state.value.addingMac)
        assertTrue(socket.sentOf("machinePair").isEmpty())
    }
}
