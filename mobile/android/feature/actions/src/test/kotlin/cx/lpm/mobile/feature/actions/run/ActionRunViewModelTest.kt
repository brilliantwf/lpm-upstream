package cx.lpm.mobile.feature.actions.run

import cx.lpm.mobile.core.data.actions.ActionsStore
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.actions.demoAction
import cx.lpm.mobile.feature.actions.demoSession
import cx.lpm.mobile.feature.actions.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActionRunViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val picks = ActionPicks()
    private val spawned = mutableListOf<TerminalInfo>()
    private val onSpawn: (TerminalInfo) -> Unit = { spawned += it }

    private fun TestScope.runner(t: TestSession, project: String): ActionRunViewModel {
        val vm = ActionRunViewModel(project, SessionKey.Live, t.sessions, picks)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        return vm
    }

    @Test
    fun `a pick from the sheet waits for the screen under it, then asks for inputs`() = runTest {
        val t = demoSession()
        val vm = runner(t, "blog")
        picks.pick(SessionKey.Live, "blog", t.demoAction("blog", "new-post"))
        t.settle()
        assertTrue(vm.uiState.value.picked)

        vm.runPicked(onSpawn)
        t.settle()
        assertFalse(vm.uiState.value.picked)
        val inputs = vm.uiState.value.step as RunStep.Inputs
        assertEquals("new-post", inputs.action.name)

        vm.submitInputs(mapOf("title" to "Hello world"), onSpawn)
        t.settle()
        val run = vm.uiState.value.step as RunStep.Background
        assertEquals("New post", run.label)
        val actions = t.store<ActionsStore>()
        assertEquals(listOf(run.runId), actions.state.value.runs("blog").map { it.runId })

        actions.loadOutput("blog", run.runId)
        t.settle(5_000)
        actions.loadOutput("blog", run.runId)
        t.settle()
        assertTrue(
            actions.state.value.outputs[run.runId]!!.text.contains("posts/hello-world.md created")
        )
    }

    @Test
    fun `an action marked confirm asks first, and Cancel runs nothing`() = runTest {
        val t = demoSession()
        val vm = runner(t, "mobile-app")
        val clean = t.demoAction("mobile-app", "clean")
        vm.run(clean, onSpawn)
        t.settle()
        val confirm = vm.uiState.value.step as RunStep.Confirm
        vm.dismiss(confirm)
        t.settle()
        assertNull(vm.uiState.value.step)
        assertTrue(t.store<ActionsStore>().state.value.runs.isEmpty())

        vm.run(clean, onSpawn)
        vm.confirm(onSpawn)
        t.settle()
        val run = vm.uiState.value.step as RunStep.Background
        // The confirmation's own dismissal, after Run, leaves the run's sheet up.
        vm.dismiss(confirm)
        assertEquals(run, vm.uiState.value.step)
    }

    @Test
    fun `a terminal action runs on the Mac and opens the tab it spawned`() = runTest {
        val t = demoSession()
        val vm = runner(t, "storefront")
        t.store<TerminalsStore>().loadTerminals("storefront")
        t.settle()
        vm.run(t.demoAction("storefront", "lint"), onSpawn)
        t.settle()
        assertNull(vm.uiState.value.step)
        t.settle(3_000)
        assertEquals(listOf("Lint"), spawned.map { it.label })
    }
}
