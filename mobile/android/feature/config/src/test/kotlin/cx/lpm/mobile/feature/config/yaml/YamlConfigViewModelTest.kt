package cx.lpm.mobile.feature.config.yaml

import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.config.demoSession
import cx.lpm.mobile.feature.config.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class YamlConfigViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.editor(t: TestSession, project: String): YamlConfigViewModel {
        val vm = YamlConfigViewModel(project, SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        return vm
    }

    @Test
    fun `each layer loads its own text, and an edit marks it unsaved`() = runTest {
        val t = demoSession()
        val vm = editor(t, "storefront")
        val project = vm.uiState.value
        assertFalse(project.loading)
        assertTrue(project.text.startsWith("services:\n  web:\n    cmd: npm run dev\n"))
        assertFalse(project.dirty)
        assertFalse(project.canSave)

        vm.selectLayer(ConfigLayer.Global)
        t.settle()
        assertEquals(
            "# Applies to every project on this Mac.\n" +
                "# Services and actions added here show up everywhere.\n",
            vm.uiState.value.text
        )
        vm.edit(vm.uiState.value.text + "# mine\n")
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.dirty)
        assertTrue(vm.uiState.value.canSave)
        vm.save()
        t.settle()
        assertTrue(vm.uiState.value.done)

        val again = editor(t, "storefront")
        again.selectLayer(ConfigLayer.Global)
        t.settle()
        assertTrue(again.uiState.value.text.endsWith("# mine\n"))
    }

    @Test
    fun `the shared layer of an SSH project can't be edited from here`() = runTest {
        val t = demoSession()
        t.store<ProjectConfigStore>().createSshProject(
            "box",
            buildJsonObject {
                put("host", "box.local")
                put("user", "me")
            }
        )
        t.settle()
        val vm = editor(t, "box")
        vm.selectLayer(ConfigLayer.Repo)
        t.settle()
        assertFalse(vm.uiState.value.available)
        vm.edit("services: {}\n")
        t.scheduler.runCurrent()
        assertFalse(vm.uiState.value.dirty)
    }
}
