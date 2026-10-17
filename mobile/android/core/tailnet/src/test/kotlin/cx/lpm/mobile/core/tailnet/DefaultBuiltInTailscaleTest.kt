package cx.lpm.mobile.core.tailnet

import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBuiltInTailscaleTest {
    private val dir: File = Files.createTempDirectory("tailnet").toFile()
    private val network = FakeNetwork()
    private val visibility = FakeVisibility()

    /** Reads the test scheduler's time, plus [frozenFor] to stand in for a frozen process. */
    private class SchedulerClock(private val scope: TestScope) : Clock() {
        var frozenFor = 0L

        override fun instant(): Instant =
            Instant.ofEpochMilli(scope.testScheduler.currentTime + frozenFor)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    private fun TestScope.tailscale(
        node: FakeNode,
        prefs: AppPreferences = AppPreferences(FakeStore(), backgroundScope),
        clock: Clock = SchedulerClock(this)
    ) = DefaultBuiltInTailscale(
        node = node,
        network = network,
        prefs = prefs,
        clock = clock,
        device = ThisDevice("Pixel"),
        visibility = visibility,
        stateDir = dir,
        commands = UnconfinedTestDispatcher(testScheduler),
        scope = backgroundScope
    ).also { runCurrent() }

    @Test
    fun routesOnlyTailnetAddressesThroughABridgeWhileTheNodeIsOn() = runTest {
        val node = FakeNode()
        val tailscale = tailscale(node)
        assertEquals("100.101.2.3" to 8765, tailscale.route("100.101.2.3", 8765))

        tailscale.setEnabled(true)
        runCurrent()
        // The node learns the network before it starts, so it never looks itself.
        assertEquals(listOf("network wlan0 192.168.1.1", "start lpm-phone"), node.calls)
        assertEquals("127.0.0.1" to 41_000, tailscale.route("100.101.2.3", 8765))
        assertEquals("127.0.0.1" to 41_000, tailscale.route("mac.tail1234.ts.net", 8765))
        assertEquals("192.168.1.20" to 8765, tailscale.route("192.168.1.20", 8765))
        assertEquals("100.128.0.1" to 8765, tailscale.route("100.128.0.1", 8765))

        network.onChange?.invoke(DefaultNetwork("rmnet_data0", ""))
        runCurrent()
        assertEquals("network rmnet_data0 ", node.calls.last())

        tailscale.setEnabled(false)
        runCurrent()
        assertEquals("stop", node.calls.last())
        assertEquals(1, network.stops)
        assertEquals("100.101.2.3" to 8765, tailscale.route("100.101.2.3", 8765))
    }

    @Test
    fun aFailedBridgeDialsTheAddressItself() = runTest {
        val node = FakeNode().apply { bridgePort = -1 }
        val tailscale = tailscale(node)
        tailscale.setEnabled(true)
        runCurrent()
        assertEquals("100.101.2.3" to 8765, tailscale.route("100.101.2.3", 8765))
    }

    @Test
    fun publishesWhatTheNodeReports() = runTest {
        val node = FakeNode()
        node.onStart = listOf(TailnetStatus(state = "starting"), TailnetStatus(state = "running"))
        val tailscale = tailscale(node)
        tailscale.setEnabled(true)
        runCurrent()
        tailscale.awaitState("running")
        assertEquals("running", tailscale.status.value.state)
    }

    @Test
    fun followsTheSavedSwitchOnceThePreferencesLoad() = runTest {
        val store = FakeStore()
        AppPreferences(
            store,
            backgroundScope
        ).setBool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED, true)
        runCurrent()
        val prefs = AppPreferences(store, backgroundScope)
        val node = FakeNode()
        val tailscale = tailscale(node, prefs)
        assertFalse(tailscale.enabled.value)
        assertTrue(node.calls.isEmpty())

        prefs.load()
        runCurrent()
        assertTrue(tailscale.enabled.value)
        assertEquals("start lpm-phone", node.calls.last())
    }

    @Test
    fun aNodeTheAppKeptRunningInTheBackgroundIsLeftAlone() = runTest {
        val node = FakeNode().apply { onStart = listOf(TailnetStatus(state = "running")) }
        val tailscale = tailscale(node)
        tailscale.setEnabled(true)
        runCurrent()
        tailscale.awaitState("running")

        visibility.background()
        advanceTimeBy(60_000)
        visibility.foreground()
        runCurrent()
        assertFalse("restart" in node.calls)
    }

    @Test
    fun aRunningNodeRestartsAfterTheAppWasFrozen() = runTest {
        val clock = SchedulerClock(this)
        val node = FakeNode().apply { onStart = listOf(TailnetStatus(state = "running")) }
        val tailscale = tailscale(node, clock = clock)
        tailscale.setEnabled(true)
        runCurrent()
        tailscale.awaitState("running")

        visibility.background()
        clock.frozenFor = 25_000
        visibility.foreground()
        runCurrent()
        assertEquals("restart", node.calls.last())
    }

    @Test
    fun anOverdueHeartbeatStillCountsAsFrozen() = runTest {
        val clock = SchedulerClock(this)
        val node = FakeNode().apply { onStart = listOf(TailnetStatus(state = "running")) }
        val tailscale = tailscale(node, clock = clock)
        tailscale.setEnabled(true)
        runCurrent()
        tailscale.awaitState("running")

        visibility.background()
        clock.frozenFor = 25_000
        // The tick that was due while frozen runs before the app comes back.
        advanceTimeBy(5_001)
        visibility.foreground()
        runCurrent()
        assertEquals("restart", node.calls.last())
    }

    @Test
    fun aNodeMidSignInIsNotRestarted() = runTest {
        val clock = SchedulerClock(this)
        val node = FakeNode().apply {
            onStart =
                listOf(
                    TailnetStatus(state = "needsLogin", authUrl = "https://login.tailscale.com/a/1")
                )
        }
        val tailscale = tailscale(node, clock = clock)
        tailscale.setEnabled(true)
        runCurrent()
        tailscale.awaitState("needsLogin")

        visibility.background()
        clock.frozenFor = 60_000
        visibility.foreground()
        runCurrent()
        assertFalse("restart" in node.calls)
    }

    @Test
    fun withoutTheNativeNodeItReportsUnavailableAndDialsDirectly() = runTest {
        val node = FakeNode(loads = false)
        val prefs = AppPreferences(FakeStore(), backgroundScope)
        val tailscale = tailscale(node, prefs)

        tailscale.setEnabled(true)
        runCurrent()
        assertTrue(tailscale.enabled.value)
        assertEquals(true, prefs.bool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED))
        assertEquals("error", tailscale.state.value)
        assertEquals(DefaultBuiltInTailscale.UNAVAILABLE, tailscale.status.value.error)
        assertEquals("100.101.2.3" to 8765, tailscale.route("100.101.2.3", 8765))
        tailscale.signOut()
        assertTrue(node.calls.isEmpty())

        tailscale.setEnabled(false)
        runCurrent()
        assertEquals("off", tailscale.state.value)
        assertNull(tailscale.status.value.error)
    }

    @Test
    fun recognizesTailnetHosts() {
        assertTrue(DefaultBuiltInTailscale.isTailnetHost("100.64.0.1"))
        assertTrue(DefaultBuiltInTailscale.isTailnetHost("100.127.255.254"))
        assertTrue(DefaultBuiltInTailscale.isTailnetHost("mac.tail1234.ts.net."))
        assertFalse(DefaultBuiltInTailscale.isTailnetHost("100.63.0.1"))
        assertFalse(DefaultBuiltInTailscale.isTailnetHost("100.128.0.1"))
        assertFalse(DefaultBuiltInTailscale.isTailnetHost("192.168.1.20"))
        assertFalse(DefaultBuiltInTailscale.isTailnetHost("100.64.1"))
        assertFalse(DefaultBuiltInTailscale.isTailnetHost("ts.net.example.com"))
    }

    @Test
    fun readsTheNodeStatus() {
        val (status, version) = DefaultBuiltInTailscale.parseStatus(
            """{"state":"running","ip":"100.101.2.3","dnsName":"lpm-phone.tail1234.ts.net",""" +
                """"account":"me@example.com","tailnet":"tail1234.ts.net","version":7}"""
        )!!
        assertEquals(7L, version)
        assertEquals("running", status.state)
        assertEquals("100.101.2.3", status.ip)
        assertEquals("lpm-phone.tail1234.ts.net", status.dnsName)
        assertEquals("me@example.com", status.account)
        assertNull(DefaultBuiltInTailscale.parseStatus(""))
        assertNull(DefaultBuiltInTailscale.parseStatus("""{"version":1}"""))
    }
}
