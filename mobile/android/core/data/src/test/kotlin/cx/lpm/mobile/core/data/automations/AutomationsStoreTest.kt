package cx.lpm.mobile.core.data.automations

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.long
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationsStoreTest {
    private val jobsFrame =
        """{"t":"jobs","ok":true,"jobs":[""" +
            """{"id":"nightly","project":"storefront","unread":2,"valid":true,"enabled":true},""" +
            """{"id":"weekly","project":"api-gateway","valid":true,"enabled":true,""" +
            """"targets":["api-gateway","storefront"],"unread":1},""" +
            """{"id":"solo","project":"","standalone":true,"unread":3,"valid":true},""" +
            """{"id":"quiet","project":"storefront","unread":0}]}"""

    private fun TestScope.ready(): Pair<SessionHarness, FakeSocket> {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.receive(jobsFrame)
        testScheduler.runCurrent()
        return h to socket
    }

    private val SessionHarness.store: AutomationsStore get() = session.store()

    private fun SessionHarness.job(id: String) = store.state.value.jobs.single { it.id == id }

    @Test
    fun `the jobs list counts unread runs overall and per project`() = runTest {
        val (h, _) = ready()
        val state = h.store.state.value
        assertTrue(state.loaded)
        assertEquals(3, state.unread)
        assertEquals(2, state.unread("storefront"))
        assertEquals(1, state.unread("api-gateway"))
        assertEquals(0, state.unread("blog"))
    }

    @Test
    fun `a refused list keeps the jobs and reports the error`() = runTest {
        val (h, socket) = ready()
        socket.receive("""{"t":"jobs","ok":false,"error":"Mac busy"}""")
        testScheduler.runCurrent()
        assertEquals(4, h.store.state.value.jobs.size)
        assertEquals("Mac busy", h.store.state.value.error)
        socket.receive(jobsFrame)
        testScheduler.runCurrent()
        assertEquals("Mac busy", h.store.state.value.error)
        h.store.dismissError()
        assertNull(h.store.state.value.error)
    }

    @Test
    fun `run, stop and enable reach every project a shared job runs in`() = runTest {
        val (h, socket) = ready()
        val weekly = h.job("weekly")
        h.store.run(weekly)
        h.store.stop(weekly)
        h.store.setEnabled(weekly, false)
        testScheduler.runCurrent()
        for (t in listOf("runJob", "stopJob", "setJobEnabled")) {
            assertEquals(
                listOf("api-gateway", "storefront"),
                socket.sentOf(t).map { it.string("project") }
            )
        }
        assertEquals(
            setOf(automationKey("api-gateway", "weekly"), automationKey("storefront", "weekly")),
            h.store.state.value.pending
        )
    }

    @Test
    fun `a mutation reply clears its spinner and refreshes what's open`() = runTest {
        val (h, socket) = ready()
        h.store.loadHistory("storefront", "nightly")
        h.store.run(h.job("nightly"))
        testScheduler.runCurrent()
        val jobs = socket.sentOf("jobs").size
        val histories = socket.sentOf("jobHistory").size
        socket.receive("""{"t":"runJob","project":"storefront","jobId":"nightly","ok":true}""")
        testScheduler.runCurrent()
        assertTrue(h.store.state.value.pending.isEmpty())
        assertEquals(jobs + 1, socket.sentOf("jobs").size)
        // No history was loaded yet, so there's nothing to refresh.
        assertEquals(histories, socket.sentOf("jobHistory").size)

        socket.receive(
            """{"t":"jobHistory","project":"storefront","jobId":"nightly","ok":true,""" +
                """"entries":[{"at":100,"result":"completed"}]}"""
        )
        socket.receive(
            """{"t":"stopJob","project":"storefront","jobId":"nightly","ok":false,"error":"No"}"""
        )
        testScheduler.runCurrent()
        assertEquals(histories + 1, socket.sentOf("jobHistory").size)
        assertEquals("No", h.store.state.value.error)
    }

    @Test
    fun `reading an automation reads it in every folder it runs in`() = runTest {
        val (h, socket) = ready()
        h.store.markSeen(h.job("weekly"), upTo = 42)
        h.store.markSeen(h.job("solo"))
        h.store.markSeen(h.job("quiet"))
        testScheduler.runCurrent()
        val seen = socket.sentOf("markJobSeen")
        assertEquals(listOf("api-gateway", "storefront", ""), seen.map { it.string("project") })
        assertEquals(listOf(42L, 42L, null), seen.map { it.long("at") })
        assertTrue(h.store.state.value.pending.isEmpty())

        h.store.markAllSeen()
        testScheduler.runCurrent()
        assertEquals(1, socket.sentOf("markAllJobsSeen").size)
    }

    @Test
    fun `history and live output land per project and job`() = runTest {
        val (h, socket) = ready()
        h.store.loadHistory("", "solo")
        assertEquals(setOf(automationKey("", "solo")), h.store.state.value.historyLoading)
        socket.receive(
            """{"t":"jobHistory","project":"","jobId":"solo","ok":true,""" +
                """"entries":[{"at":1,"result":"completed"},{"at":2,"result":"error"}]}"""
        )
        socket.receive(
            """{"t":"jobLiveOutput","project":"","jobId":"solo","ok":true,""" +
                """"live":{"startedAt":5,"text":"building"}}"""
        )
        testScheduler.runCurrent()
        val state = h.store.state.value
        assertTrue(state.historyLoading.isEmpty())
        assertEquals(
            listOf(1L, 2L),
            state.history.getValue(automationKey("", "solo")).map {
                it.at
            }
        )
        assertEquals("building", state.liveOutput.getValue(automationKey("", "solo")).text)

        socket.receive(
            """{"t":"jobLiveOutput","project":"","jobId":"solo","ok":true,"live":null}"""
        )
        testScheduler.runCurrent()
        assertTrue(h.store.state.value.liveOutput.isEmpty())
    }

    @Test
    fun `jobs-changed asks for the list and every open history again`() = runTest {
        val (h, socket) = ready()
        socket.receive(
            """{"t":"jobHistory","project":"","jobId":"solo","ok":true,"entries":[]}"""
        )
        socket.receive(
            """{"t":"jobHistory","project":"storefront","jobId":"nightly","ok":true,"entries":[]}"""
        )
        testScheduler.runCurrent()
        val before = socket.sentOf("jobs").size
        socket.receive("""{"t":"jobs-changed"}""")
        testScheduler.runCurrent()
        assertEquals(before + 1, socket.sentOf("jobs").size)
        assertEquals(
            setOf("" to "solo", "storefront" to "nightly"),
            socket.sentOf("jobHistory").map { it.string("project") to it.string("jobId") }.toSet()
        )
        assertTrue(h.store.state.value.loaded)
    }

    @Test
    fun `a follow-up stays pending until its reply, which can fail`() = runTest {
        val (h, socket) = ready()
        h.store.sendFollowup("storefront", "nightly", 100, "why?", "claude", "", "high")
        testScheduler.runCurrent()
        val sent = socket.sentOf("sendJobFollowup").single()
        assertEquals("why?", sent.string("message"))
        assertEquals(100L, sent.long("at"))
        val key = automationKey("storefront", "nightly")
        assertTrue(key in h.store.state.value.pending)

        socket.receive(
            """{"t":"sendJobFollowup","project":"storefront","jobId":"nightly",""" +
                """"ok":false,"error":"Busy"}"""
        )
        testScheduler.runCurrent()
        assertFalse(key in h.store.state.value.pending)
        assertEquals("Busy", h.store.state.value.followupError[key])
        assertEquals(1, socket.sentOf("jobLiveOutput").size)

        h.store.sendFollowup("storefront", "nightly", 100, "again", "", "", "")
        assertNull(h.store.state.value.followupError[key])
    }

    @Test
    fun `the editor's body is fetched fresh for each edit`() = runTest {
        val (h, socket) = ready()
        socket.receive(
            """{"t":"jobConfig","project":"storefront","jobId":"nightly","ok":true,""" +
                """"job":{"label":"Nightly tests"}}"""
        )
        testScheduler.runCurrent()
        h.store.loadJobConfig("storefront", "nightly", "project")
        val loading = h.store.state.value
        assertNull(loading.jobConfigBody)
        assertEquals(automationKey("storefront", "nightly"), loading.jobConfigLoadingKey)
        assertEquals("project", socket.sentOf("jobConfig").single().string("source"))

        socket.receive(
            """{"t":"jobConfig","project":"storefront","jobId":"nightly","ok":true,""" +
                """"job":{"label":"Nightly"}}"""
        )
        testScheduler.runCurrent()
        assertEquals(JsonPrimitive("Nightly"), h.store.state.value.jobConfigBody?.get("label"))
        assertNull(h.store.state.value.jobConfigLoadingKey)

        h.store.loadJobConfig("storefront", "gone", "project")
        socket.receive(
            """{"t":"jobConfig","project":"storefront","jobId":"gone","ok":false,"error":"Gone"}"""
        )
        testScheduler.runCurrent()
        assertEquals("Gone", h.store.state.value.jobConfigError)
    }

    @Test
    fun `a save lands, bumps the done token and refreshes the list`() = runTest {
        val (h, socket) = ready()
        val body = buildJsonObject { put("label", "Docs") }
        h.store.saveJob("docs", "global", "", body)
        testScheduler.runCurrent()
        assertTrue(h.store.state.value.mutationInFlight)
        assertEquals(body, socket.sentOf("saveJob").single()["job"])
        val jobs = socket.sentOf("jobs").size

        socket.receive("""{"t":"saveJob","ok":true,"id":"docs"}""")
        testScheduler.runCurrent()
        assertFalse(h.store.state.value.mutationInFlight)
        assertEquals(1, h.store.state.value.mutationDoneToken)
        assertEquals(jobs + 1, socket.sentOf("jobs").size)

        // The timeout was cancelled with the reply.
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        assertNull(h.store.state.value.mutationError)
    }

    @Test
    fun `a silent Mac times the write out after 20 seconds`() = runTest {
        val (h, _) = ready()
        h.store.deleteJob("nightly", "project", "storefront", deleteCopies = true)
        testScheduler.advanceTimeBy(19_999)
        testScheduler.runCurrent()
        assertTrue(h.store.state.value.mutationInFlight)
        testScheduler.advanceTimeBy(1)
        testScheduler.runCurrent()
        val state = h.store.state.value
        assertFalse(state.mutationInFlight)
        assertEquals("The Mac didn't respond. Try again.", state.mutationError)
        assertEquals(0, state.mutationDoneToken)
    }

    @Test
    fun `a refused delete reports why`() = runTest {
        val (h, socket) = ready()
        h.store.deleteJob("nightly", "project", "storefront", deleteCopies = false)
        testScheduler.runCurrent()
        assertEquals(
            false,
            socket.sentOf("deleteJob").single()["deleteCopies"]?.let {
                (it as JsonPrimitive).content.toBooleanStrict()
            }
        )
        socket.receive("""{"t":"deleteJob","ok":false,"id":"nightly","error":"Read-only"}""")
        testScheduler.runCurrent()
        assertEquals("Read-only", h.store.state.value.mutationError)
        assertFalse(h.store.state.value.mutationInFlight)
    }

    @Test
    fun `a dropped link clears spinners that can no longer be answered`() = runTest {
        val (h, socket) = ready()
        h.store.run(h.job("nightly"))
        h.store.loadHistory("storefront", "nightly")
        socket.fail()
        testScheduler.runCurrent()
        assertTrue(h.store.state.value.pending.isEmpty())
        assertTrue(h.store.state.value.historyLoading.isEmpty())
    }
}
