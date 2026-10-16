package cx.lpm.mobile.core.data.git

import cx.lpm.mobile.core.data.testing.FakeSocket
import cx.lpm.mobile.core.data.testing.SessionHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitStoreTest {
    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun FakeSocket.snapshot(files: Int) {
        val list = (1..files).joinToString(",") { """{"path":"f$it.ts","status":"modified"}""" }
        receive(
            """{"t":"git","ok":true,"project":"storefront","isRepo":true,""" +
                """"branch":"main","ghCli":true,"files":[$list]}"""
        )
    }

    @Test
    fun `a load lands as the project's snapshot and its changed count`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.load("storefront")
        git.load("storefront")
        assertEquals(1, socket.sentOf("git").size)
        assertTrue("storefront" in git.state.value.loading)

        socket.snapshot(files = 4)
        settle()
        val state = git.state.value
        assertEquals(4, state.changedCount("storefront"))
        assertEquals("main", state.snapshots.getValue("storefront").branch)
        assertTrue(state.loading.isEmpty())
    }

    @Test
    fun `a pull spins, then announces itself and reloads`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.pull("storefront")
        assertTrue("storefront" in git.state.value.pulling)
        assertEquals(1, socket.sentOf("gitPull").size)

        socket.receive("""{"t":"gitPull","ok":true,"project":"storefront"}""")
        settle()
        assertTrue(git.state.value.pulling.isEmpty())
        assertEquals("Pulled", git.state.value.notice["storefront"]?.text)
        assertEquals(1, socket.sentOf("git").size)
        settle(2_001)
        assertNull(git.state.value.notice["storefront"])
    }

    @Test
    fun `a failed push surfaces what Git said`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val git = h.session.store<GitStore>()
        git.push("storefront")
        socket.receive("""{"t":"gitPush","ok":false,"project":"storefront","error":"rejected"}""")
        settle()
        assertEquals(GitOpError("Push failed", "rejected"), git.state.value.opError["storefront"])
        git.dismissOpError("storefront")
        assertTrue(git.state.value.opError.isEmpty())
    }

    @Test
    fun `a fetch the Mac never answers times out`() = runTest {
        val h = SessionHarness(this)
        h.connectReady()
        val git = h.session.store<GitStore>()
        git.fetch("storefront")
        settle(119_000)
        assertTrue("storefront" in git.state.value.fetching)
        settle(1_001)
        assertTrue(git.state.value.fetching.isEmpty())
        assertEquals("Fetch failed", git.state.value.opError["storefront"]?.title)
    }

    @Test
    fun `a burst of git-changed pushes reloads once`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        repeat(3) { socket.receive("""{"t":"git-changed","project":"storefront"}""") }
        settle(400)
        assertTrue(socket.sentOf("git").isEmpty())
        settle(200)
        assertEquals(1, socket.sentOf("git").size)
    }
}
