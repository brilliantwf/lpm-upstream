package cx.lpm.mobile.feature.memory.create

import cx.lpm.mobile.core.model.SessionKey
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

class MemoryCreateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the title becomes the slug and a skeleton document`() = runTest {
        val t = demoSession()
        t.memory.load("storefront")
        t.settle()
        val vm = MemoryCreateViewModel("storefront", SessionKey.Live, t.sessions)
        keepCollecting(vm.uiState)
        vm.setTitle("  Release notes, v2! ")
        assertEquals("release-notes-v2", vm.uiState.value.slug)
        assertFalse(vm.uiState.value.exists)
        vm.create()
        assertTrue(vm.uiState.value.saving)
        t.settle()
        assertEquals("release-notes-v2", vm.uiState.value.opened)
        assertNull(t.memory.state.value.createdName["storefront"])
        t.memory.openSession("storefront", "release-notes-v2")
        t.settle()
        assertEquals(
            "# Release notes, v2!\n\n## Goal\n\n\n## Current state\n\n\n## Timeline\n",
            t.memory.state.value.document("storefront", "release-notes-v2")?.content
        )
    }

    @Test
    fun `an existing name opens that session instead of overwriting it`() = runTest {
        val t = demoSession()
        t.memory.load("storefront")
        t.settle()
        val vm = MemoryCreateViewModel("storefront", SessionKey.Live, t.sessions)
        keepCollecting(vm.uiState)
        vm.setTitle("Checkout redesign")
        assertTrue(vm.uiState.value.exists)
        vm.create()
        assertEquals("checkout-redesign", vm.uiState.value.opened)
        assertTrue(t.memory.state.value.saving.isEmpty())
    }

    @Test
    fun `a title with nothing usable can't create`() = runTest {
        val t = demoSession()
        val vm = MemoryCreateViewModel("storefront", SessionKey.Live, t.sessions)
        keepCollecting(vm.uiState)
        vm.setTitle("—!")
        assertEquals("", vm.uiState.value.slug)
        vm.create()
        assertNull(vm.uiState.value.opened)
        assertTrue(t.memory.state.value.saving.isEmpty())
    }
}
