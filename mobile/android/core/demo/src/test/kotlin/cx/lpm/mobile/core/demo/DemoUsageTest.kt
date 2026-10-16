package cx.lpm.mobile.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoUsageTest {
    @Test
    fun statsCoverTwoWeeksOfSessions() = runTest {
        val demo = DemoHarness(this)
        val week = demo.exchange(0.6, "stats", "days" to 7).single("stats").sub("stats")

        assertEquals(7, week.int("days"))
        assertEquals(27, week.int("sessions"))
        assertEquals(64_086_054L, week.sub("totals").long("totalTokens"))
        assertEquals(
            listOf("claude" to 18L, "codex" to 9L),
            week.dictList("providers").map { it.string("key") to it.long("sessions") }
        )
        assertEquals(
            listOf("Claude Code", "Codex"),
            week.dictList("providers").map {
                it.string("label")
            }
        )
        val today = week.dictList("daily").last()
        assertEquals("2026-10-15", today.string("date"))
        assertEquals(5_726_919L, today.long("totalTokens"))
        assertEquals(12, week.dictList("recentSessions").size)

        val all = demo.exchange(0.6, "stats", "days" to 0).single("stats").sub("stats")
        assertEquals(54, all.int("sessions"))
        assertEquals(14, all.dictList("daily").size)
        assertEquals(
            listOf("claude" to 36L, "codex" to 18L),
            all.dictList("sources").map { it.string("provider") to it.long("files") }
        )
    }

    @Test
    fun limitsUpdateOnAScriptedTimer() = runTest {
        val demo = DemoHarness(this)
        val frames = demo.exchange(6.0, "limits")

        assertEquals(
            listOf(500L to "limits", 6000L to "limits-changed"),
            frames.map {
                it.at to it.t
            }
        )
        val first = frames[0].frame
        assertEquals(true, first.bool("ok"))
        val main = first.sub("limits").sub("claude:default")
        assertEquals(62L, main.sub("fiveHour").long("usedPercent"))
        assertEquals((BASE_MS + 500) / 1000 + 3 * 3600, main.sub("fiveHour").long("resetsAt"))
        assertEquals(BASE_MS + 500 - 90_000, main.long("updatedAt"))
        assertFalse(first.sub("limits").sub("codex").containsKey("accountId"))

        val changed = frames[1].frame
        assertFalse(changed.containsKey("ok"))
        assertEquals(
            71L,
            changed.sub("limits").sub("claude:default").sub("fiveHour").long("usedPercent")
        )
        assertEquals(98L, changed.sub("limits").sub("codex").sub("weekly").long("usedPercent"))
    }

    @Test
    fun historyPagesSixtyAtATime() = runTest {
        val demo = DemoHarness(this)
        val first = demo.request("historyQuery").single("historyQuery")
        val items = first.dictList("items")

        assertEquals(60, items.size)
        assertEquals(true, first.bool("hasMore"))
        assertEquals(
            "Investigate the flaky checkout e2e — start with the payment mock timeouts",
            items[0].string("text")
        )
        assertEquals("draft", items[0].string("kind"))
        val last = items.last()
        val rest = demo.request(
            "historyQuery",
            "before" to mapOf("at" to last.long("at"), "seq" to last.long("seq"))
        ).single("historyQuery")
        assertEquals(listOf("hm-3", "hm-2", "hm-1"), rest.dictList("items").map { it.string("id") })
        assertEquals(false, rest.bool("hasMore"))
    }

    @Test
    fun claudeAccountsPoolTheMainLoginAndWork() = runTest {
        val demo = DemoHarness(this)
        val accounts = demo.exchange(0.2, "claudeAccounts").single("claudeAccounts")

        assertEquals(
            listOf("default", "work", "personal", "client", "laptop"),
            accounts.dictList("accounts").map { it.string("id") }
        )
        assertEquals(
            listOf(true, true, false, false, false),
            accounts.dictList("accounts").map { it.bool("confirmed") }
        )
        val pools = accounts.sub("switching").dictList("pools")
        assertEquals(listOf("default", "work"), pools[0].strings("members"))
        assertEquals(
            "mobile-app" to "work",
            pools[1].string("project") to pools[1].string("current")
        )
    }
}
