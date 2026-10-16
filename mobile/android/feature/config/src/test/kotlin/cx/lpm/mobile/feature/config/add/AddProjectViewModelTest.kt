package cx.lpm.mobile.feature.config.add

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddProjectViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.form(t: TestSession): AddProjectViewModel {
        val vm = AddProjectViewModel(t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        return vm
    }

    @Test
    fun `a picked folder names the project, and Create adds it`() = runTest {
        val t = demoSession()
        val vm = form(t)
        assertFalse(vm.uiState.value.canCreate)
        vm.pickLocalRoot("/Users/demo/Developer/playground")
        t.scheduler.runCurrent()
        assertEquals("playground", vm.uiState.value.draft.name)
        assertTrue(vm.uiState.value.canCreate)
        vm.create()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertNotNull(t.demoProject("playground"))
    }

    @Test
    fun `a typed name sticks through later picks and mode changes`() = runTest {
        val t = demoSession()
        val vm = form(t)
        vm.editName("My Site")
        vm.pickLocalRoot("/Users/demo/Projects")
        vm.selectMode(AddProjectMode.Clone)
        vm.editCloneUrl("https://github.com/acme/web-shop.git")
        t.scheduler.runCurrent()
        assertEquals("My Site", vm.uiState.value.draft.name)
        assertFalse(vm.uiState.value.canCreate)
        vm.pickCloneParent("/Users/demo/Projects")
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.canCreate)
    }

    @Test
    fun `SSH lists the saved hosts, and one fills the form`() = runTest {
        val t = demoSession()
        val vm = form(t)
        vm.selectMode(AddProjectMode.Ssh)
        t.settle()
        val state = vm.uiState.value
        assertTrue(state.sshHostsLoaded)
        assertEquals(listOf("dev-server", "staging"), state.sshHosts.map { it.name })
        assertFalse(state.sshFieldsVisible)
        vm.pickHost("staging")
        t.scheduler.runCurrent()
        val picked = vm.uiState.value
        assertTrue(picked.sshFieldsVisible)
        assertEquals("deploy", picked.draft.user)
        assertEquals("~/.ssh/id_ed25519", picked.draft.identityFile)
        assertEquals("deploy-staging", picked.draft.name)
        vm.create()
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertEquals(true, t.demoProject("deploy-staging")?.isRemote)
    }

    @Test
    fun `a name the Mac already has fails and keeps the sheet open`() = runTest {
        val t = demoSession()
        val vm = form(t)
        vm.pickLocalRoot("/Users/demo/Projects/storefront")
        vm.create()
        t.settle()
        val state = vm.uiState.value
        assertFalse(state.done)
        assertEquals("A project named “storefront” already exists.", state.mutationError)
        vm.dismissMutationError()
        t.scheduler.runCurrent()
        assertEquals(null, vm.uiState.value.mutationError)
    }
}
