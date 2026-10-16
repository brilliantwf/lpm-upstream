package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.data.testing.FakeTransport
import cx.lpm.mobile.core.data.testing.SchedulerClock
import cx.lpm.mobile.core.demo.DemoServer
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.TerminalInfo
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Demo Mode end to end: the real client against the real in-process [DemoServer]. */
@OptIn(ExperimentalCoroutinesApi::class)
class LpmClientDemoTest {
    @Test
    fun `connect, list, subscribe and type against the demo Mac`() = runTest {
        val transport = FakeTransport()
        val env =
            ClientEnvironment(
                backgroundScope,
                SchedulerClock(testScheduler),
                transport,
                NoTailnet,
                Random(1)
            )
        val c = LpmClientFactory(env).demo("Test Phone")
        val states = mutableListOf<LpmClient.State>()
        var identity: Pair<String?, String?>? = null
        var projects = emptyList<Project>()
        val terminals = mutableMapOf<String, List<TerminalInfo>>()
        val seeds = mutableListOf<String>()
        val output = StringBuilder()
        c.onState = { states += it }
        c.onIdentity = { serverId, serverName, _ -> identity = serverId to serverName }
        c.onProjects = { projects = it }
        c.onTerminals = { project, list -> terminals[project] = list }
        c.onSeed = { id, _, _, _, _ -> seeds += id }
        c.onOutput = { _, data -> output.append(data) }

        // A demo client never opens a socket and knows its device before `ready`.
        assertEquals(DemoServer.DEVICE_ID, c.deviceId)
        c.connect()
        assertEquals(LpmClient.State.Connecting, c.state)
        testScheduler.advanceTimeBy(401)
        testScheduler.runCurrent()
        assertEquals(LpmClient.State.Ready, c.state)
        assertEquals(listOf(LpmClient.State.Connecting, LpmClient.State.Ready), states)
        assertEquals(DemoServer.SERVER_ID to DemoServer.SERVER_NAME, identity)
        assertTrue(LpmClient.FILE_UPLOAD_FEATURE in c.hostFeatures)
        assertTrue(transport.sockets.isEmpty())

        c.requestProjects()
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        val names = projects.map { it.name }
        assertTrue(names.containsAll(listOf("storefront", "api-gateway", "mobile-app")))
        assertTrue(projects.first { it.name == "storefront" }.running)

        c.requestTerminals("storefront")
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        val shell = terminals.getValue("storefront").first { it.cli.isEmpty() }
        assertEquals("Terminal 1", shell.label)

        c.subscribe(shell.id)
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        assertEquals(listOf(shell.id), seeds)

        c.sendInput(shell.id, "x")
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        assertTrue(output.toString(), output.contains("x"))

        c.disconnect()
        assertEquals(LpmClient.State.Idle, c.state)
    }
}
