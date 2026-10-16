package cx.lpm.mobile.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoActionsTest {
    private fun DemoHarness.poll(runId: String): Dict =
        request("actionBgOutput", "project" to "api-gateway", "runId" to runId)
            .single("actionBgOutput")

    @Test
    fun backgroundRunGrowsBetweenPollsAndFinishes() = runTest {
        val demo = DemoHarness(this)
        val started = demo.request(
            "runActionBackground",
            "project" to "api-gateway",
            "action" to "test",
            "inputValues" to emptyMap<String, String>(),
            "runId" to "run-1"
        ).single("runActionBackground")
        assertEquals(true, started.bool("ok"))

        val first = demo.poll("run-1")
        assertEquals("$ go test ./...\n", first.string("text"))
        assertEquals(true, first.bool("running"))
        assertEquals("Test", first.string("label"))

        demo.advance(2.5)
        assertEquals(
            "$ go test ./...\nok  api-gateway/internal/auth  0.31s\nok  api-gateway/internal/routes  0.58s\n",
            demo.poll("run-1").string("text")
        )

        demo.advance(3.5)
        val done = demo.poll("run-1")
        assertEquals(false, done.bool("running"))
        assertEquals(true, done.bool("success"))
        assertTrue(done.string("text")!!.endsWith("ok  api-gateway/cmd/server  0.22s\nPASS\n"))

        val runs = demo.request(
            "backgroundRuns",
            "project" to "api-gateway"
        ).single("backgroundRuns")
            .dictList("runs")
        assertEquals(listOf("run-1"), runs.map { it.string("runId") })
        assertEquals(false, runs.single().containsKey("text"))

        demo.advance(301.0)
        assertEquals(false, demo.poll("run-1").bool("found"))
    }

    @Test
    fun cancelStopsTheOutputWhereItWas() = runTest {
        val demo = DemoHarness(this)
        demo.send(
            "runActionBackground",
            "project" to "blog",
            "action" to "new-post",
            "inputValues" to mapOf("title" to "Hello World!"),
            "runId" to "run-2"
        )
        demo.advance(2.5)
        demo.send("cancelActionBackground", "runId" to "run-2")
        demo.advance(3.0)

        val run = demo.request(
            "actionBgOutput",
            "project" to "blog",
            "runId" to "run-2"
        ).single("actionBgOutput")
        assertEquals(
            "$ npm run new-post -- \"Hello World!\"\nCreating a new draft…\nposts/hello-world.md created\n",
            run.string("text")
        )
        assertEquals("cancelled", run.string("error"))
        assertEquals(false, run.bool("success"))
    }

    @Test
    fun terminalActionOpensATerminalAndStreamsOutput() = runTest {
        val demo = DemoHarness(this)
        demo.advance(1.0)
        val reply = demo.request(
            "runAction",
            "project" to "storefront",
            "action" to "lint"
        ).single("runAction")
        assertEquals(true, reply.bool("ok"))

        val terminals = demo.request("terminals", "project" to "storefront").single("terminals")
            .dictList("terminals")
        val run = terminals.last()
        assertEquals("demo-storefront-run-${BASE_MS + 1000}", run.string("id"))
        assertEquals("Lint", run.string("label"))

        demo.clear()
        demo.advance(1.0)
        val out = demo.pushed.single().frame
        assertEquals(run.string("id"), out.string("id"))
        val expected = "> storefront@1.4.2 lint\r\n> next lint\r\n\r\n" +
            "✔ No ESLint warnings or errors\r\n"
        assertTrue(out.string("d")!!.contains(expected))
    }
}
