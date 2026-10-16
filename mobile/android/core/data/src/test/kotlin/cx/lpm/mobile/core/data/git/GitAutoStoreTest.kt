package cx.lpm.mobile.core.data.git

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.data.testing.settleAlive
import cx.lpm.mobile.core.model.GitAutoKind
import cx.lpm.mobile.core.model.GitAutoRun
import cx.lpm.mobile.core.model.GitAutoStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitAutoStoreTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private class Started(val h: SessionHarness, val socket: FakeSocket, val nonce: String) {
        val auto: GitAutoStore get() = h.session.store()
        val git: GitStore get() = h.session.store()

        fun run(id: Int, phase: String, extra: String = "", nonce: String = this.nonce) {
            val steps = """[{"id":"commit","status":"running"}]"""
            socket.receive(
                """{"t":"gitAuto","ok":true,"project":"storefront","run":{"runId":$id,""" +
                    """"nonce":"$nonce","kind":"commit","phase":"$phase","steps":$steps$extra}}"""
            )
        }
    }

    private fun TestScope.started(kind: GitAutoKind = GitAutoKind.Commit): Started {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        h.session.store<GitAutoStore>().start("storefront", kind)
        val nonce = socket.sentOf("gitAuto").single().getValue("nonce").jsonPrimitive.content
        return Started(h, socket, nonce)
    }

    @Test
    fun `a start shows the planned steps until the Mac's run takes over`() = runTest {
        val s = started(GitAutoKind.Pr)
        val placeholder = s.auto.state.value.runs.getValue("storefront")
        assertEquals(0, placeholder.runId)
        assertEquals(listOf("branch", "commit", "push", "pr"), placeholder.steps.map { it.id })
        assertTrue(placeholder.steps.all { it.status == GitAutoStep.Status.Pending })
        assertEquals(
            "pr",
            s.socket.sentOf("gitAuto").single().getValue("kind").jsonPrimitive.content
        )

        s.auto.start("storefront", GitAutoKind.Commit)
        assertEquals(
            "a run in flight is watched, not restarted",
            1,
            s.socket.sentOf("gitAuto").size
        )

        s.run(3, "running")
        settle()
        assertEquals(3, s.auto.state.value.runs.getValue("storefront").runId)
        settleAlive(s.socket, 40_000)
        assertTrue(
            "a run the Mac answered isn't verified",
            s.socket.sentOf("gitAutoState").isEmpty()
        )
    }

    @Test
    fun `a finished run refreshes the snapshot and the project list`() = runTest {
        val s = started()
        s.run(3, "running")
        s.run(3, "done", extra = ""","message":"feat: x"""")
        settle()
        val run = s.auto.state.value.runs.getValue("storefront")
        assertEquals(GitAutoRun.Phase.Done, run.phase)
        assertEquals("feat: x", run.message)
        assertEquals(1, s.socket.sentOf("git").size)
        assertNull(s.git.state.value.opError["storefront"])
    }

    @Test
    fun `a failure shows on the project screen only while the sheet is closed`() = runTest {
        val s = started()
        s.auto.sheetAppeared("storefront")
        s.run(3, "failed", extra = ""","error":"nothing to commit"""")
        settle()
        assertNull(s.git.state.value.opError["storefront"])

        s.auto.start("storefront", GitAutoKind.Commit)
        s.auto.sheetDisappeared("storefront")
        s.run(
            4,
            "failed",
            extra = ""","error":"hook failed"""",
            nonce = s.auto.state.value.runs.getValue("storefront").nonce
        )
        settle()
        assertEquals(
            GitOpError("Auto Commit failed", "hook failed"),
            s.git.state.value.opError["storefront"]
        )
    }

    @Test
    fun `an unanswered start asks the Mac, then gives up`() = runTest {
        val s = started()
        settleAlive(s.socket, 20_000)
        assertEquals(1, s.socket.sentOf("gitAutoState").size)
        settleAlive(s.socket, 20_000)
        val run = s.auto.state.value.runs.getValue("storefront")
        assertEquals(GitAutoRun.Phase.Failed, run.phase)
        assertEquals(
            "Your Mac didn't respond. Make sure lpm on your Mac is up to date, then try again.",
            run.error
        )
    }

    @Test
    fun `no run on the Mac, or only the previous one, means the start was lost`() = runTest {
        val s = started()
        s.socket.receive("""{"t":"gitAuto","ok":true,"project":"storefront","run":null}""")
        settle()
        assertEquals(
            "Couldn't start on your Mac. Try again.",
            s.auto.state.value.runs["storefront"]?.error
        )

        s.auto.start("storefront", GitAutoKind.Commit)
        s.run(2, "done", nonce = "OLD")
        settle()
        assertEquals(
            "Couldn't start on your Mac. Try again.",
            s.auto.state.value.runs["storefront"]?.error
        )

        s.auto.start("storefront", GitAutoKind.Commit)
        val nonce = s.auto.state.value.runs.getValue("storefront").nonce
        s.run(5, "running", nonce = nonce)
        s.socket.receive("""{"t":"gitAuto","ok":true,"project":"storefront","run":null}""")
        settle()
        assertEquals(
            "Lost track of this run on your Mac. Check the project before trying again.",
            s.auto.state.value.runs["storefront"]?.error
        )
    }

    @Test
    fun `stop asks once and an older frame can't roll a run back`() = runTest {
        val s = started()
        s.run(3, "running")
        settle()
        s.auto.cancel("storefront")
        s.auto.cancel("storefront")
        assertEquals(1, s.socket.sentOf("gitAutoCancel").size)
        assertTrue(s.auto.state.value.runs.getValue("storefront").canceling)

        s.run(3, "canceled")
        s.run(2, "running", nonce = "OLD")
        settle()
        assertEquals(
            GitAutoRun.Phase.Canceled,
            s.auto.state.value.runs.getValue("storefront").phase
        )
    }

    @Test
    fun `switching to the base closes the sheet, a failed pull alerts`() = runTest {
        val s = started()
        s.auto.switchToBase("storefront", "main")
        assertTrue("storefront" in s.auto.state.value.switching)
        s.socket.receive(
            """{"t":"gitAutoSwitchBase","ok":true,"project":"storefront","base":"main",""" +
                """"pullError":"diverged"}"""
        )
        settle()
        assertTrue(s.auto.state.value.switching.isEmpty())
        assertEquals(1, s.auto.state.value.switchedTick["storefront"])
        assertEquals(
            GitOpError("Pull failed", "Switched to main, but the pull failed: diverged"),
            s.git.state.value.opError["storefront"]
        )

        s.auto.switchToBase("storefront", "main")
        s.socket.receive(
            """{"t":"gitAutoSwitchBase","ok":false,"project":"storefront","error":"dirty"}"""
        )
        settle()
        assertEquals("dirty", s.auto.state.value.switchError["storefront"])
        s.auto.dismissSwitchError("storefront")

        s.auto.switchToBase("storefront", "main")
        settleAlive(s.socket, 120_000)
        assertEquals(
            "Switching branch timed out. Try again.",
            s.auto.state.value.switchError["storefront"]
        )
    }

    @Test
    fun `a dropped link re-asks for running runs and drops the switch spinner`() = runTest {
        val s = started()
        s.run(3, "running")
        s.auto.switchToBase("storefront", "main")
        settle()
        s.socket.fail()
        settle()
        assertTrue(s.auto.state.value.switching.isEmpty())

        settle(2_000)
        val next = s.h.transport.last
        next.open()
        settle()
        next.receive("""{"t":"ready","serverId":"srv-1","serverName":"Studio Mac"}""")
        settle()
        assertEquals(1, next.sentOf("gitAutoState").size)
    }
}
