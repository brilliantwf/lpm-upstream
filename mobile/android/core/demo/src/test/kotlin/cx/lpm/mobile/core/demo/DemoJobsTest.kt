package cx.lpm.mobile.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoJobsTest {
    // 03:00 this morning in New York, the nightly run's last fire.
    private val lastNight = 1_792_047_600L

    private fun DemoHarness.nightly(t: String): Dict =
        request(t, "project" to "storefront", "jobId" to "nightly-tests").single(t)

    @Test
    fun jobsMatchTheSeed() = runTest {
        val demo = DemoHarness(this)
        val jobs = demo.request("jobs").single("jobs").dictList("jobs")

        assertEquals(
            listOf("Nightly tests", "Weekly dependency check"),
            jobs.map { it.string("label") }
        )
        assertEquals(listOf("🌙", "📦"), jobs.map { it.string("emoji") })
        val nightly = jobs[0]
        assertEquals(lastNight, nightly.long("lastRunAt"))
        assertEquals(lastNight + 86_400, nightly.long("nextFireAt"))
        assertEquals(1, nightly.int("unread"))
        assertEquals(
            mapOf("mode" to "calendar", "atMinutes" to 180L, "days" to emptyList<String>()),
            nightly.sub("schedule")
        )
        val weekly = jobs[1]
        assertEquals(false, weekly.bool("enabled"))
        assertEquals(null, weekly["nextFireAt"])
        assertEquals("prompt", weekly.string("runKind"))
    }

    @Test
    fun historyMarksRunsSinceLastRead() = runTest {
        val demo = DemoHarness(this)
        val entries = demo.nightly("jobHistory").dictList("entries")

        assertEquals(
            listOf(
                lastNight - 2 * 86_400 to "error",
                lastNight - 2 * 86_400 + 1560 to "completed",
                lastNight - 86_400 to "completed",
                lastNight to "completed"
            ),
            entries.map { it.long("at") to it.string("result") }
        )
        assertEquals(listOf(null, null, null, true), entries.map { it.bool("unread") })
        assertEquals(
            " ✓ lib/payments.test.ts (12 tests) 271ms",
            entries[0].string("output")!!.lines()[0]
        )
    }

    @Test
    fun runStreamsLiveOutputThenRecordsTheRun() = runTest {
        val demo = DemoHarness(this)
        assertEquals(
            listOf("runJob", "jobs-changed"),
            demo.request("runJob", "project" to "storefront", "jobId" to "nightly-tests")
                .map { it.t }
        )

        demo.advance(3.0)
        assertEquals(
            "\n> storefront@0.4.2 test\n> vitest run\n\n" +
                " RUN  v3.1.4 /Users/demo/dev/storefront\n\n" +
                " ✓ lib/payments.test.ts (12 tests) 262ms\n",
            demo.nightly("jobLiveOutput").sub("live").string("text")
        )

        demo.clear()
        demo.advance(5.2)
        assertEquals(listOf(8200L to "jobs-changed"), demo.pushed.map { it.at to it.t })
        assertEquals(JsonNull, demo.nightly("jobLiveOutput")["live"])
        val last = demo.nightly("jobHistory").dictList("entries").last()
        assertEquals("completed", last.string("result"))
        assertEquals(8, last.int("durationSecs"))
    }

    @Test
    fun savedIntervalJobGetsABandedSchedule() = runTest {
        val demo = DemoHarness(this)
        demo.send(
            "saveJob",
            "id" to "hourly",
            "source" to "project",
            "project" to "storefront",
            "job" to mapOf(
                "label" to "Hourly",
                "schedule" to mapOf("every" to "2h-4h"),
                "run" to mapOf("prompt" to "Check")
            )
        )
        val job = demo.request("jobs").single("jobs").dictList("jobs")
            .first { it.string("id") == "hourly" }

        assertEquals(
            mapOf("mode" to "interval", "everySecs" to 7200L, "everyMaxSecs" to 14_400L),
            job.sub("schedule")
        )
        val config = demo.request("jobConfig", "project" to "storefront", "jobId" to "hourly")
            .single("jobConfig")
        assertEquals("2h-4h", config.sub("job").sub("schedule").string("every"))
    }
}
