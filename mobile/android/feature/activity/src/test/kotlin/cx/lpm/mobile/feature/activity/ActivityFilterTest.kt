package cx.lpm.mobile.feature.activity

import cx.lpm.mobile.core.model.Profile
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.navigation.AutomationKey
import cx.lpm.mobile.core.navigation.ProjectKey
import cx.lpm.mobile.core.navigation.TerminalKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityFilterTest {
    private val activity = build(
        listOf(
            project(
                "storefront",
                running = true,
                services = listOf(Service("web", 3000), Service("api", 4000)),
                started = listOf("web", "api"),
                profiles = listOf(Profile("dev", listOf("web", "api"))),
                activeProfile = "dev",
                statusEntries = listOf(status("claude_code_1", "Waiting", pane = "t1"))
            ),
            project(
                "blog",
                running = true,
                services = listOf(Service("hugo", 1313), Service("search", 0)),
                started = listOf("hugo")
            )
        ),
        jobs = listOf(job("nightly", project = "blog", targets = listOf("blog"))),
        titles = mapOf("storefront" to mapOf("t1" to "Checkout bug"))
    )

    private fun ids(visible: VisibleActivity) = visible.rows.map { it.id.substringBefore(':') }

    @Test
    fun `each kind keeps only its own rows`() {
        val all = filterActivity(activity, "", ActivityKindFilter.All)
        assertEquals(listOf("agent", "automation"), ids(all))
        assertEquals(2, all.services.size)

        val agents = filterActivity(activity, "", ActivityKindFilter.Agents)
        assertEquals(listOf("agent"), ids(agents))
        assertTrue(agents.services.isEmpty())

        val automations = filterActivity(activity, "", ActivityKindFilter.Automations)
        assertEquals(listOf("automation"), ids(automations))

        val services = filterActivity(activity, " ", ActivityKindFilter.Services)
        assertTrue(services.rows.isEmpty())
        assertEquals(listOf("storefront", "blog"), services.services.map { it.id })
    }

    @Test
    fun `a search matches a project, an agent, or a tab, ignoring case`() {
        assertEquals(
            listOf("agent"),
            ids(filterActivity(activity, "checkout", ActivityKindFilter.All))
        )
        assertEquals(
            listOf("agent"),
            ids(filterActivity(activity, " CLAUDE ", ActivityKindFilter.All))
        )
        assertEquals(
            listOf("automation"),
            ids(filterActivity(activity, "Blog", ActivityKindFilter.All))
        )
    }

    @Test
    fun `a search narrows a service card to what matches, or keeps it whole`() {
        val narrowed = filterActivity(activity, "sear", ActivityKindFilter.All).services.single()
        assertEquals("blog", narrowed.id)
        assertEquals(emptyList<String>(), narrowed.running)
        assertEquals(listOf("search"), narrowed.declared)
        assertEquals(listOf("search"), narrowed.chips.map { it.name })

        val whole = filterActivity(activity, "store", ActivityKindFilter.All).services.single()
        assertEquals(listOf("web", "api"), whole.running)

        assertTrue(filterActivity(activity, "zzz", ActivityKindFilter.All).isEmpty)
    }

    @Test
    fun `a row opens its terminal, its project, or its automation`() {
        val agent = activity.rows.first()
        assertEquals(TerminalKey("storefront", "t1"), activityDestination(agent))
        assertEquals(
            ProjectKey("storefront"),
            activityDestination(agent.copy(terminalId = null))
        )
        assertNull(activityDestination(agent.copy(project = ActivityIdentity.nameless("x"))))
        val job = activity.rows.last()
        val jobId = requireNotNull(job.jobId)
        assertEquals(AutomationKey(project = "blog", jobId = jobId), activityDestination(job))
        val global = job.copy(jobProject = "")
        assertEquals(AutomationKey(project = "", jobId = jobId), activityDestination(global))
    }
}
