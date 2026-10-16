package cx.lpm.mobile.feature.connection

import cx.lpm.mobile.core.data.link.MacBrowser
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.core.testing.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditEndpointViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.editor(): Pair<TestSession, EditEndpointViewModel> {
        val t = TestSession(this)
        val record = t.savedMac(host = "192.168.1.20")
        t.connectReady(record)
        val vm = EditEndpointViewModel(SessionKey.Live, record.localId, t.sessions, t.browser)
        keepCollecting(vm.uiState)
        return t to vm
    }

    @Test
    fun startsFromTheSavedAddressesAndShowsTheOneInUse() = runTest {
        val (_, vm) = editor()
        assertEquals(listOf("192.168.1.20"), vm.hosts.map { it.text })
        assertEquals("8765", vm.port)
        assertEquals("192.168.1.20", vm.uiState.value.inUse)
        assertTrue(vm.canSave)
        assertTrue(vm.canFind)
    }

    @Test
    fun savesOnlyPlausibleAddressesAndAPort() = runTest {
        val (t, vm) = editor()
        vm.addHost()
        vm.setHost(vm.hosts.last().id, " 100.64.1.2 ")
        assertEquals(listOf("192.168.1.20", "100.64.1.2"), vm.cleanedHosts)
        vm.moveHost(1, 0)
        assertEquals(listOf("100.64.1.2", "192.168.1.20"), vm.cleanedHosts)

        vm.port = "99999"
        assertFalse(vm.canSave)
        vm.port = "9000"
        vm.setHost(vm.hosts.first().id, "http://x")
        assertFalse(vm.canSave)
        vm.setHost(vm.hosts.first().id, "100.64.1.2")
        assertTrue(vm.canSave)

        vm.removeHost(vm.hosts.last().id)
        vm.save()
        settle()
        val saved = t.session.state.value.activeRecord!!
        assertEquals(listOf("100.64.1.2"), saved.hosts)
        assertEquals(9000, saved.port)
    }

    @Test
    fun findsTheMacOnThisWiFi() = runTest {
        val (t, vm) = editor()
        val mac = MacBrowser.DiscoveredMac(
            "srv-1",
            "Studio Mac",
            isDev = false,
            requestPair = true,
            serviceName = "Studio"
        )
        t.browser.browse.value = MacBrowser.Browse(found = listOf(mac))
        t.browser.addresses["Studio"] = MacBrowser.Resolved("192.168.1.44", 8766)
        vm.find()
        settle()
        assertEquals(FindResult.Found("192.168.1.44"), vm.findResult)
        assertEquals("192.168.1.44", vm.hosts.first().text)
        assertEquals("8766", vm.port)
        assertFalse(vm.finding)
        assertFalse(t.browser.browsing)
    }

    @Test
    fun saysWhenItCannotFindTheMac() = runTest {
        val (t, vm) = editor()
        vm.find()
        assertTrue(vm.finding)
        assertTrue(t.browser.browsing)
        settle(10_000)
        assertEquals(FindResult.NotFound, vm.findResult)
        assertFalse(vm.finding)
    }
}
