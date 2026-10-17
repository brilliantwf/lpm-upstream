package cx.lpm.mobile.core.tailnet

import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import java.nio.file.Files
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DefaultBuiltInTailscale.signIn] waits for statuses the watcher thread
 * publishes, so these run on real dispatchers and real time.
 */
class SignInTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private fun tailscale(node: FakeNode) = DefaultBuiltInTailscale(
        node = node,
        network = FakeNetwork(),
        prefs = AppPreferences(FakeStore(), scope),
        clock = Clock.systemUTC(),
        device = ThisDevice("Pixel"),
        visibility = FakeVisibility(),
        stateDir = Files.createTempDirectory("tailnet").toFile(),
        commands = Dispatchers.Unconfined,
        scope = scope
    )

    private fun failure(block: suspend () -> Unit): String? = runBlocking {
        runCatching { block() }.exceptionOrNull().let {
            assertTrue("expected a TailnetException, got $it", it is TailnetException)
            it?.message
        }
    }

    @Test
    fun turnsTheNodeOnAndOpensThePageItAsksForAsItStarts() = runBlocking {
        val node = FakeNode().apply {
            onStart = listOf(
                TailnetStatus(state = "starting"),
                TailnetStatus(state = "needsLogin", authUrl = PAGE)
            )
        }
        val tailscale = tailscale(node)
        assertEquals(PAGE, tailscale.signIn())
        assertTrue(tailscale.enabled.value)
        assertFalse("login" in node.calls)
    }

    @Test
    fun asksForANewPageOnlyWhenSignedOut() = runBlocking {
        val node = FakeNode().apply {
            onStart = listOf(TailnetStatus(state = "needsLogin"))
            onLogin = listOf(TailnetStatus(state = "needsLogin", authUrl = PAGE))
        }
        val tailscale = tailscale(node)
        assertEquals(PAGE, tailscale.signIn())
        assertEquals(1, node.calls.count { it == "login" })
    }

    @Test
    fun aSignedInNodeHasNoPageToOpen() = runBlocking {
        val node = FakeNode().apply { onStart = listOf(TailnetStatus(state = "running")) }
        assertNull(tailscale(node).signIn())
        val waiting = FakeNode().apply { onStart = listOf(TailnetStatus(state = "needsApproval")) }
        assertNull(tailscale(waiting).signIn())
    }

    @Test
    fun aNodeThatWontRunFailsWithItsReason() {
        val node = FakeNode().apply {
            onStart =
                listOf(
                    TailnetStatus(state = "stopped", error = "Tailscale turned this device off.")
                )
        }
        assertEquals("Tailscale turned this device off.", failure { tailscale(node).signIn() })
    }

    @Test
    fun aFailedLoginFailsWithTheNodesMessage() {
        val node = FakeNode().apply {
            onStart = listOf(TailnetStatus(state = "needsLogin"))
            loginFailure = "no network"
        }
        assertEquals("no network", failure { tailscale(node).signIn() })
    }

    @Test
    fun withoutTheNativeNodeSignInFailsAsUnavailable() {
        val tailscale = tailscale(FakeNode(loads = false))
        assertEquals(DefaultBuiltInTailscale.UNAVAILABLE, failure { tailscale.signIn() })
        assertTrue(tailscale.enabled.value)
    }

    @Test
    fun signsOutThroughTheNode() = runBlocking {
        val node = FakeNode()
        tailscale(node).signOut()
        assertEquals(listOf("logout"), node.calls)
    }

    private companion object {
        const val PAGE = "https://login.tailscale.com/a/abc123"
    }
}
