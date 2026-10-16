package cx.lpm.mobile.feature.projects.services

import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.projects.demoSession
import cx.lpm.mobile.feature.projects.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServiceEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.editor(t: TestSession, key: String?): ServiceEditorViewModel {
        val vm = ServiceEditorViewModel("storefront", key, SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        return vm
    }

    @Test
    fun `editing a service seeds the form from the Mac, and a save closes it`() = runTest {
        val t = demoSession()
        val vm = editor(t, "web")
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.editing)
        assertEquals("web", state.draft.name)
        assertTrue(state.draft.cmd.isNotEmpty())
        assertEquals(listOf("api", "worker"), state.otherServices)

        vm.edit { it.copy(port = "3001") }
        vm.save()
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.saving)
        t.settle()
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `a new service can't save until it has a free name and a command`() = runTest {
        val t = demoSession()
        val vm = editor(t, null)
        assertFalse(vm.uiState.value.loading)
        assertFalse(vm.uiState.value.canSave)
        vm.edit { it.copy(name = "api", cmd = "go run .") }
        t.scheduler.runCurrent()
        assertFalse(vm.uiState.value.canSave)
        vm.edit { it.copy(name = "docs") }
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.canSave)
    }
}
