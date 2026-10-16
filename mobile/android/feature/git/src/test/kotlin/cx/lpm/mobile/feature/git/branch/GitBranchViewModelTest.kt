package cx.lpm.mobile.feature.git.branch

import cx.lpm.mobile.core.data.git.GitStore
import cx.lpm.mobile.core.navigation.GitBranchKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.git.demoSession
import cx.lpm.mobile.feature.git.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitBranchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.branches(t: TestSession): GitBranchViewModel {
        val vm = GitBranchViewModel(GitBranchKey("storefront"), t.sessions)
        keepCollecting(vm.uiState)
        t.settle()
        return vm
    }

    @Test
    fun `local branches newest first, then remote-only ones, the current one checked`() = runTest {
        val t = demoSession()
        val state = branches(t).uiState.value
        assertEquals(
            listOf("feat/checkout-redesign", "main", "fix/cart-badge"),
            state.local.map {
                it.name
            }
        )
        assertEquals(listOf("origin/main"), state.remote.map { it.id })
        assertEquals("feat/checkout-redesign", state.current)
        assertTrue(state.isCurrent(state.local.first()))
        assertFalse("a remote branch is never current", state.isCurrent(state.remote.single()))
        assertNull(state.error)
    }

    @Test
    fun `checking out a branch closes the sheet and moves the current branch`() = runTest {
        val t = demoSession()
        val vm = branches(t)
        vm.checkout(vm.uiState.value.local.single { it.name == "main" })
        t.settle(0)
        assertEquals("main", vm.uiState.value.checkingOut)
        t.settle()
        assertTrue(vm.uiState.value.switched)
        assertNull(vm.uiState.value.checkingOut)
    }

    @Test
    fun `a new branch is created off HEAD, and a taken name says so`() = runTest {
        val t = demoSession()
        val vm = branches(t)
        vm.createBranch("main")
        t.settle(0)
        assertTrue(vm.uiState.value.creating)
        t.settle()
        assertEquals("A branch named main already exists.", vm.uiState.value.error)
        assertFalse(vm.uiState.value.switched)
        vm.dismissError()

        vm.createBranch(" feat/receipts ")
        t.settle()
        assertTrue(vm.uiState.value.switched)
        assertEquals(
            "feat/receipts",
            t.store<GitStore>().state.value.snapshots["storefront"]?.branch
        )

        val reopened = branches(t).uiState.value
        assertEquals(
            "the sheet loads the list afresh",
            "feat/receipts",
            reopened.local.first().name
        )
        assertEquals("feat/receipts", reopened.current)
    }
}
