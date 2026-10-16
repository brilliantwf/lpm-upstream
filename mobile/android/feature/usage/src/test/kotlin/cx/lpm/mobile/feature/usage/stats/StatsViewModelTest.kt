package cx.lpm.mobile.feature.usage.stats

import cx.lpm.mobile.core.navigation.StatsKey
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private fun TestScope.opened(t: TestSession): StatsViewModel {
        val vm = StatsViewModel(StatsKey(), t.sessions, t.clock)
        backgroundScope.launch { vm.uiState.collect() }
        vm.open()
        return vm
    }

    private val StatsUiState.loaded get() = body as StatsBody.Loaded

    @Test
    fun `the screen opens on the last 30 days`() = runTest(main.dispatcher) {
        val t = demoSession()
        val vm = opened(t)
        t.settle(0)
        assertEquals(StatsBody.Loading, vm.uiState.value.body)

        t.settle()
        val state = vm.uiState.value
        assertEquals(30, state.days)
        assertEquals(30, state.loaded.stats.days)
        assertFalse(state.loaded.dimmed)
    }

    @Test
    fun `picking a period dims the numbers until its own arrive`() = runTest(main.dispatcher) {
        val t = demoSession()
        val vm = opened(t)
        t.settle()

        vm.select(7)
        t.settle(0)
        val loading = vm.uiState.value
        assertEquals(7, loading.days)
        assertTrue(loading.loaded.dimmed)
        assertEquals(30, loading.loaded.stats.days)

        t.settle()
        assertEquals(7, vm.uiState.value.loaded.stats.days)
        assertFalse(vm.uiState.value.loaded.dimmed)
    }

    @Test
    fun `a Mac that never answers ends in an error to retry`() = runTest(main.dispatcher) {
        val t = TestSession(this, UsageStores)
        val mac = t.connectLive()
        val socket = mac.socket
        val vm = opened(t)
        mac.settle(30_000)
        assertEquals(
            StatsBody.Failed("Couldn't load stats. Pull to refresh."),
            vm.uiState.value.body
        )

        val asked = socket.sentOf("stats").size
        vm.retry()
        mac.settle(0)
        assertEquals(asked + 1, socket.sentOf("stats").size)
        assertEquals(StatsBody.Loading, vm.uiState.value.body)
    }
}
