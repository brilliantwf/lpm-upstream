package cx.lpm.mobile.feature.git.auto

import cx.lpm.mobile.core.data.git.GitStore
import cx.lpm.mobile.core.model.GitAutoKind
import cx.lpm.mobile.core.model.GitAutoRun
import cx.lpm.mobile.core.model.GitAutoStep
import cx.lpm.mobile.core.navigation.GitAutoKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.git.demoSession
import cx.lpm.mobile.feature.git.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitAutoViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `an Auto Commit runs its step to a commit`() = runTest {
        val t = demoSession()
        val vm = GitAutoViewModel(GitAutoKey("storefront", GitAutoKind.Commit.rawValue), t.sessions)
        keepCollecting(vm.uiState)
        vm.appeared()
        vm.start()
        t.settle(0)
        val placeholder = vm.uiState.value.run
        assertEquals(GitAutoKind.Commit, placeholder?.kind)
        assertEquals(listOf("commit"), placeholder?.steps?.map { it.id })

        t.settle(15_000)
        val done = vm.uiState.value.run!!
        assertEquals(GitAutoRun.Phase.Done, done.phase)
        assertEquals(GitAutoStep.Status.Done, done.steps.single().status)
        assertTrue(done.message.isNotEmpty())
        assertEquals(0, t.store<GitStore>().state.value.changedCount("storefront"))
        vm.disappeared()
    }

    @Test
    fun `an Auto Create PR can switch back to its base`() = runTest {
        val t = demoSession()
        val vm = GitAutoViewModel(GitAutoKey("storefront", GitAutoKind.Pr.rawValue), t.sessions)
        keepCollecting(vm.uiState)
        vm.start()
        t.settle(30_000)
        val done = vm.uiState.value.run!!
        assertEquals(GitAutoRun.Phase.Done, done.phase)
        assertTrue(done.url.isNotEmpty())
        assertTrue(done.base.isNotEmpty())

        vm.switchToBase()
        t.settle(0)
        assertTrue(vm.uiState.value.switching)
        t.settle(10_000)
        assertTrue(vm.uiState.value.switched)
    }

    @Test
    fun `stop cancels a run`() = runTest {
        val t = demoSession()
        val vm =
            GitAutoViewModel(GitAutoKey("storefront", GitAutoKind.CommitPush.rawValue), t.sessions)
        keepCollecting(vm.uiState)
        vm.start()
        t.settle(500)
        vm.stop()
        t.settle(0)
        assertTrue(vm.uiState.value.run!!.canceling)
        t.settle(15_000)
        assertEquals(GitAutoRun.Phase.Canceled, vm.uiState.value.run!!.phase)

        vm.retry()
        t.settle(0)
        assertEquals(GitAutoRun.Phase.Running, vm.uiState.value.run!!.phase)
    }
}
