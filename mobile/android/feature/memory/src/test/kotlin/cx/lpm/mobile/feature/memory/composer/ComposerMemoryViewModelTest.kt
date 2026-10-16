package cx.lpm.mobile.feature.memory.composer

import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.memory.demoSession
import cx.lpm.mobile.feature.memory.memory
import cx.lpm.mobile.feature.memory.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposerMemoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `Claude Code continues a session with the slash command`() = runTest {
        val t = demoSession()
        val vm =
            ComposerMemoryViewModel("storefront", "claude", SessionKey.Live, t.sessions, t.clock)
        assertEquals("/lpm-memory", vm.invocation(""))
        assertEquals("/lpm-memory payments-module ", vm.invocation("payments-module"))
        val codex =
            ComposerMemoryViewModel("storefront", "codex", SessionKey.Live, t.sessions, t.clock)
        assertEquals("\$lpm-memory payments-module ", codex.invocation("payments-module"))
    }

    @Test
    fun `three sessions list under a heading, four or more are searchable`() = runTest {
        val t = demoSession()
        val vm =
            ComposerMemoryViewModel("storefront", "claude", SessionKey.Live, t.sessions, t.clock)
        keepCollecting(vm.uiState)
        assertTrue(vm.uiState.value.loading)
        vm.load()
        t.settle()
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(
            listOf("Checkout redesign", "Payments module", "Cart badge not updating"),
            state.visible.map {
                it.title
            }
        )
        assertEquals("2 hours ago", state.visible.first().updated)
        assertFalse(state.searchable)

        t.memory.create("storefront", "release-notes", "# Release notes\n")
        t.settle()
        assertTrue(vm.uiState.value.searchable)
        vm.setQuery("  PAY ")
        assertEquals(listOf("payments-module"), vm.uiState.value.visible.map { it.name })
        vm.setQuery("cart-badge")
        assertEquals(listOf("cart-badge-bug"), vm.uiState.value.visible.map { it.name })
        vm.setQuery("zzz")
        assertTrue(vm.uiState.value.visible.isEmpty())
    }

    @Test
    fun `deleting from the picker drops the row`() = runTest {
        val t = demoSession()
        val vm =
            ComposerMemoryViewModel("storefront", "claude", SessionKey.Live, t.sessions, t.clock)
        keepCollecting(vm.uiState)
        vm.load()
        t.settle()
        vm.delete("payments-module")
        t.settle()
        assertEquals(
            listOf("checkout-redesign", "cart-badge-bug"),
            vm.uiState.value.sessions.map {
                it.name
            }
        )
    }
}
