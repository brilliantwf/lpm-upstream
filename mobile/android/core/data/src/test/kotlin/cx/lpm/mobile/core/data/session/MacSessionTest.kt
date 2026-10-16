package cx.lpm.mobile.core.data.session

import cx.lpm.mobile.core.data.client.CertPinning
import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.startProject
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.demo.DemoServer
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacSessionTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    @Test
    fun `entering Demo Mode loads the demo projects`() = runTest {
        val h = SessionHarness(this)
        h.session.enterDemo()
        settle(500)

        val state = h.session.state.value
        assertTrue(state.demoMode)
        assertEquals(LpmClient.State.Ready, state.connection)
        assertEquals(DemoServer.SERVER_NAME, state.activeRecord?.name)
        val projects = h.projects.state.value
        assertTrue(projects.projectsLoaded)
        assertTrue(projects.projects.any { it.name == "storefront" })
        assertTrue(projects.groups.any { it.name == "Client Work" })
    }

    @Test
    fun `leaving Demo Mode persists nothing about the demo Mac`() = runTest {
        val h = SessionHarness(this)
        h.session.enterDemo()
        settle(500)
        h.session.exitDemo()
        settle(5_000)

        val state = h.session.state.value
        assertFalse(state.demoMode)
        assertEquals(emptyList<Any>(), state.macs)
        assertNull(state.activeMacId)
        assertEquals(LpmClient.State.Idle, state.connection)
        assertTrue(h.projects.state.value.projects.isEmpty())
        // Neither the in-memory snapshot nor the store behind it saw a record.
        assertNull(h.prefs.string(PreferenceKeys.MACS))
        assertNull(h.prefs.string(PreferenceKeys.ACTIVE_MAC_ID))
        assertTrue(h.prefsStore.current.asMap().isEmpty())
        assertTrue(h.credentialsStore.current.asMap().isEmpty())
        assertTrue(h.snapshotDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a code pairing saves the credential, pin and record, and makes it active`() = runTest {
        val h = SessionHarness(this)
        val certificate = HeldCertificate.Builder().commonName("lpm").build().certificate
        h.session.pair(hosts = listOf("192.168.1.20"), port = 8765, code = "481516")
        settle()

        val socket = h.transport.last
        // The TLS handshake: no pin yet and no QR fingerprint, so trust on first use.
        socket.trust.checkServerTrusted(arrayOf(certificate), "ECDHE_RSA")
        socket.open()
        settle()
        val pair = socket.sentOf("pair").single()
        assertEquals("481516", pair.string("code"))
        assertEquals("Test Phone", pair.string("name"))

        socket.receive(
            """{"t":"paired","deviceId":"dev-9","token":"tok-9","serverId":"srv-9",""" +
                """"serverName":"Studio Mac","hosts":["192.168.1.20","100.101.102.103"],""" +
                """"platform":"macos"}"""
        )
        settle()

        val state = h.session.state.value
        val record = state.macs.single()
        assertEquals(record.localId, state.activeMacId)
        assertEquals("srv-9", record.serverId)
        assertEquals("Studio Mac", record.name)
        assertEquals(listOf("192.168.1.20", "100.101.102.103"), record.hosts)
        assertEquals("macos", record.platform)
        assertNotNull(record.lastConnected)
        assertEquals(LpmClient.State.Ready, state.connection)
        assertEquals(LpmClient.Credential("dev-9", "tok-9"), h.credentials.load(record.localId))
        val pin = CertPinning.sha256Hex(certificate.encoded)
        assertEquals(pin, h.credentials.loadPin(record.localId))
        assertEquals(listOf(record), h.macStore.loadRecords())
        assertEquals(record.localId, h.macStore.loadActiveId())
        assertFalse(h.session.link.state.value.pairing)
        // The first requests of a fresh link went out.
        val firstRequests = listOf("projects", "sidebar", "duplicateDefaults", "jobs")
        assertTrue(socket.sentTypes.containsAll(firstRequests))

        settle(1_000)
        assertEquals(record.localId, h.session.link.state.value.summary?.macId)
    }

    @Test
    fun `re-pairing a saved Mac refreshes its record in place and folds addresses in`() = runTest {
        val h = SessionHarness(this)
        val saved = h.savedMac(host = "100.101.102.103", serverId = "srv-1")
        h.session.bootstrap()
        settle()
        h.session.pair(listOf("192.168.1.30"), 8765, "000000", replacing = saved.localId)
        settle()
        val socket = h.transport.last
        socket.open()
        settle()
        // The Mac doesn't answer at an address the record knows, so no `replaces`.
        assertNull(socket.sentOf("pair").single().string("replaces"))
        socket.receive(
            """{"t":"paired","deviceId":"dev-2","token":"tok-2",""" +
                """"serverId":"srv-1","serverName":"Studio"}"""
        )
        settle()

        val record = h.session.state.value.macs.single()
        assertEquals(saved.localId, record.localId)
        assertEquals(listOf("192.168.1.30", "100.101.102.103"), record.hosts)
        assertEquals(LpmClient.Credential("dev-2", "tok-2"), h.credentials.load(saved.localId))
    }

    @Test
    fun `a reconnect pins the first certificate, and a changed one asks to compare`() = runTest {
        val h = SessionHarness(this)
        val record = h.savedMac()
        val first = HeldCertificate.Builder().commonName("lpm").build().certificate
        h.session.bootstrap()
        settle()
        var socket = h.transport.last
        socket.trust.checkServerTrusted(arrayOf(first), "ECDHE_RSA")
        socket.open()
        settle()
        socket.receive("""{"t":"ready","serverId":"srv-1"}""")
        settle()
        assertEquals(CertPinning.sha256Hex(first.encoded), h.credentials.loadPin(record.localId))

        socket.fail()
        settle(2_000)
        socket = h.transport.last
        val changed = HeldCertificate.Builder().commonName("lpm").build().certificate
        runCatching { socket.trust.checkServerTrusted(arrayOf(changed), "ECDHE_RSA") }
        settle()

        assertTrue(h.session.state.value.identityMismatch)
        val link = h.session.link.state.value
        assertTrue(link.identityCheckOpen)
        assertEquals(CertPinning.sha256Hex(changed.encoded), link.newFingerprint)

        h.session.trustNewIdentity()
        settle()
        assertEquals(CertPinning.sha256Hex(changed.encoded), h.credentials.loadPin(record.localId))
        assertFalse(h.session.state.value.identityMismatch)
    }

    @Test
    fun `a rejected credential asks for re-pairing instead of retrying`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.receive("""{"t":"error","error":"unauthorized"}""")
        settle()
        assertTrue(h.session.state.value.needsRepair)

        h.session.repairActiveMac()
        val link = h.session.link.state.value
        assertTrue(h.session.state.value.addingMac)
        assertEquals(RepairReason.NotRecognized, link.repairReason)
        assertEquals(h.session.state.value.activeMacId, link.repairMacId)
    }

    @Test
    fun `removing the only Mac forgets it, its credential and its saved list`() = runTest {
        val h = SessionHarness(this)
        h.connectReady()
        val id = h.session.state.value.activeMacId!!
        h.session.removeMac(id)
        settle()

        assertTrue(h.session.state.value.macs.isEmpty())
        assertNull(h.session.state.value.activeMacId)
        assertNull(h.credentials.load(id))
        assertEquals(emptyList<Any>(), h.macStore.loadRecords())
    }

    @Test
    fun `an action tapped while away goes out with the first live list`() = runTest {
        val h = SessionHarness(this)
        h.savedMac()
        h.session.bootstrap()
        settle()
        h.session.whenConnected("Start", "storefront") { it.startProject("storefront") }
        assertEquals(1, h.session.link.state.value.waitingCount)

        val socket = h.transport.last
        socket.open()
        settle()
        socket.receive("""{"t":"ready"}""")
        settle()
        assertFalse("start" in socket.sentTypes)
        socket.receive("""{"t":"projects","projects":[{"name":"storefront"}]}""")
        settle()

        assertTrue("start" in socket.sentTypes)
        assertEquals(0, h.session.link.state.value.waitingCount)
    }

    @Test
    fun `address probes expect the saved Mac's pinned certificate`() = runTest {
        val h = SessionHarness(this)
        val record = h.savedMac()
        h.credentials.savePin("ab12", record.localId)
        h.macStore.saveRecords(listOf(record.copy(hosts = listOf("192.168.1.20", "100.101.1.2"))))
        h.prober.reachable += "100.101.1.2"
        h.session.bootstrap()
        settle()

        assertEquals("ab12", h.prober.identities.first().pin)
        assertEquals("wss://100.101.1.2:8765/", h.transport.last.url)
    }
}
