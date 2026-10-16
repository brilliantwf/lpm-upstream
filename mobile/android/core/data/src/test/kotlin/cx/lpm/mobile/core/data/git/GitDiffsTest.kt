package cx.lpm.mobile.core.data.git

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.data.testing.settleAlive
import cx.lpm.mobile.core.model.GitDiffResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitDiffsTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun FakeSocket.snapshot(vararg files: Pair<String, String>) {
        val list = files.joinToString(",") { (path, stamp) ->
            """{"path":"$path","status":"modified","stamp":"$stamp"}"""
        }
        receive("""{"t":"git","ok":true,"project":"storefront","isRepo":true,"files":[$list]}""")
    }

    private fun FakeSocket.diffs(vararg entries: String) = receive(
        """{"t":"gitDiffs","ok":true,"project":"storefront","files":[${entries.joinToString(
            ","
        )}]}"""
    )

    private fun FakeSocket.requestedPaths(): List<List<String>> = sentOf("gitDiffs").map { frame ->
        frame.getValue("paths").jsonArray.map { it.jsonPrimitive.content }
    }

    private val GitState.storefront: Map<String, GitDiffResult>
        get() = diffs.filterKeys { it.project == "storefront" }.mapKeys { it.key.path }

    @Test
    fun `a burst of lazy loads goes out as one gitDiffs batch`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.loadDiff("storefront", "a.ts")
        git.loadDiff("storefront", "b.ts")
        git.loadDiff("storefront", "a.ts")
        settle(49)
        assertTrue(socket.sentOf("gitDiffs").isEmpty())
        settle(1)
        assertEquals(listOf(listOf("a.ts", "b.ts")), socket.requestedPaths())

        socket.diffs(
            """{"path":"a.ts","diff":"@@ -1 +1 @@\n-a\n+b\n","binary":false,"truncated":false}""",
            """{"path":"b.ts","error":"too big"}"""
        )
        settle()
        val state = git.state.value
        assertEquals("@@ -1 +1 @@\n-a\n+b\n", state.storefront.getValue("a.ts").diff)
        assertEquals("too big", state.diffError[GitDiffKey("storefront", "b.ts")])
        assertTrue(state.diffLoading.isEmpty())
    }

    @Test
    fun `a single gitDiff reply and a failed batch`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.loadDiff("storefront", "a.ts")
        settle(50)
        socket.receive(
            """{"t":"gitDiffs","ok":false,"project":"storefront","error":"No project"}"""
        )
        settle()
        assertTrue(GitDiffKey("storefront", "a.ts") in git.state.value.diffLoading)
        socket.receive(
            """{"t":"gitDiff","ok":true,"project":"storefront","path":"a.ts","diff":"x","binary":true}"""
        )
        settle()
        assertEquals(true, git.state.value.storefront.getValue("a.ts").binary)
        assertTrue(git.state.value.diffLoading.isEmpty())
    }

    @Test
    fun `a diff the Mac never sends times out with a retry message`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.loadDiff("storefront", "a.ts")
        settleAlive(socket, 29_999)
        assertNull(git.state.value.diffError[GitDiffKey("storefront", "a.ts")])
        settle(1)
        assertEquals(
            "Couldn't load the diff. Try again.",
            git.state.value.diffError[GitDiffKey("storefront", "a.ts")]
        )
    }

    @Test
    fun `an open review screen fetches every file's diff with the snapshot`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.load("storefront")
        socket.snapshot("a.ts" to "1", "b.ts" to "1")
        settle(100)
        assertTrue("an unwatched project prefetches nothing", socket.sentOf("gitDiffs").isEmpty())

        git.watch("storefront")
        assertEquals(1, socket.sentOf("gitWatch").size)
        git.load("storefront")
        socket.snapshot("a.ts" to "1", "b.ts" to "1")
        settle(100)
        assertEquals(listOf(listOf("a.ts", "b.ts")), socket.requestedPaths())
    }

    @Test
    fun `a git-changed push re-fetches only changed diffs and drops gone ones`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.watch("storefront")
        git.load("storefront")
        socket.snapshot("a.ts" to "1", "b.ts" to "1", "c.ts" to "1")
        settle(100)
        socket.diffs(
            """{"path":"a.ts","diff":"a1"}""",
            """{"path":"b.ts","diff":"b1"}""",
            """{"path":"c.ts","diff":"c1"}"""
        )
        settle()
        assertEquals(3, git.state.value.storefront.size)

        socket.receive("""{"t":"git-changed","project":"storefront"}""")
        settle(500)
        assertEquals(2, socket.sentOf("git").size)
        socket.snapshot("a.ts" to "1", "b.ts" to "2")
        settle(100)
        assertEquals(listOf("b.ts"), socket.requestedPaths().last())
        assertEquals(setOf("a.ts", "b.ts"), git.state.value.storefront.keys)

        git.unwatch("storefront")
        assertEquals(1, socket.sentOf("gitUnwatch").size)
    }

    @Test
    fun `a reconnect watches the open review screen's project again`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        h.session.store<GitStore>().watch("storefront")
        socket.receive("""{"t":"ready","serverId":"srv-1","serverName":"Studio Mac"}""")
        settle()
        assertEquals(2, socket.sentOf("gitWatch").size)

        h.session.store<GitStore>().unwatch("storefront")
        socket.receive("""{"t":"ready","serverId":"srv-1","serverName":"Studio Mac"}""")
        settle()
        assertEquals(2, socket.sentOf("gitWatch").size)
    }

    @Test
    fun `viewed marks survive a refresh and a reset clears everything`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.toggleViewed("storefront", "a.ts")
        assertTrue(git.state.value.isViewed("storefront", "a.ts"))
        git.load("storefront")
        socket.snapshot("a.ts" to "1")
        settle()
        assertTrue(git.state.value.isViewed("storefront", "a.ts"))
        git.toggleViewed("storefront", "a.ts")
        assertTrue(!git.state.value.isViewed("storefront", "a.ts"))
        git.toggleViewed("storefront", "a.ts")
        git.reset()
        assertEquals(GitState(), git.state.value)
    }
}
