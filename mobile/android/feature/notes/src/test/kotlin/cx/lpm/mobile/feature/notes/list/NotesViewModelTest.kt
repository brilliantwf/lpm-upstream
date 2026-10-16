package cx.lpm.mobile.feature.notes.list

import cx.lpm.mobile.core.navigation.NotesKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.notes.demoSession
import cx.lpm.mobile.feature.notes.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.notebook(t: TestSession, project: String = "storefront"): NotesViewModel {
        val vm = NotesViewModel(NotesKey(project), t.sessions, t.clock)
        keepCollecting(vm.uiState)
        vm.open()
        return vm
    }

    @Test
    fun `the chats load under a spinner, newest activity first`() = runTest {
        val t = demoSession()
        val vm = notebook(t)
        assertEquals(NotesOverlay.Loading, vm.uiState.value.overlay)
        t.settle()
        val state = vm.uiState.value
        assertEquals(NotesOverlay.None, state.overlay)
        assertEquals(listOf("Checkout redesign", "Ideas", "General"), state.chats.map { it.title })
        // The demo stamps by the wall clock, the screen reads the test's: only the shape holds.
        assertTrue(state.chats.all { it.time.isNotEmpty() })
        assertFalse(state.searching)
    }

    @Test
    fun `searching lists hits, then says when nothing matches`() = runTest {
        val t = demoSession()
        val vm = notebook(t)
        t.settle()
        vm.setSearch("payment mock")
        vm.runSearch()
        assertEquals(NotesOverlay.Loading, vm.uiState.value.overlay)
        t.settle()
        var state = vm.uiState.value
        assertTrue(state.searching)
        assertEquals("Checkout redesign", state.hits.single().hit.chatTitle)
        assertEquals(NotesOverlay.None, state.overlay)

        vm.setSearch("zebra")
        vm.runSearch()
        t.settle()
        state = vm.uiState.value
        assertTrue(state.hits.isEmpty())
        assertEquals(NotesOverlay.NoResults("zebra"), state.overlay)

        vm.setSearch("")
        vm.runSearch()
        state = vm.uiState.value
        assertFalse(state.searching)
        assertEquals(3, state.chats.size)
    }

    @Test
    fun `a new chat is announced once to open`() = runTest {
        val t = demoSession()
        val vm = notebook(t)
        t.settle()
        vm.create("Launch plan")
        assertTrue(vm.uiState.value.creating)
        t.settle()
        val created = vm.uiState.value.created
        assertEquals("Launch plan", created?.title)
        assertFalse(vm.uiState.value.creating)
        vm.consumeCreated()
        assertNull(vm.uiState.value.created)
    }

    @Test
    fun `a project with no notebook gets an empty General chat`() = runTest {
        val t = demoSession()
        val vm = notebook(t, project = "blog")
        t.settle()
        assertEquals(listOf("General"), vm.uiState.value.chats.map { it.title })
    }

    @Test
    fun `a failed rename shows in the banner until dismissed`() = runTest {
        val t = demoSession()
        val vm = notebook(t)
        t.settle()
        val chat = vm.uiState.value.chats.first().chat
        vm.delete(chat)
        t.settle()
        vm.rename(chat, "Gone")
        t.settle()
        assertEquals("That chat is no longer on your Mac.", vm.uiState.value.chatError)
        vm.dismissError()
        assertNull(vm.uiState.value.chatError)
    }
}
