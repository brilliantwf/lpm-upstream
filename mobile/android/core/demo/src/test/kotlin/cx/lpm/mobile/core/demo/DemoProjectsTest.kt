package cx.lpm.mobile.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoProjectsTest {
    @Test
    fun projectsMatchTheSeed() = runTest {
        val demo = DemoHarness(this)
        val projects = demo.request("projects").single("projects").dictList("projects")

        assertEquals(
            listOf("storefront", "api-gateway", "mobile-app", "blog", "design-system"),
            projects.map { it.string("name") }
        )
        val storefront = projects[0]
        assertEquals(true, storefront.bool("running"))
        assertEquals(
            listOf("web", "api"),
            storefront.dictList("services").map {
                it.string("name")
            }
        )
        assertEquals(
            listOf(3000L, 4000L, 0L),
            storefront.dictList("allServices").map { it.long("port") }
        )
        assertEquals(
            listOf("Test", "Lint", "Fix tests", "Review", "Review"),
            storefront.dictList("actions").map { it.string("label") }
        )
        assertEquals(
            "🚀 Release · v2.4.1 · 3d ago",
            projects[1].dictList("actions")[1].string("label")
        )
        assertEquals(listOf("work", "default"), projects[2].strings("claudeAccounts"))
        assertEquals("work", projects[4].string("claudeAccount"))
        assertEquals(false, projects[4].bool("autoPull"))
    }

    @Test
    fun servicesReportPanesForRunningOnes() = runTest {
        val demo = DemoHarness(this)
        val services = demo.request("services", "project" to "storefront").single("services")
            .dictList("services")

        assertEquals(listOf(true, true, false), services.map { it.bool("running") })
        assertEquals(listOf(0L, 1L, null), services.map { it.long("paneIndex") })
    }

    @Test
    fun sidebarHoldsTheClientWorkFolder() = runTest {
        val demo = DemoHarness(this)
        val sidebar = demo.request("sidebar").single("sidebar")

        assertEquals(
            listOf("storefront", "api-gateway", "mobile-app", "group:client-work"),
            sidebar.strings("order")
        )
        val folder = sidebar.dictList("groups").single()
        assertEquals("Client Work", folder.string("name"))
        assertEquals(listOf("blog", "design-system"), folder.strings("members"))
    }

    @Test
    fun startBringsServicesUpOnTimers() = runTest {
        val demo = DemoHarness(this)
        demo.send("start", "name" to "api-gateway", "profile" to "")
        demo.advance(0.7)
        assertTrue(demo.pushed.isEmpty())

        demo.advance(0.1)
        assertEquals(listOf(800L to "projects-changed"), demo.pushed.map { it.at to it.t })
        demo.advance(0.8)
        assertEquals(
            listOf("projects-changed", "status-changed", "projects-changed"),
            demo.pushed.map { it.t }
        )
        val gateway = demo.request("projects").single("projects").dictList("projects")[1]
        assertEquals(true, gateway.bool("running"))
        assertEquals(listOf("server"), gateway.dictList("services").map { it.string("name") })
    }

    @Test
    fun duplicateStreamsEachCopy() = runTest {
        val demo = DemoHarness(this)
        val frames = demo.exchange(
            3.0,
            "duplicate",
            "name" to "storefront",
            "count" to 2,
            "labels" to listOf("", "Feature X")
        )

        assertEquals(
            listOf(
                700L to "duplicateProgress",
                700L to "projects-changed",
                1400L to "duplicateProgress"
            ),
            frames.take(3).map { it.at to it.t }
        )
        val done = frames.single("duplicate")
        assertEquals(listOf("storefront-2", "feature-x"), done.strings("names"))
        val copy = demo.request("projects").single("projects").dictList("projects")
            .first { it.string("name") == "feature-x" }
        assertEquals("storefront", copy.string("parentName"))
        assertEquals("Feature X", copy.string("label"))
        assertFalse(copy.bool("running")!!)
    }

    @Test
    fun createProjectRejectsADuplicateName() = runTest {
        val demo = DemoHarness(this)
        val reply = demo.request("createProject", "name" to "storefront").single("createProject")

        assertEquals(false, reply.bool("ok"))
        assertEquals("A project named “storefront” already exists.", reply.string("error"))
    }
}
