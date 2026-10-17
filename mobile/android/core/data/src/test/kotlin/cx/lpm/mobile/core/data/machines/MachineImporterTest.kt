package cx.lpm.mobile.core.data.machines

import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.session.AdoptedMachine
import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.data.testing.settleAlive
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MachineImporterTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private val SessionHarness.importer: MachineImporter get() = session.store()

    private fun machine(
        slug: String = "build-box",
        state: String = "ready",
        serverId: String = "srv-linux",
        relay: Boolean = false,
        projects: String? = null
    ) = """{"slug":"$slug","name":"Build Box","platform":"linux","serverId":"$serverId",""" +
        """"state":"$state","relay":$relay${projects?.let { ""","projects":$it""" }.orEmpty()}}"""

    private fun FakeSocket.listMachines(vararg machines: String) =
        receive("""{"t":"machines","machines":[${machines.joinToString(",")}]}""")

    private fun FakeSocket.offer(slug: String = "build-box", hosts: String = """["10.0.0.9"]""") =
        receive(
            """{"t":"machinePair","slug":"$slug","ok":true,"code":"AB12-CD34","hosts":$hosts,""" +
                """"port":8765,"fingerprint":"ab","serverId":"srv-linux","name":"Build Box","platform":"linux"}"""
        )

    @Test
    fun `a ready machine is paired over a throwaway connection and saved beside the Mac`() =
        runTest {
            val h = SessionHarness(this)
            val live = h.connectReady()
            live.listMachines(machine())
            settle()
            assertEquals(
                MachineImportStatus.Adding,
                h.importer.state.value.rows(h.session.state.value.macs).single().status
            )
            assertEquals("build-box", live.sentOf("machinePair").single().string("slug"))

            live.offer()
            settle()
            val pairing = h.transport.last
            pairing.open()
            settle()
            assertEquals("AB12-CD34", pairing.sentOf("pair").single().string("code"))
            pairing.receive(
                """{"t":"paired","deviceId":"dev-2","token":"tok-2","serverId":"srv-linux",""" +
                    """"serverName":"build-box.local","hosts":["192.168.1.40"],"platform":"linux"}"""
            )
            settle()

            val macs = h.session.state.value.macs
            val saved = macs.single { it.serverId == "srv-linux" }
            assertEquals(listOf("10.0.0.9", "192.168.1.40"), saved.hosts)
            assertEquals("build-box.local", saved.name)
            assertEquals("Build Box", saved.displayName)
            assertNotNull(h.credentials.load(saved.localId))
            assertTrue(pairing.closed)
            // The live Mac stays active, and the machine is no longer pending.
            assertEquals("srv-1", h.session.state.value.activeRecord?.serverId)
            assertTrue(h.importer.state.value.rows(macs).isEmpty())
            assertTrue(h.prefs.string(PreferenceKeys.MACHINE_IMPORTS)!!.contains("srv-1/build-box"))
            val section = h.importer.section("build-box")!!
            assertEquals(saved.localId, section.record?.localId)
            assertTrue(section.canOpen)
        }

    @Test
    fun `a failure is shown and not retried on its own for ten minutes`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        live.listMachines(machine())
        settle()
        live.receive(
            """{"t":"machinePair","slug":"build-box","ok":false,"error":"not connected"}"""
        )
        settle()
        val failed = MachineImportStatus.Failed("The Mac isn't connected to it right now.")
        assertEquals(
            failed,
            h.importer.state.value.rows(h.session.state.value.macs).single().status
        )

        live.listMachines(machine())
        settle()
        assertEquals(1, live.sentOf("machinePair").size)

        settleAlive(live, 600_000)
        live.listMachines(machine())
        settle()
        assertEquals(2, live.sentOf("machinePair").size)
    }

    @Test
    fun `adding by hand retries a failure at once`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        live.listMachines(machine())
        settle()
        live.receive("""{"t":"machinePair","slug":"build-box","ok":false,"error":"timed out"}""")
        settle()
        assertEquals(
            MachineImportStatus.Failed("It didn't answer in time."),
            h.importer.state.value.rows(h.session.state.value.macs).single().status
        )

        h.importer.add("build-box")
        settle()
        assertEquals(2, live.sentOf("machinePair").size)
        assertEquals(
            MachineImportStatus.Adding,
            h.importer.state.value.rows(h.session.state.value.macs).single().status
        )
    }

    @Test
    fun `an offer that never comes, no address, or none that answers each says why`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        fun status() = h.importer.state.value.rows(h.session.state.value.macs).single().status

        live.listMachines(machine())
        settleAlive(live, 30_000)
        assertEquals(MachineImportStatus.Failed("It didn't answer in time."), status())

        h.importer.add("build-box")
        settle()
        live.offer(hosts = "[]")
        settle()
        assertEquals(
            MachineImportStatus.Failed("The Mac doesn't know an address for it."),
            status()
        )

        h.importer.add("build-box")
        settle()
        live.offer(hosts = """["10.0.0.9","100.64.0.9"]""")
        settle()
        assertEquals(
            MachineImportStatus.Failed("This phone can't reach it on port 8765."),
            status()
        )
    }

    @Test
    fun `an expired code is the machine's answer, not a lost connection`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        live.listMachines(machine())
        settle()
        live.offer()
        settle()
        val pairing = h.transport.last
        pairing.open()
        settle()
        pairing.receive("""{"t":"error","error":"pairing rejected"}""")
        settle()
        assertEquals(
            MachineImportStatus.Failed("Its pairing code expired. Try again."),
            h.importer.state.value.rows(h.session.state.value.macs).single().status
        )
    }

    @Test
    fun `a machine removed from the phone is never re-added on its own`() = runTest {
        val h = SessionHarness(this)
        h.prefs.setString(PreferenceKeys.MACHINE_IMPORTS, """{"srv-1/build-box":"GONE"}""")
        val live = h.connectReady()
        live.listMachines(machine(relay = true))
        settle()
        val section = h.importer.section("build-box")!!
        assertEquals(MachineImportStatus.Removed, section.pending)
        assertTrue(section.throughMac)
        assertTrue(live.sentOf("machinePair").isEmpty())

        h.importer.add("build-box")
        settle()
        assertEquals(1, live.sentOf("machinePair").size)
    }

    @Test
    fun `a machine that removed this phone is not added again until paired there`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        fun status() = h.importer.state.value.rows(h.session.state.value.macs).single().status
        live.listMachines(machine())
        settle()
        live.offer()
        settle()
        val pairing = h.transport.last
        pairing.open()
        settle()
        pairing.receive("""{"t":"error","error":"revoked"}""")
        settle()
        assertEquals(MachineImportStatus.Revoked, status())
        assertEquals(
            """["srv-1/build-box"]""",
            h.prefs.string(PreferenceKeys.MACHINE_IMPORTS_REVOKED)
        )

        settleAlive(live, 600_000)
        live.listMachines(machine())
        settle()
        assertEquals(1, live.sentOf("machinePair").size)
        assertEquals(MachineImportStatus.Revoked, status())

        // Paired on the machine itself: the refusal is over.
        h.session.adoptMachine(
            AdoptedMachine(
                serverId = "srv-linux",
                serverName = "build-box.local",
                label = "Build Box",
                hosts = listOf("10.0.0.9"),
                port = 8765,
                platform = "linux",
                deviceId = "dev-2",
                token = "tok-2",
                fingerprint = "ab"
            )
        )
        live.listMachines(machine())
        settle()
        assertEquals("[]", h.prefs.string(PreferenceKeys.MACHINE_IMPORTS_REVOKED))
        assertTrue(h.importer.state.value.rows(h.session.state.value.macs).isEmpty())
    }

    @Test
    fun `an offline machine is listed again a few times while it is away`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        val asked = live.sentOf("machines").size
        repeat(4) {
            live.listMachines(machine(state = "offline"))
            settleAlive(live, 30_000)
        }
        assertEquals(asked + 3, live.sentOf("machines").size)
        assertEquals(
            MachineImportStatus.Offline,
            h.importer.state.value.rows(h.session.state.value.macs).single().status
        )
        // Offline machines have no section; the switcher still lists them.
        assertNull(h.importer.section("build-box"))
    }

    @Test
    fun `switching Macs forgets the list but not a failure`() = runTest {
        val h = SessionHarness(this)
        val live = h.connectReady()
        live.listMachines(machine(state = "update"), machine(slug = "mini", serverId = "srv-mini"))
        settle()
        assertEquals(
            MachineImportStatus.NeedsUpdate,
            h.importer.state.value.rows(h.session.state.value.macs).first().status
        )
        h.session.resetSessionState()
        settle()
        val state = h.importer.state.value
        assertNull(state.sourceId)
        assertTrue(state.machines.isEmpty())
        assertTrue(state.adding.isEmpty())
        // The pending offer was cut off by the switch, which isn't the machine's failure.
        assertTrue(state.failures.isEmpty())
    }

    @Test
    fun `a machine's projects nest each copy under the project it was made from`() {
        val rows = machineRows(
            listOf(
                project("api"),
                project("web-2", parent = "web"),
                project("web"),
                project("orphan", parent = "gone")
            )
        )
        assertEquals(
            listOf("api" to false, "web" to false, "web-2" to true, "orphan" to false),
            rows.map { it.project.name to it.isChild }
        )
    }

    private fun project(name: String, parent: String = "") = Project.parse(
        Json.parseToJsonElement("""{"name":"$name","parentName":"$parent"}""") as JsonObject
    )
}
