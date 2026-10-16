package cx.lpm.mobile.core.data.config

import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The raw YAML editor's read and write of one config layer. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectConfigYamlTest {
    @Test
    fun `a layer's text seeds the editor, a new read clears the old one first`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.readConfig("storefront", "project")
        val sent = socket.sentOf("readConfig").single()
        assertEquals(setOf("t", "project", "layer"), sent.keys)
        assertEquals("project", sent.string("layer"))
        socket.receive(
            """{"t":"readConfig","ok":true,"project":"storefront","layer":"project",""" +
                """"content":"# kept\nservices: {}\n","available":true}"""
        )
        testScheduler.runCurrent()
        assertEquals("# kept\nservices: {}\n", store.state.value.configText)
        assertFalse(store.state.value.configTextLoading)

        store.readConfig("remote-box", "repo")
        assertNull(store.state.value.configText)
        assertTrue(store.state.value.configTextLoading)
        socket.receive(
            """{"t":"readConfig","ok":true,"project":"remote-box","layer":"repo",""" +
                """"content":"","available":false}"""
        )
        testScheduler.runCurrent()
        assertEquals("", store.state.value.configText)
        assertFalse(store.state.value.configAvailable)

        store.readConfig("storefront", "global")
        assertTrue(store.state.value.configAvailable)
        testScheduler.advanceTimeBy(20_001)
        testScheduler.runCurrent()
        assertFalse(store.state.value.configTextLoading)
        assertEquals("The Mac didn't respond. Try again.", store.state.value.configTextError)
    }

    @Test
    fun `a save writes the exact text and adopts the name it landed under`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        val text = "name: shop\n# a comment\nservices:\n  web: npm run dev\n"
        store.saveConfig("storefront", "project", text)
        val sent = socket.sentOf("saveConfig").single()
        assertEquals(setOf("t", "project", "layer", "content"), sent.keys)
        assertEquals(text, sent.string("content"))
        assertTrue(store.state.value.mutationInFlight)
        socket.receive(
            """{"t":"saveConfig","ok":true,"project":"storefront","layer":"project","name":"shop"}"""
        )
        testScheduler.runCurrent()
        assertEquals("shop", store.state.value.savedName)
        assertEquals(1, store.state.value.mutationDoneToken)

        store.saveConfig("shop", "project", "services: [")
        assertNull(store.state.value.savedName)
        socket.receive(
            """{"t":"saveConfig","ok":false,"project":"shop","layer":"project","name":"shop",""" +
                """"error":"invalid YAML: did not find expected node content"}"""
        )
        testScheduler.runCurrent()
        assertNull(store.state.value.savedName)
        assertEquals(
            "invalid YAML: did not find expected node content",
            store.state.value.mutationError
        )
        assertEquals(1, store.state.value.mutationDoneToken)
    }
}
