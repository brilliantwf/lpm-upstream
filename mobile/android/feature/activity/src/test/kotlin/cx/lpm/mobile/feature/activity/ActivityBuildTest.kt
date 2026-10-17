package cx.lpm.mobile.feature.activity

import cx.lpm.mobile.core.model.AgentState
import cx.lpm.mobile.core.model.Profile
import cx.lpm.mobile.core.model.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityBuildTest {
    private val web = Service("web", 3000)
    private val api = Service("api", 4000)
    private val worker = Service("worker", 0)

    @Test
    fun `agent rows read like the desktop's and sort by what waits on you`() {
        val activity = build(
            listOf(
                project(
                    "storefront",
                    statusEntries = listOf(
                        status("claude_code_1", "Done", pane = "t1", at = NOW - 10_000),
                        status("codex_1", "Waiting", pane = "t2", at = NOW - 5_000),
                        status("gemini_1", "Running", pane = "t3", at = NOW - 90_000),
                        status("cursor_1", "Waiting", pane = "t4", at = NOW - 50_000)
                    )
                )
            ),
            titles = mapOf("storefront" to mapOf("t1" to "Fix login", "t2" to "Codex"))
        )
        assertEquals(
            listOf(
                "agent:storefront:cursor_1",
                "agent:storefront:codex_1",
                "agent:storefront:gemini_1",
                "agent:storefront:claude_code_1"
            ),
            activity.rows.map { it.id }
        )
        val done = activity.rows.last()
        assertEquals("Claude Code", done.title)
        assertEquals("Fix login", done.tabTitle)
        assertEquals(AgentState.Done, done.state)
        // A tab named after its agent says nothing the row doesn't already.
        assertNull(activity.rows[1].tabTitle)
        assertEquals("Gemini CLI", activity.rows[2].title)
    }

    @Test
    fun `a stamp from the Mac's future is clamped and an unstamped row has no time`() {
        val rows = build(
            listOf(
                project(
                    "api",
                    statusEntries = listOf(
                        status("claude_code_1", "Running", pane = "t1", at = NOW + 60_000),
                        status("codex_1", "Running", pane = "", at = 0)
                    )
                )
            )
        ).rows
        assertEquals(NOW, rows.first().stateSince)
        assertEquals("working 0s", activityElapsedLabel(rows.first(), NOW))
        val unstamped = rows.last()
        assertNull(unstamped.stateSince)
        assertNull(unstamped.terminalId)
        assertEquals("", activityElapsedLabel(unstamped, NOW))
    }

    @Test
    fun `only a finish or a problem alone on its terminal can be cleared`() {
        val rows = build(
            listOf(
                project(
                    "api",
                    statusEntries = listOf(
                        status("claude_code_1", "Done", pane = "t1"),
                        status("claude_code_2", "Error", pane = "t2"),
                        status("codex_2", "Error", pane = "t2"),
                        status("codex_3", "Waiting", pane = "t3"),
                        status("codex_4", "Running", pane = "t4"),
                        status("codex_5", "Idle", pane = "t5"),
                        status("codex_6", "Done", pane = "")
                    )
                )
            )
        ).rows.associateBy { it.id.substringAfterLast(':') }
        assertTrue(rows.getValue("claude_code_1").dismissable)
        assertNull(rows.getValue("claude_code_1").dismissBlocked)
        val shared =
            "Another agent shares this terminal — clear it from the terminal so only one goes."
        assertEquals(shared, rows.getValue("claude_code_2").dismissBlocked)
        assertEquals(shared, rows.getValue("codex_2").dismissBlocked)
        assertEquals(
            "The agent is asking for you — answering it is what clears this.",
            rows.getValue("codex_3").dismissBlocked
        )
        assertEquals(
            "The agent is still working — this clears when it finishes.",
            rows.getValue("codex_4").dismissBlocked
        )
        assertEquals(
            "There is nothing to clear on this one.",
            rows.getValue("codex_5").dismissBlocked
        )
        assertEquals(
            "This isn't tied to an open terminal, so there is nothing to clear.",
            rows.getValue("codex_6").dismissBlocked
        )
        assertFalse(rows.values.filter { it.dismissBlocked != null }.any { it.dismissable })
    }

    @Test
    fun `a machine's agents are listed under its name and cleared there`() {
        val box = ActivityMachine(
            slug = "box",
            name = "Build Box",
            projects = listOf(project("svc", statusEntries = listOf(status("codex_1", "Done"))))
        )
        val row = build(emptyList(), machines = listOf(box)).rows.single()
        assertEquals("agent:box/svc:codex_1", row.id)
        assertEquals(listOf("Build Box"), row.project.tags)
        assertEquals(
            "This agent runs on Build Box — open it there to clear this.",
            row.dismissBlocked
        )
        assertNull(activityDestination(row))
    }

    @Test
    fun `an automation headlines its one project, or none when it spans several`() {
        val rows = build(
            listOf(project("blog"), project("api")),
            jobs = listOf(
                job("nightly", project = "blog", targets = listOf("blog")),
                job("weekly", targets = listOf("blog", "api", "web"), runningCount = 2),
                job("solo", standalone = true),
                job("gone", project = "old", targets = listOf("old")),
                job("idle", project = "api", targets = listOf("api"), running = false)
            )
        ).rows.associateBy { it.jobId }
        assertEquals("blog", rows.getValue("nightly").project.label)
        assertNull(rows.getValue("nightly").detail)
        assertEquals(MULTI_PROJECT_LABEL, rows.getValue("weekly").project.label)
        assertEquals("", rows.getValue("weekly").project.name)
        assertEquals("Running in 2 of 3 projects", rows.getValue("weekly").detail)
        assertEquals(STANDALONE_PROJECT_LABEL, rows.getValue("solo").project.label)
        assertEquals("old", rows.getValue("gone").project.name)
        assertEquals("old", rows.getValue("gone").project.label)
        assertFalse("idle" in rows)
        val nightly = rows.getValue("nightly")
        assertEquals(AgentState.Working, nightly.state)
        assertEquals(NOW - 120_000, nightly.stateSince)
        assertEquals("This automation clears itself when the run finishes.", nightly.dismissBlocked)
    }

    @Test
    fun `a profile that matches the running set stands for its services`() {
        val group = build(
            listOf(
                project(
                    "storefront",
                    running = true,
                    services = listOf(web, api, worker),
                    started = listOf("web", "api"),
                    profiles = listOf(Profile("dev", listOf("web", "api"))),
                    activeProfile = "dev"
                )
            )
        ).services.single()
        assertEquals(listOf("web", "api"), group.running)
        assertEquals(listOf("worker"), group.declared)
        assertEquals(mapOf("web" to 3000, "api" to 4000), group.ports)
        assertEquals(
            listOf(
                ActivityChip(ActivityChip.Kind.Profile, "dev", running = true),
                ActivityChip(ActivityChip.Kind.Service, "worker", running = false)
            ),
            group.chips
        )
    }

    @Test
    fun `a custom mix of services shows every service on its own`() {
        val group = build(
            listOf(
                project(
                    "storefront",
                    running = true,
                    services = listOf(web, api, worker),
                    started = listOf("web", "api"),
                    profiles = listOf(Profile("dev", listOf("web", "api")))
                )
            )
        ).services.single()
        assertEquals(
            listOf("web" to true, "api" to true, "worker" to false),
            group.chips.map { it.name to it.running }
        )
        assertTrue(group.chips.all { it.kind == ActivityChip.Kind.Service })
    }

    @Test
    fun `a crashed service shows on its own chip even under a lit profile`() {
        val p = project(
            "storefront",
            running = true,
            services = listOf(web, api, worker),
            profiles = listOf(Profile("dev", listOf("web", "api"))),
            activeProfile = "dev"
        ).copy(services = listOf(web.copy(exited = true, exitCode = 1), api.copy(exited = true)))
        val group = build(listOf(p)).services.single()
        assertEquals(emptyList<String>(), group.running)
        assertEquals(listOf("web", "api", "worker"), group.declared)
        assertEquals(
            listOf(
                ActivityChip(ActivityChip.Kind.Service, "web", running = false, crashed = true),
                ActivityChip(ActivityChip.Kind.Service, "api", running = false),
                ActivityChip(ActivityChip.Kind.Service, "worker", running = false)
            ),
            group.chips
        )
    }

    @Test
    fun `projects with nothing going are one footer count`() {
        val activity = build(
            listOf(
                project(
                    "storefront",
                    running = true,
                    services = listOf(web),
                    started = listOf("web")
                ),
                project("api", statusEntries = listOf(status("codex_1", "Done"))),
                project("blog"),
                project("docs", running = true),
                project("mobile")
            ),
            jobs = listOf(job("weekly", targets = listOf("blog", "mobile")))
        )
        assertEquals(listOf("storefront"), activity.services.map { it.id })
        assertEquals(1, activity.quietProjectCount)
    }

    @Test
    fun `durations read as the desktop writes them`() {
        assertEquals("0s", formatActivityDuration(-5))
        assertEquals("12s", formatActivityDuration(12))
        assertEquals("4m", formatActivityDuration(240))
        assertEquals("4m 30s", formatActivityDuration(270))
        assertEquals("1h", formatActivityDuration(3_600))
        assertEquals("1h 12m", formatActivityDuration(4_320))
    }

    @Test
    fun `tags name what kind of project a row belongs to`() {
        val copy = ActivityIdentity.of(project("a-copy", parent = "a"))
        val tree = ActivityIdentity.of(project("a-tree", parent = "a", worktree = true))
        val ssh = ActivityIdentity.of(project("box", remote = true))
        assertEquals(listOf("Copy"), copy.tags)
        assertEquals(listOf("Worktree"), tree.tags)
        assertEquals(listOf("SSH"), ssh.tags)
        assertEquals(emptyList<String>(), ActivityIdentity.nameless(MULTI_PROJECT_LABEL).tags)
    }
}
