package cx.lpm.mobile.feature.usage.rows

import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.usage.UsageStores
import cx.lpm.mobile.feature.usage.connectLive
import cx.lpm.mobile.feature.usage.demoSession
import cx.lpm.mobile.feature.usage.settle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsageRowsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private fun TestScope.shown(t: TestSession, project: String? = null): UsageRowsViewModel {
        val vm = UsageRowsViewModel(UsageRowsKey(project = project), t.sessions, t.clock)
        backgroundScope.launch { vm.uiState.collect() }
        backgroundScope.launch { vm.feed() }
        t.settle()
        return vm
    }

    private val UsageRowsUiState.labels get() = rows.map { it.row.label }

    private fun UsageRowsUiState.row(label: String) = rows.single { it.row.label == label }

    @Test
    fun `the projects list shows a row per account, tagged for the main accounts`() =
        runTest(main.dispatcher) {
            val t = demoSession()
            val state = shown(t).uiState.value
            assertEquals(
                listOf("Work", "Personal", "Client", "Old laptop", "Claude", "Codex"),
                state.labels
            )
            assertEquals(UsageAccountMark.Standby, state.row("Work").mark)
            assertEquals(UsageAccountMark.Current, state.row("Claude").mark)
            assertNull(state.row("Personal").mark)
            assertNull(state.row("Codex").mark)
            assertEquals("4d 10h", state.row("Work").row.detail)
            assertEquals("11%", state.row("Work").row.percentText)
            assertNull(state.inUse?.project)
            assertFalse(state.switches)
            assertTrue(state.shown)
        }

    @Test
    fun `reset countdowns and the pace tick move with the clock`() = runTest(main.dispatcher) {
        val t = demoSession()
        val vm = shown(t)
        t.settle(10_000)
        val before = vm.uiState.value.row("Work").row
        t.settle(30 * 60_000L)
        val after = vm.uiState.value.row("Work").row
        assertEquals("4d 10h", before.detail)
        assertEquals("4d 9h", after.detail)
        assertEquals(0.5 / 168, after.pace!! - before.pace!!, 1e-4)
    }

    @Test
    fun `a project's screen switches the project to an account`() = runTest(main.dispatcher) {
        val t = demoSession()
        // The project's screen asks for its tabs; the rows wait for them.
        t.store<TerminalsStore>().loadTerminals("storefront")
        val vm = shown(t, project = "storefront")
        assertTrue(vm.uiState.value.switches)
        assertTrue(vm.uiState.value.shown)
        assertEquals("storefront", vm.uiState.value.inUse?.project?.name)

        vm.switchTo("work")
        val projects = t.store<ProjectsStore>()
        assertEquals(
            ClaudeOwnChoice(account = "work"),
            projects.state.value.project("storefront")?.claude
        )
        t.settle()
        val state = vm.uiState.value
        assertEquals("work", state.inUse?.current)
        assertEquals(true, state.inUse?.pinned)
        assertEquals(UsageAccountMark.Current, state.row("Work").mark)

        vm.switchTo("default")
        assertEquals(
            ClaudeOwnChoice(account = ""),
            projects.state.value.project("storefront")?.claude
        )
    }

    @Test
    fun `today's spend is read every 3 minutes, on a push at most every 30 seconds`() =
        runTest(main.dispatcher) {
            val t = TestSession(this, UsageStores)
            val mac = t.connectLive()
            val socket = mac.socket
            val todays = { socket.sentOf("stats").count { it["days"].toString() == "1" } }
            val push = """{"t":"limits-changed","claudeEnabled":true,"limits":{}}"""
            val vm = UsageRowsViewModel(UsageRowsKey(), t.sessions, t.clock)
            val feed = backgroundScope.launch { vm.feed() }
            mac.settle(0)
            assertEquals(1, todays())
            assertEquals(1, socket.sentOf("limits").size)

            mac.settle(179_000)
            assertEquals(1, todays())
            mac.settle(1_000)
            assertEquals(2, todays())

            // A push means an agent reported work, but it can't hurry the next read.
            mac.settle(10_000)
            socket.receive(push)
            mac.settle(0)
            assertEquals(2, todays())
            mac.settle(25_000)
            socket.receive(push)
            mac.settle(0)
            assertEquals(3, todays())

            // Off screen, neither the clock nor a push reads anything.
            feed.cancel()
            mac.settle(400_000)
            socket.receive(push)
            mac.settle(0)
            assertEquals(3, todays())
        }
}
