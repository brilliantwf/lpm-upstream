package cx.lpm.mobile.feature.config.profile

import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.config.demoProject
import cx.lpm.mobile.feature.config.demoSession
import cx.lpm.mobile.feature.config.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.editor(t: TestSession, name: String?): ProfileEditorViewModel {
        val vm = ProfileEditorViewModel("storefront", name, SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        return vm
    }

    private fun TestSession.profiles() =
        demoProject("storefront")?.profiles.orEmpty().associate { it.name to it.services }

    @Test
    fun `a new profile saves its services in the project's order`() = runTest {
        val t = demoSession()
        val vm = editor(t, null)
        assertEquals(listOf("web", "api", "worker"), vm.uiState.value.services)
        assertFalse(vm.uiState.value.canSave)
        vm.editName(" frontend ")
        t.scheduler.runCurrent()
        assertFalse(vm.uiState.value.canSave)
        vm.toggle("api")
        vm.toggle("web")
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.canSave)
        vm.save()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertEquals(mapOf("frontend" to listOf("web", "api")), t.profiles())
    }

    @Test
    fun `editing seeds the form, renames, and refuses another profile's name`() = runTest {
        val t = demoSession()
        editor(t, null).apply {
            editName("frontend")
            toggle("web")
            save()
        }
        editor(t, null).apply {
            editName("backend")
            toggle("api")
            toggle("worker")
            save()
        }
        t.settle()
        val vm = editor(t, "backend")
        assertEquals("backend", vm.uiState.value.name)
        assertEquals(listOf("api", "worker"), vm.uiState.value.selected)
        vm.editName("frontend")
        t.scheduler.runCurrent()
        assertFalse(vm.uiState.value.canSave)
        vm.editName("services")
        vm.toggle("worker")
        vm.save()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertEquals(
            mapOf("frontend" to listOf("web"), "services" to listOf("api")),
            t.profiles()
        )
    }

    @Test
    fun `Delete removes the profile`() = runTest {
        val t = demoSession()
        editor(t, null).apply {
            editName("frontend")
            toggle("web")
            save()
        }
        t.settle()
        val vm = editor(t, "frontend")
        vm.delete()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertEquals(emptyMap<String, List<String>>(), t.profiles())
    }
}
