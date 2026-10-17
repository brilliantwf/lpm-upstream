package cx.lpm.mobile.feature.terminal

import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.navigation.TerminalKey
import cx.lpm.mobile.core.testing.FakeSocket
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.terminal.composer.ComposerSessions
import cx.lpm.mobile.feature.terminal.composer.FakeMedia
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabRestoreViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun FakeSocket.terminals(vararg ids: String) {
        val list = ids.joinToString(",") {
            """{"id":"$it","label":"Claude","project":"storefront"}"""
        }
        receive("""{"t":"terminals","project":"storefront","terminals":[$list]}""")
    }

    private fun TestScope.setup(): Triple<TestSession, FakeSocket, TerminalViewModel> {
        val media = FakeMedia(Files.createTempDirectory("composer").toFile())
        val io = StandardTestDispatcher(testScheduler)
        val t = TestSession(
            this,
            stores = setOf(
                SessionStore.Factory {
                    ComposerSessions(it, media, io, "phone") { testScheduler.currentTime }
                }
            )
        )
        val socket = t.connectReady()
        socket.receive("""{"t":"projects","projects":[{"name":"storefront","running":true}]}""")
        socket.terminals("old")
        testScheduler.runCurrent()
        val vm = TerminalViewModel(TerminalKey("storefront", "old"), t.sessions, t.prefs, t.clock)
        backgroundScope.launch { vm.uiState.collect {} }
        testScheduler.runCurrent()
        return Triple(t, socket, vm)
    }

    @Test
    fun `the screen carries on with the tab lpm reopened, with what was typed`() = runTest {
        val (t, socket, vm) = setup()
        t.store<ComposerSessions>().store("old", "storefront", "Claude").setText("fix the build")
        socket.receive("""{"t":"exit","id":"old","code":-1,"gone":true}""")
        socket.terminals()
        testScheduler.runCurrent()
        assertEquals("old", vm.uiState.value.terminal?.id)
        assertEquals("This terminal is no longer running", vm.uiState.value.ended?.message)
        assertFalse(vm.uiState.value.showsComposer)

        socket.terminals("new")
        testScheduler.runCurrent()
        val state = vm.uiState.value
        assertEquals("new", state.terminal?.id)
        assertNull(state.ended)
        assertTrue(state.reopened)
        assertTrue(state.showsComposer)
        assertEquals("new", socket.sentOf("claim").last()["id"]?.toString()?.trim('"'))
        assertEquals(
            "fix the build",
            t.store<ComposerSessions>().store("new", "storefront", "Claude").current.text
        )

        testScheduler.advanceTimeBy(4_001)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.reopened)
    }

    @Test
    fun `a gone tab keeps asking for the list until lpm brings it back`() = runTest {
        val (_, socket, _) = setup()
        val before = socket.sentOf("terminals").size
        socket.receive("""{"t":"exit","id":"old","code":-1,"gone":true}""")
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(8_001)
        testScheduler.runCurrent()
        assertEquals(before + 3, socket.sentOf("terminals").size)
    }

    @Test
    fun `a process that exited on its own is no restart`() = runTest {
        val (_, socket, vm) = setup()
        socket.receive("""{"t":"exit","id":"old","code":0}""")
        socket.terminals("new")
        testScheduler.runCurrent()
        assertEquals("old", vm.uiState.value.terminal?.id)
        assertEquals("Process exited", vm.uiState.value.ended?.message)
        assertFalse(vm.uiState.value.reopened)
    }
}
