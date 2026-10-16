package cx.lpm.mobile.feature.usage.usage

import cx.lpm.mobile.core.model.USAGE_GENERIC_ERROR
import cx.lpm.mobile.core.navigation.UsageKey
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
class UsageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private fun TestScope.opened(t: TestSession): UsageViewModel {
        val vm = UsageViewModel(UsageKey(), t.sessions, t.clock)
        backgroundScope.launch { vm.uiState.collect() }
        vm.open()
        return vm
    }

    private val UsageUiState.loaded get() = body as UsageBody.Loaded

    @Test
    fun `a card per Claude account, named as on the Mac, and one for Codex`() =
        runTest(main.dispatcher) {
            val t = demoSession()
            val vm = opened(t)
            t.settle()
            val body = vm.uiState.value.loaded
            assertTrue(body.claudeEnabled)
            assertEquals(listOf("Claude", "Work"), body.claude.map { it.title })
            assertEquals(
                listOf("you@example.com", "you@work.example"),
                body.claude.map {
                    it.subtitle
                }
            )
            assertEquals("pro", body.codex?.label)
            assertNull(body.refreshError)
            assertFalse(body.dimmed)
        }

    @Test
    fun `the clock moves every 30 seconds, on both sides`() = runTest(main.dispatcher) {
        val t = demoSession()
        val vm = opened(t)
        t.settle()
        val first = vm.uiState.value
        t.settle(30_000)
        val next = vm.uiState.value
        assertEquals(30_000.0, next.now - first.now, 0.0)
        // The demo Mac stamps its snapshot with this phone's own clock.
        assertEquals(next.now, next.macNow, 1.0)
    }

    @Test
    fun `a reload dims the cards, and a failed one says so over them`() = runTest(main.dispatcher) {
        val t = TestSession(this, UsageStores)
        val mac = t.connectLive()
        val socket = mac.socket
        val vm = opened(t)
        mac.settle(0)
        assertEquals(UsageBody.Loading, vm.uiState.value.body)
        socket.receive("""{"t":"limits","ok":true,"claudeEnabled":false,"limits":{}}""")
        mac.settle(0)
        assertTrue(vm.uiState.value.loaded.claude.isEmpty())
        assertFalse(vm.uiState.value.loaded.claudeEnabled)

        vm.retry()
        mac.settle(0)
        assertTrue(vm.uiState.value.loaded.dimmed)
        mac.settle(20_000)
        assertEquals(USAGE_GENERIC_ERROR, vm.uiState.value.loaded.refreshError)
        assertFalse(vm.uiState.value.loaded.dimmed)
    }

    @Test
    fun `a first load that never answers fails`() = runTest(main.dispatcher) {
        val t = TestSession(this, UsageStores)
        val mac = t.connectLive()
        val vm = opened(t)
        mac.settle(20_000)
        assertEquals(UsageBody.Failed(USAGE_GENERIC_ERROR), vm.uiState.value.body)
    }
}
