package cx.lpm.mobile.feature.memory.list

import cx.lpm.mobile.core.navigation.MemoryKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.memory.demoSession
import cx.lpm.mobile.feature.memory.liveSession
import cx.lpm.mobile.feature.memory.memoryReply
import cx.lpm.mobile.feature.memory.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.list(t: TestSession, project: String = "storefront"): MemoryViewModel {
        val vm = MemoryViewModel(MemoryKey(project), t.sessions, t.clock)
        keepCollecting(vm.uiState)
        vm.open()
        return vm
    }

    @Test
    fun `the storefront lists its sessions with their goal and last agent`() = runTest {
        val t = demoSession()
        val vm = list(t)
        assertTrue(vm.uiState.value.loading)
        t.settle()
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.shared)
        assertEquals("storefront", state.ownerLabel)
        val first = state.rows.first()
        assertEquals("Checkout redesign", first.title)
        assertEquals("claude · 2 hours ago", first.stamp)
        assertTrue(first.subtitle.startsWith("Rebuild checkout as a two-column flow"))
        assertEquals(3, state.rows.size)
        assertEquals("Payments module", state.titleOf("payments-module"))
        assertEquals("unknown", state.titleOf("unknown"))
    }

    @Test
    fun `a project with no memory yet shows the empty state`() = runTest {
        val t = demoSession()
        val vm = list(t, project = "blog")
        t.settle()
        val state = vm.uiState.value
        assertTrue(state.rows.isEmpty())
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `a copy names its original`() = runTest {
        val t = liveSession()
        val socket = t.connectReady()
        val vm = list(t, project = "storefront-2")
        socket.receive(memoryReply("storefront-2", owner = "storefront", names = arrayOf("a")))
        t.settle(0)
        assertTrue(vm.uiState.value.shared)
        assertEquals("storefront", vm.uiState.value.ownerLabel)
    }

    @Test
    fun `deleting removes the row, and a refused delete is shown until dismissed`() = runTest {
        val t = demoSession()
        val vm = list(t)
        t.settle()
        vm.delete("cart-badge-bug")
        t.settle()
        assertEquals(2, vm.uiState.value.rows.size)
        assertNull(vm.uiState.value.deleteError)

        val live = liveSession()
        val socket = live.connectReady()
        val other = list(live)
        other.delete("a")
        socket.receive(
            """{"t":"memoryDelete","project":"storefront","name":"a","ok":false,"error":"Locked"}"""
        )
        live.settle(0)
        assertEquals("Locked", other.uiState.value.deleteError)
        other.dismissDeleteError()
        assertNull(other.uiState.value.deleteError)
    }

    @Test
    fun `a Mac that doesn't answer leaves the failure to retry`() = runTest {
        val t = liveSession()
        val socket = t.connectReady()
        val vm = list(t)
        t.settle(25_000)
        assertEquals("Couldn't reach your Mac. Pull to refresh.", vm.uiState.value.error)
        vm.retry()
        assertEquals(2, socket.sentOf("memory").size)
    }
}
