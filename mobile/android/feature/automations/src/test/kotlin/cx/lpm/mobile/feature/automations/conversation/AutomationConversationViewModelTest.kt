package cx.lpm.mobile.feature.automations.conversation

import cx.lpm.mobile.core.data.automations.AutomationsStore
import cx.lpm.mobile.core.data.automations.automationKey
import cx.lpm.mobile.core.navigation.AutomationRunKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.automations.demoSession
import cx.lpm.mobile.feature.automations.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AutomationConversationViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val project = "storefront"
    private val jobId = "nightly-tests"

    /** The demo's runs of Nightly tests, oldest first. */
    private fun runs(t: TestSession): List<Long> {
        val store = t.store<AutomationsStore>()
        store.loadHistory(project, jobId)
        t.settle()
        return store.state.value.history.getValue(automationKey(project, jobId)).map { it.at }
    }

    private fun TestScope.conversation(
        t: TestSession,
        rootAt: Long
    ): AutomationConversationViewModel {
        val vm = AutomationConversationViewModel(
            AutomationRunKey(project, jobId, rootAt),
            t.sessions,
            t.clock
        )
        keepCollecting(vm.uiState)
        vm.load()
        t.settle()
        return vm
    }

    @Test
    fun `opening the newest run reads it`() = runTest {
        val t = demoSession()
        val latest = runs(t).max()
        val vm = conversation(t, latest)
        val state = vm.uiState.value
        assertEquals("Nightly tests", state.job?.displayName)
        assertEquals(0, state.job?.unread)
        val message = state.messages.single()
        assertEquals("Done", message.label)
        assertEquals("1 min 12 sec", message.duration)
        assertFalse(message.quiet)
        assertTrue(message.output.contains("25 passed"))
        assertFalse(state.showsComposer)
        assertFalse(state.sending)
    }

    @Test
    fun `opening an older run leaves the newer one unread`() = runTest {
        val t = demoSession()
        val oldest = runs(t).min()
        val vm = conversation(t, oldest)
        assertEquals(1, vm.uiState.value.job?.unread)
        assertEquals("Problem during the run", vm.uiState.value.messages.single().label)
    }

    @Test
    fun `a reply shows as sent until the answer threads under the run`() = runTest {
        val t = demoSession()
        val latest = runs(t).max()
        val vm = conversation(t, latest)
        vm.send("Why did it pass?")
        t.scheduler.runCurrent()
        assertEquals("Why did it pass?", vm.uiState.value.pendingMessage)
        assertTrue(vm.uiState.value.sending)

        t.settle(3_000)
        assertTrue(vm.uiState.value.running)
        t.settle(15_000)
        val state = vm.uiState.value
        assertNull(state.pendingMessage)
        assertFalse(state.sending)
        assertEquals(2, state.messages.size)
        assertEquals("Why did it pass?", state.messages.last().question)
    }

    @Test
    fun `a reply the Mac refuses clears the pending bubble and says why`() = runTest {
        val t = demoSession()
        val latest = runs(t).max()
        val vm = conversation(t, latest)
        t.store<AutomationsStore>().run(vm.uiState.value.job!!)
        t.settle(500)
        vm.send("Too soon")
        t.settle()
        assertNull(vm.uiState.value.pendingMessage)
        assertEquals("The automation is already running.", vm.uiState.value.followupError)
        vm.dismissFollowupError()
        assertNull(vm.uiState.value.followupError)
    }
}
