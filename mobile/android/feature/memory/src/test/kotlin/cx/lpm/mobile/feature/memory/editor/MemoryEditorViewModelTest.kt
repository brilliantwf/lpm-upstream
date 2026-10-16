package cx.lpm.mobile.feature.memory.editor

import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.core.navigation.MemorySessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.memory.data.memoryKey
import cx.lpm.mobile.feature.memory.demoSession
import cx.lpm.mobile.feature.memory.liveSession
import cx.lpm.mobile.feature.memory.memory
import cx.lpm.mobile.feature.memory.settle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemoryEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val key = MemorySessionKey("storefront", "checkout-redesign", "Checkout redesign")

    private fun TestScope.editor(t: TestSession): MemoryEditorViewModel {
        t.memory.openSession(key.project, key.name)
        t.settle()
        return MemoryEditorViewModel(key, t.sessions).also { keepCollecting(it.uiState) }
    }

    private val TestSession.loaded: String
        get() = memory.state.value.document(key.project, key.name)!!.content

    @Test
    fun `the draft starts as the loaded document and dirties on an edit`() = runTest {
        val t = demoSession()
        val vm = editor(t)
        assertEquals(t.loaded, vm.uiState.value.draft)
        assertFalse(vm.uiState.value.dirty)
        vm.edit(t.loaded + "x")
        assertTrue(vm.uiState.value.dirty)
    }

    @Test
    fun `saving lands and closes the sheet`() = runTest {
        val t = demoSession()
        val vm = editor(t)
        val text = t.loaded + "- Next: ship it\n"
        vm.edit(text)
        vm.save()
        assertTrue(vm.uiState.value.saving)
        t.settle()
        assertTrue(vm.uiState.value.saved)
        assertEquals(text, t.loaded)
    }

    @Test
    fun `a clash reloads the newer text into the draft and says why`() = runTest {
        val t = liveSession()
        val socket = t.connectReady()
        t.memory.openSession(key.project, key.name)
        socket.receive(document("# Mine"))
        val vm = MemoryEditorViewModel(key, t.sessions).also { keepCollecting(it.uiState) }
        t.settle(0)
        vm.edit("# Mine, edited")
        vm.save()
        assertEquals("# Mine", socket.sentOf("memorySave").single().string("baseline"))
        socket.receive(
            """{"t":"memorySave","project":"storefront","name":"checkout-redesign",""" +
                """"ok":false,"error":"modified"}"""
        )
        socket.receive(document("# Theirs"))
        t.settle(0)
        val state = vm.uiState.value
        assertEquals(
            "Changed on your Mac by an agent — reloaded the latest version.",
            state.conflictNotice
        )
        assertEquals("# Theirs", state.draft)
        assertFalse(state.dirty)
        assertFalse(state.saved)
        vm.dismissConflict()
        assertEquals(null, vm.uiState.value.conflictNotice)
    }

    private fun document(content: String) =
        """{"t":"memorySession","project":"storefront","name":"checkout-redesign",""" +
            """"ok":true,"title":"Checkout redesign","content":"$content"}"""

    @Test
    fun `an open editor holds off live refreshes until it closes`() = runTest {
        val t = demoSession()
        val vm = editor(t)
        t.memory.changed("storefront")
        t.settle(500)
        assertFalse(memoryKey(key.project, key.name) in t.memory.state.value.documentLoading)
        vm.edit("draft")
        assertEquals("draft", vm.uiState.value.draft)
    }
}
