package cx.lpm.mobile.feature.automations.editor

import cx.lpm.mobile.core.data.automations.AutomationsStore
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.obj
import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.automations.demoSession
import cx.lpm.mobile.feature.automations.job
import cx.lpm.mobile.feature.automations.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AutomationEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.editor(t: TestSession, target: AutomationEditorTarget) =
        AutomationEditorViewModel(target, SessionKey.Live, t.sessions).also {
            keepCollecting(it.uiState)
            t.settle()
        }

    private fun TestSession.nightly() =
        store<AutomationsStore>().state.value.jobs.single { it.id == "nightly-tests" }

    @Test
    fun `editing seeds the form from the job's body and saves it back`() = runTest {
        val t = demoSession()
        val vm = editor(t, AutomationEditorTarget.Edit(t.nightly()))
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.editing)
        assertEquals("Nightly tests", state.draft.label)
        assertEquals("🌙", state.draft.emoji)
        assertEquals(RunMode.Cmd, state.draft.runMode)
        assertEquals("npm test", state.draft.cmd)
        assertEquals("03:00", state.draft.time)
        assertFalse(state.scopeEditable)
        assertEquals("storefront", state.scopeLabel)
        assertTrue(state.deletable)
        assertFalse(state.global)

        vm.edit { it.copy(cmd = "npm run test:ci") }
        vm.save()
        assertTrue(vm.uiState.value.saving)
        t.settle()
        assertTrue(vm.uiState.value.done)

        val store = t.store<AutomationsStore>()
        store.loadJobConfig("storefront", "nightly-tests", "project")
        t.settle()
        val body = store.state.value.jobConfigBody!!
        assertEquals("npm run test:ci", body.obj("run")?.string("cmd"))
        assertEquals("03:00", body.obj("schedule")?.string("at"))
        assertNull(body["projects"])
    }

    @Test
    fun `a new job saves once it has a name and a prompt, under a fresh id`() = runTest {
        val t = demoSession()
        val vm = editor(t, AutomationEditorTarget.Create())
        assertFalse(vm.uiState.value.loading)
        assertFalse(vm.uiState.value.canSave)
        assertTrue(vm.uiState.value.scopeEditable)
        assertTrue(vm.uiState.value.projects.any { it.name == "storefront" })

        vm.edit { it.copy(label = "Docs check") }
        t.scheduler.runCurrent()
        assertFalse(vm.uiState.value.canSave)
        vm.edit { it.copy(prompt = "Update the docs", agent = "claude", effort = "max") }
        t.scheduler.runCurrent()
        assertTrue(vm.uiState.value.canSave)
        vm.edit { it.copy(agent = "gemini") }
        t.scheduler.runCurrent()
        assertEquals("", vm.uiState.value.draft.effort)

        vm.save()
        t.settle()
        assertTrue(vm.uiState.value.done)
        val saved = t.store<AutomationsStore>().state.value.jobs.single { it.id == "docs-check" }
        assertEquals("Docs check", saved.label)
        assertEquals("prompt", saved.runKind)
    }

    @Test
    fun `a job started from a project runs there, and can run one of its actions`() = runTest {
        val t = demoSession()
        val vm = editor(t, AutomationEditorTarget.Create(project = "storefront"))
        val state = vm.uiState.value
        assertEquals(listOf("storefront"), state.draft.targets)
        assertTrue(state.actions.isNotEmpty())
        vm.edit { it.copy(runMode = RunMode.Action) }
        assertEquals(state.actions.first().name, vm.uiState.value.draft.action)

        vm.edit { it.toggleTarget("storefront") }
        t.scheduler.runCurrent()
        assertEquals(RunMode.Prompt, vm.uiState.value.draft.runMode)
        assertTrue(vm.uiState.value.actions.isEmpty())
    }

    @Test
    fun `deleting removes the job`() = runTest {
        val t = demoSession()
        val vm = editor(t, AutomationEditorTarget.Edit(t.nightly()))
        vm.delete(deleteCopies = false)
        t.settle()
        assertTrue(vm.uiState.value.done)
        assertTrue(t.store<AutomationsStore>().state.value.jobs.none { it.id == "nightly-tests" })
    }

    @Test
    fun `a job the Mac can't read stops loading and says why`() = runTest {
        val t = demoSession()
        val vm = editor(t, AutomationEditorTarget.Edit(job(""""id":"gone"""")))
        assertFalse(vm.uiState.value.loading)
        assertEquals("Couldn't load the automation.", vm.uiState.value.configError)
        vm.dismissConfigError()
        assertNull(vm.uiState.value.configError)
    }
}
