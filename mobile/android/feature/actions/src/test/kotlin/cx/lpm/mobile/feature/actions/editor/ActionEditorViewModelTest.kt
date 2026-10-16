package cx.lpm.mobile.feature.actions.editor

import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.actions.demoSession
import cx.lpm.mobile.feature.actions.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActionEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.editor(t: TestSession, key: String?): ActionEditorViewModel {
        val vm = ActionEditorViewModel("storefront", key, SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        return vm
    }

    private fun TestSession.storefrontActions() =
        store<ProjectsStore>().state.value.project("storefront")!!.actions.map { it.name }

    @Test
    fun `editing an action seeds the form from its body, and a save closes it`() = runTest {
        val t = demoSession()
        val vm = editor(t, "lint")
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.editing)
        assertEquals("lint", state.draft.name)
        assertEquals("Lint", state.draft.label)
        assertEquals("npm run lint", state.draft.cmd)
        assertEquals(ActionRunIn.Command, state.draft.runIn)
        assertEquals(ActionPlacement.InZone("checks", null), state.draft.placement)
        assertTrue("lint" !in state.otherKeys && "test" in state.otherKeys)

        vm.edit { it.copy(name = "lint-all", label = "Lint all") }
        vm.save()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertTrue("lint-all" in t.storefrontActions())
        assertFalse("lint" in t.storefrontActions())
    }

    @Test
    fun `a new action can't save until it has a free name and a command`() = runTest {
        val t = demoSession()
        val vm = editor(t, null)
        assertFalse(vm.uiState.value.loading)
        assertFalse(vm.uiState.value.canSave)
        vm.edit { it.copy(name = "test", cmd = "npm run deploy") }
        t.scheduler.runCurrent()
        assertFalse(vm.uiState.value.canSave)
        vm.edit { it.copy(name = "deploy") }
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.canSave)
        vm.save()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertTrue("deploy" in t.storefrontActions())
    }

    @Test
    fun `Delete removes the action`() = runTest {
        val t = demoSession()
        val vm = editor(t, "test")
        vm.delete()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertFalse("test" in t.storefrontActions())
    }
}
