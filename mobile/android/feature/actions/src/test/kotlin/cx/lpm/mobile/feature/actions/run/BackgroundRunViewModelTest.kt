package cx.lpm.mobile.feature.actions.run

import cx.lpm.mobile.core.data.actions.ActionsStore
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.actions.ActionStores
import cx.lpm.mobile.feature.actions.demoSession
import cx.lpm.mobile.feature.actions.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackgroundRunViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `an open run follows its output until it's done`() = runTest {
        val t = demoSession()
        val runId = t.store<ActionsStore>().startBackground(
            "api-gateway",
            "test",
            "Test",
            emptyMap()
        )
        val vm = BackgroundRunViewModel("api-gateway", runId, SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        assertEquals(RunStatus.Starting, vm.uiState.value.status)
        val follow = backgroundScope.launch { vm.follow() }
        t.settle(2_500)
        assertEquals(RunStatus.Running, vm.uiState.value.status)
        assertTrue(vm.uiState.value.text.contains("ok  api-gateway/internal/auth"))
        assertTrue(vm.uiState.value.running)

        t.settle(6_000)
        assertEquals(RunStatus.Done, vm.uiState.value.status)
        assertTrue(vm.uiState.value.text.contains("PASS"))
        assertFalse(vm.uiState.value.running)
        assertTrue(follow.isCompleted)
    }

    @Test
    fun `Stop cancels the run on the Mac`() = runTest {
        val t = demoSession()
        val runId = t.store<ActionsStore>().startBackground(
            "mobile-app",
            "clean",
            "Clear caches",
            emptyMap()
        )
        val vm = BackgroundRunViewModel("mobile-app", runId, SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        backgroundScope.launch { vm.follow() }
        t.settle(1_500)
        vm.stop()
        t.settle(2_500)
        assertEquals(RunStatus.Stopped, vm.uiState.value.status)
        assertFalse(vm.uiState.value.text.contains("Caches cleared."))
    }

    @Test
    fun `the project's runs re-attach after a reconnect and stay polled while shown`() = runTest {
        val t = TestSession(this, ActionStores)
        val socket = t.connectReady()
        val vm = BackgroundRunsViewModel("storefront", SessionKey.Live, t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        backgroundScope.launch { vm.keepCurrent() }
        t.scheduler.runCurrent()
        assertEquals(1, socket.sentOf("backgroundRuns").size)
        socket.receive(
            """{"t":"backgroundRuns","ok":true,"project":"storefront","runs":[""" +
                """{"runId":"r1","label":"Deploy","startedAt":1,"running":true,"success":false,""" +
                """"error":""}]}"""
        )
        t.scheduler.runCurrent()
        assertEquals(listOf("Deploy"), vm.uiState.value.map { it.run.label })
        assertEquals(RunStatus.Starting, vm.uiState.value.single().status)
        t.settle(3_000)
        assertTrue(socket.sentOf("actionBgOutput").any { it["runId"].toString().contains("r1") })

        socket.fail()
        t.settle(5_000)
        val next = t.transport.last
        next.open()
        t.scheduler.runCurrent()
        next.receive("""{"t":"ready","serverId":"srv-1","serverName":"Studio Mac"}""")
        t.scheduler.runCurrent()
        assertEquals(1, next.sentOf("backgroundRuns").size)
    }
}
