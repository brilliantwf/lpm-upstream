package cx.lpm.mobile.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityOrderTest {
    private fun rows(vararg entries: Pair<String, String>) = build(
        entries.map { (name, value) ->
            project(name, statusEntries = listOf(status("codex_$name", value, pane = "t-$name")))
        }
    ).rows

    private val List<ActivityRow>.names get() = map { it.project.name }

    @Test
    fun `a held order keeps rows still and sends newcomers to the bottom`() {
        val before = rows("a" to "Running", "b" to "Done")
        val held = freezeOrder(before)
        // "b" now needs you and "c" arrives, both mid-hold.
        val after = rows("a" to "Running", "b" to "Waiting", "c" to "Error")
        assertEquals(listOf("b", "c", "a"), after.names)

        val shown = heldOrder(after, held)
        assertEquals(listOf("a", "b", "c"), shown.names)
        assertTrue(orderIsStale(shown))
        assertEquals(after, heldOrder(after, null))
        assertFalse(orderIsStale(after))
    }

    @Test
    fun `grouping keeps attention order and sets nameless rows apart`() {
        val activity = build(
            listOf(
                project(
                    "api",
                    statusEntries = listOf(
                        status("codex_1", "Waiting", pane = "t1"),
                        status("claude_code_1", "Done", pane = "t2")
                    )
                ),
                project("blog", statusEntries = listOf(status("codex_2", "Error", pane = "t3")))
            ),
            jobs = listOf(
                job("solo", standalone = true),
                job("weekly", targets = listOf("api", "blog"))
            )
        )
        val groups = activityGroups(activity.rows)
        assertEquals(
            listOf("api", "blog", STANDALONE_PROJECT_LABEL, MULTI_PROJECT_LABEL),
            groups.map { it.id }
        )
        assertEquals(2, groups.first().rows.size)
    }
}
