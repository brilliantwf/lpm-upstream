package cx.lpm.mobile.feature.config.hub

import cx.lpm.mobile.core.navigation.ProjectConfigKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.feature.config.demoSession
import cx.lpm.mobile.feature.config.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectConfigViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the lists follow the Mac as rows are deleted`() = runTest {
        val t = demoSession()
        val vm = ProjectConfigViewModel(ProjectConfigKey("storefront"), t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        val state = vm.uiState.value
        assertEquals(
            listOf("web" to 3000, "api" to 4000, "worker" to 0),
            state.services.map {
                it.name to it.port
            }
        )
        assertEquals(
            listOf("test", "lint", "fix-tests", "review", "codex-review"),
            state.actions.map { it.name }
        )

        vm.deleteService("worker")
        vm.deleteAction("lint")
        t.settle()
        assertEquals(listOf("web", "api"), vm.uiState.value.services.map { it.name })
        assertEquals(4, vm.uiState.value.actions.size)
        assertEquals(null, vm.uiState.value.mutationError)
    }
}
