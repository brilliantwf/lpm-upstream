package cx.lpm.mobile.feature.memory.format

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryFormatTest {
    private val doc = """
        # Checkout redesign

        ## Goal
          Rebuild checkout as two columns.
        More goal.

        ## Timeline

        ### 2026-10-14 09:00 — claude
        - Done
        ### 2026-10-15 10:00 — codex
        ### 2026-10-15 11:00 — not an agent name
    """.trimIndent()

    @Test
    fun `the goal line is the first text under Goal`() {
        assertEquals("Rebuild checkout as two columns.", memoryGoalLine(doc))
        assertEquals("", memoryGoalLine("# T\n\n## Goal\n\n\n## Current state\nx"))
        assertEquals("", memoryGoalLine("# No goal here"))
    }

    @Test
    fun `the last agent is the newest one-word name after an em dash`() {
        assertEquals("codex", memoryLastAgent(doc))
        assertEquals("", memoryLastAgent("### 2026-10-14 - claude"))
    }

    @Test
    fun `Claude Code gets the slash command, other agents the skill mention`() {
        assertEquals("/lpm-memory", memoryInvocation("claude", ""))
        assertEquals(
            "/lpm-memory checkout-redesign ",
            memoryInvocation("claude", "checkout-redesign")
        )
        assertEquals("\$lpm-memory", memoryInvocation("codex", ""))
        assertEquals("\$lpm-memory auth ", memoryInvocation("", "auth"))
    }

    @Test
    fun `relative times read like iOS's named presentation`() {
        val now = Instant.parse("2026-10-15T12:00:00Z")
        fun ago(seconds: Long) =
            relativeMemoryTime(now.epochSecond - seconds, now, ZoneOffset.UTC, Locale.US)
        assertEquals("", relativeMemoryTime(0, now))
        assertEquals("now", ago(0))
        assertEquals("30 seconds ago", ago(30))
        assertEquals("1 minute ago", ago(60))
        assertEquals("2 hours ago", ago(95 * 60))
        assertEquals("yesterday", ago(26 * 3600))
        assertEquals("5 days ago", ago(7_000 * 60))
        assertEquals("last week", ago(9 * 86_400))
        assertEquals("in 5 minutes", relativeMemoryTime(now.epochSecond + 300, now, ZoneOffset.UTC))
    }
}
