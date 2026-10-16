package cx.lpm.mobile.feature.mac.update

import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.feature.mac.checkAnswer
import cx.lpm.mobile.feature.mac.data.MacUpdatePhase
import cx.lpm.mobile.feature.mac.macSession
import cx.lpm.mobile.feature.mac.progress
import cx.lpm.mobile.feature.mac.readyOn
import cx.lpm.mobile.feature.mac.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MacUpdateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun theBarFollowsTheLinkExceptThroughTheRestart() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        val vm = MacUpdateViewModel(SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        socket.checkAnswer()
        t.settle()
        assertTrue(vm.uiState.value.showsBar)
        assertTrue(vm.uiState.value.canStartInstall)
        assertEquals("Studio Mac", vm.uiState.value.macLabel)

        vm.install()
        socket.progress("installing")
        t.settle()
        socket.fail()
        t.settle()
        val restarting = vm.uiState.value
        assertEquals(MacUpdatePhase.Restarting, restarting.update.phase)
        assertFalse(restarting.ready)
        assertTrue(restarting.showsBar)
    }

    @Test
    fun anOfferWaitsForTheLink() = runTest {
        val t = macSession()
        val socket = t.readyOn()
        val vm = MacUpdateViewModel(SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        socket.checkAnswer()
        t.settle()
        t.session.client?.disconnect()
        t.settle()
        assertFalse(vm.uiState.value.showsBar)
        assertFalse(vm.uiState.value.canStartInstall)
        assertFalse(vm.uiState.value.canCheckNow)
    }

    @Test
    fun anEndedVisitHasNoUpdate() = runTest {
        val t = macSession()
        t.readyOn()
        val vm = MacUpdateViewModel(SessionKey.Visit("gone"), t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        assertFalse(vm.uiState.value.showsBar)
        vm.install()
        assertEquals(emptyList<Any>(), vm.busyPlaces())
    }
}
