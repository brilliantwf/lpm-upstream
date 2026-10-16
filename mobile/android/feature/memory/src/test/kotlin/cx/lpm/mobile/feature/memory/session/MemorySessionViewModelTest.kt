package cx.lpm.mobile.feature.memory.session

import cx.lpm.mobile.core.navigation.MemorySessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.memory.demoSession
import cx.lpm.mobile.feature.memory.memory
import cx.lpm.mobile.feature.memory.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemorySessionViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val key = MemorySessionKey("storefront", "checkout-redesign", "checkout-redesign")

    @Test
    fun `opening shows the list's title until the document lands`() = runTest {
        val t = demoSession()
        val vm = MemorySessionViewModel(key, t.sessions, t.clock)
        keepCollecting(vm.uiState)
        vm.open()
        assertEquals("checkout-redesign", vm.uiState.value.title)
        assertTrue(vm.uiState.value.loading)
        assertFalse(vm.uiState.value.canEdit)
        t.settle()
        val state = vm.uiState.value
        assertEquals("Checkout redesign", state.title)
        assertEquals("claude", state.agent)
        assertEquals("2 hours ago", state.updated)
        assertTrue(state.canEdit)
        assertTrue(state.content!!.contains("## Current state"))
    }

    @Test
    fun `deleting closes the page`() = runTest {
        val t = demoSession()
        val vm = MemorySessionViewModel(key, t.sessions, t.clock)
        keepCollecting(vm.uiState)
        vm.open()
        t.settle()
        vm.delete()
        assertTrue(vm.uiState.value.deleting)
        assertFalse(vm.uiState.value.canEdit)
        t.settle()
        assertTrue(vm.uiState.value.deleted)
    }

    @Test
    fun `an earlier delete of the same name doesn't close a new page`() = runTest {
        val t = demoSession()
        t.memory.delete("storefront", "checkout-redesign")
        t.settle()
        val vm = MemorySessionViewModel(key, t.sessions, t.clock)
        keepCollecting(vm.uiState)
        assertFalse(vm.uiState.value.deleted)
    }

    @Test
    fun `a session that's gone offers to try again`() = runTest {
        val t = demoSession()
        val vm =
            MemorySessionViewModel(
                MemorySessionKey("storefront", "gone", "gone"),
                t.sessions,
                t.clock
            )
        keepCollecting(vm.uiState)
        vm.open()
        t.settle()
        assertEquals("This session was removed.", vm.uiState.value.error)
        assertNull(vm.uiState.value.content)
        vm.retry()
        assertTrue(vm.uiState.value.loading)
    }
}
