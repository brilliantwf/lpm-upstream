package cx.lpm.mobile.feature.automations.detail

import cx.lpm.mobile.core.navigation.AutomationKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.automations.demoSession
import cx.lpm.mobile.feature.automations.format.AutomationTone
import cx.lpm.mobile.feature.automations.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AutomationDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.page(
        t: TestSession,
        project: String = "storefront",
        jobId: String = "nightly-tests"
    ): AutomationDetailViewModel {
        val vm =
            AutomationDetailViewModel(AutomationKey(project, jobId), t.sessions, t.prefs, t.clock)
        keepCollecting(vm.uiState)
        vm.load()
        t.settle()
        return vm
    }

    @Test
    fun `the page shows the job's state, setup and every run, newest first`() = runTest {
        val t = demoSession()
        val state = page(t).uiState.value
        assertEquals("Nightly tests", state.job?.displayName)
        assertEquals("storefront", state.scope)
        assertEquals("Next run in 15 hours", state.pill)
        assertEquals(AutomationTone.Green, state.pillTone)
        assertEquals("Every day at 03:00", state.schedule)
        assertEquals("Done · 9 hours ago", state.lastRun?.detail)
        assertEquals(AutomationTone.Green, state.lastRun?.tone)
        val setup = state.setup!!
        assertEquals("npm test", setup.firstLine)
        assertTrue(setup.monospace)
        assertNull(setup.agent)
        assertEquals(4, state.runs.size)
        assertEquals(state.runs.map { it.rootAt }.sortedDescending(), state.runs.map { it.rootAt })
        assertTrue(state.runs.first().unread)
        assertEquals(listOf(false, false, false), state.runs.drop(1).map { it.unread })
        assertEquals("Problem during the run", state.runs.last().label)
        assertEquals(AutomationTone.Red, state.runs.last().tone)
        assertNull(state.runs.first().preview)
        assertFalse(state.historyLoading)
        assertNull(state.live)
    }

    @Test
    fun `the run bar starts a run, which streams and then lands in the history`() = runTest {
        val t = demoSession()
        val vm = page(t)
        vm.runBarTapped()
        assertTrue(vm.uiState.value.pending)
        t.settle(500)
        assertTrue(vm.uiState.value.job!!.running)
        assertFalse(vm.uiState.value.pending)
        assertEquals("", vm.uiState.value.live)

        t.settle(3_000)
        vm.loadLiveOutput()
        t.settle(500)
        assertTrue(vm.uiState.value.live!!.isNotEmpty())

        t.settle(10_000)
        val done = vm.uiState.value
        assertFalse(done.job!!.running)
        assertEquals(5, done.runs.size)
    }

    @Test
    fun `reading the job clears its new runs`() = runTest {
        val t = demoSession()
        val vm = page(t)
        vm.markRead()
        t.settle()
        assertEquals(0, vm.uiState.value.job?.unread)
        assertTrue(vm.uiState.value.runs.none { it.unread })
    }

    @Test
    fun `turning the job off, and back on from the run bar`() = runTest {
        val t = demoSession()
        val vm = page(t)
        vm.setEnabled(false)
        t.settle()
        assertEquals("Off", vm.uiState.value.pill)
        assertEquals(AutomationTone.Orange, vm.uiState.value.pillTone)
        assertNull(vm.uiState.value.nextRun)
        vm.runBarTapped()
        t.settle()
        assertEquals(true, vm.uiState.value.job?.enabled)
    }

    @Test
    fun `the open groups are remembered across automations`() = runTest {
        val t = demoSession()
        val vm = page(t)
        assertFalse(vm.uiState.value.scheduleOpen)
        vm.setScheduleOpen(true)
        vm.setSetupOpen(true)
        t.settle()
        assertTrue(vm.uiState.value.scheduleOpen)
        val other = page(t, project = "blog", jobId = "weekly-deps")
        assertTrue(other.uiState.value.scheduleOpen)
        assertTrue(other.uiState.value.setupOpen)
        assertEquals("Claude", other.uiState.value.setup?.agent)
    }

    @Test
    fun `a job that's gone says so once the list is in`() = runTest {
        val t = demoSession()
        val state = page(t, jobId = "removed").uiState.value
        assertTrue(state.loaded)
        assertNull(state.job)
        assertTrue(state.runs.isEmpty())
    }
}
