package cx.lpm.mobile.core.data.projects

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.ProjectFolder
import cx.lpm.mobile.core.model.WorkState
import cx.lpm.mobile.core.model.WorkStatusInput
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsStoreTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun FakeSocket.projects(running: Boolean, services: List<String> = emptyList()) {
        val list = services.joinToString(",") { """{"name":"$it","port":3000}""" }
        receive(
            """{"t":"projects","projects":[""" +
                """{"name":"storefront","running":$running,"services":[$list]}]}"""
        )
    }

    @Test
    fun `start spins until the projects push confirms it`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(running = false)
        settle()
        val store = h.projects
        store.startProject(store.state.value.projects.single(), profile = "web")
        assertEquals(mapOf("storefront" to true), store.state.value.pendingRun)
        assertEquals("web", socket.sentOf("start").single().string("profile"))

        socket.projects(running = false)
        settle()
        assertEquals(mapOf("storefront" to true), store.state.value.pendingRun)
        socket.projects(running = true)
        settle()
        assertTrue(store.state.value.pendingRun.isEmpty())
    }

    @Test
    fun `a start the Mac never confirms stops spinning after a timeout`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(running = true)
        settle()
        val store = h.projects
        store.stopProject(store.state.value.projects.single())
        assertEquals(mapOf("storefront" to false), store.state.value.pendingRun)
        settle(11_000)
        assertEquals(mapOf("storefront" to false), store.state.value.pendingRun)
        settle(1_001)
        assertTrue(store.state.value.pendingRun.isEmpty())
    }

    @Test
    fun `a service toggle spins until the service moves the right way`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(running = true, services = listOf("web", "api"))
        settle()
        val store = h.projects
        store.toggleService("storefront", "web")
        assertEquals(
            mapOf("storefront" to mapOf("web" to false)),
            store.state.value.pendingServiceToggle
        )
        assertEquals("web", socket.sentOf("toggleService").single().string("service"))

        socket.projects(running = true, services = listOf("api"))
        settle()
        assertEquals(
            emptyMap<String, Boolean>(),
            store.state.value.pendingServiceToggle["storefront"]
        )

        store.toggleService("storefront", "web")
        assertEquals(mapOf("web" to true), store.state.value.pendingServiceToggle["storefront"])
        settle(12_001)
        assertEquals(
            emptyMap<String, Boolean>(),
            store.state.value.pendingServiceToggle["storefront"]
        )
    }

    @Test
    fun `a refused work status surfaces and re-asks for the list`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(running = false)
        settle()
        val store = h.projects
        store.setWorkStatus("storefront", WorkStatusInput(WorkState.Blocked))
        assertEquals(WorkState.Blocked, store.state.value.projects.single().workStatus?.state)

        val before = socket.sentOf("projects").size
        socket.receive(
            """{"t":"setWorkStatus","ok":false,"project":"storefront","error":"Not allowed"}"""
        )
        settle()
        assertEquals("Not allowed", h.session.alerts.alerts.value.actionError)
        assertEquals(before + 1, socket.sentOf("projects").size)
    }

    @Test
    fun `duplicate progress runs until the reply, and a warning becomes a notice`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(running = false)
        settle()
        val store = h.projects
        store.duplicateProject(
            store.state.value.projects.single(),
            cx.lpm.mobile.core.model.DuplicateOptions(count = 2)
        )
        assertEquals(DuplicateProgress("storefront", 0, 2), store.state.value.duplicateProgress)
        socket.receive("""{"t":"duplicateProgress","done":1,"total":2,"name":"storefront-2"}""")
        settle()
        assertEquals(DuplicateProgress("storefront", 1, 2), store.state.value.duplicateProgress)
        socket.receive(
            """{"t":"duplicate","ok":true,"name":"storefront","warning":"Open the Mac app"}"""
        )
        settle()
        assertNull(store.state.value.duplicateProgress)
        assertEquals("Open the Mac app", h.session.alerts.alerts.value.notice)
    }

    @Test
    fun `the sidebar nests duplicates under their parent and keeps folders in order`() {
        fun project(name: String, parent: String = "") = cx.lpm.mobile.core.model.Project.parse(
            kotlinx.serialization.json.buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive(name))
                put("parentName", kotlinx.serialization.json.JsonPrimitive(parent))
            }
        )
        val state = ProjectsState(
            projects = listOf(
                project("api"),
                project("web"),
                project("web-2", "web"),
                project("blog"),
                project("new")
            ),
            sidebarOrder = listOf("web", "group:g1", "peer:linux", "api"),
            groups = listOf(ProjectFolder("g1", "Client Work", false, listOf("blog")))
        )
        val items = state.sidebarItems(machineSlugs = listOf("linux", "other"))

        assertEquals(
            listOf("p:web", "p:web-2", "g:g1", "m:linux", "p:api", "p:new", "m:other"),
            items.map { it.id }
        )
        val child = items.filterIsInstance<SidebarItem.ProjectItem>().first {
            it.row.project.name ==
                "web-2"
        }
        assertTrue(child.row.isChild)
    }

    @Test
    fun `an auto pull choice shows at once and a refusal re-asks for the list`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.projects(running = false)
        settle()
        val store = h.projects
        store.setAutoPull("storefront", false)
        assertEquals(false, store.state.value.project("storefront")?.autoPull)
        assertEquals(1, socket.sentOf("setAutoPull").size)

        val before = socket.sentOf("projects").size
        socket.receive("""{"t":"setAutoPull","ok":false,"project":"storefront","error":"nope"}""")
        settle()
        assertEquals("nope", h.session.alerts.alerts.value.actionError)
        assertEquals(before + 1, socket.sentOf("projects").size)
    }
}
