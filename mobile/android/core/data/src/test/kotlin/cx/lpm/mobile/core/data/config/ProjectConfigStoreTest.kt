package cx.lpm.mobile.core.data.config

import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.string
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectConfigStoreTest {
    @Test
    fun `a service body seeds the form, and a silent Mac times out`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.loadServiceBody("storefront", "web")
        assertTrue(store.state.value.serviceBodyLoading)
        socket.receive(
            """{"t":"serviceBody","ok":true,"project":"storefront","key":"web",""" +
                """"body":{"cmd":"npm run dev"},"source":"project"}"""
        )
        testScheduler.runCurrent()
        assertEquals("npm run dev", store.state.value.serviceBody?.string("cmd"))
        assertFalse(store.state.value.serviceBodyLoading)

        store.loadServiceBody("storefront", "api")
        testScheduler.advanceTimeBy(20_001)
        testScheduler.runCurrent()
        assertEquals("The Mac didn't respond. Try again.", store.state.value.serviceBodyError)
    }

    @Test
    fun `a saved service bumps the done token and refreshes the list`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.saveService(
            "storefront",
            "web",
            buildJsonObject {
                put("cmd", "x")
            },
            previousKey = null
        )
        assertTrue(store.state.value.mutationInFlight)
        val projects = socket.sentOf("projects").size
        socket.receive("""{"t":"saveService","ok":true,"project":"storefront","key":"web"}""")
        testScheduler.runCurrent()
        val state = store.state.value
        assertFalse(state.mutationInFlight)
        assertEquals(1, state.mutationDoneToken)
        assertEquals(projects + 1, socket.sentOf("projects").size)

        store.deleteService("storefront", "web")
        socket.receive(
            """{"t":"deleteService","ok":false,"project":"storefront","key":"web","error":"in use"}"""
        )
        testScheduler.runCurrent()
        assertEquals("in use", store.state.value.mutationError)
        assertEquals(1, store.state.value.mutationDoneToken)
    }

    @Test
    fun `an action body seeds the form with its section and layer, and writes settle`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.loadActionBody("storefront", "deploy:prod")
        assertTrue(store.state.value.actionBodyLoading)
        assertEquals("deploy:prod", socket.sentOf("actionBody").single().string("key"))
        socket.receive(
            """{"t":"actionBody","ok":true,"project":"storefront","key":"deploy:prod",""" +
                """"body":{"cmd":"make deploy"},"section":"terminals","source":"repo"}"""
        )
        testScheduler.runCurrent()
        val state = store.state.value
        assertFalse(state.actionBodyLoading)
        assertEquals("make deploy", state.actionBody?.string("cmd"))
        assertEquals("terminals", state.actionBodySection)
        assertEquals("repo", state.actionBodySource)

        store.saveAction(
            "storefront",
            "ship",
            buildJsonObject { put("cmd", "make ship") },
            previousKey = "deploy",
            section = "terminals"
        )
        val sent = socket.sentOf("saveAction").single()
        assertEquals("deploy", sent.string("previousKey"))
        assertEquals("terminals", sent.string("section"))
        socket.receive("""{"t":"saveAction","ok":true,"project":"storefront","key":"ship"}""")
        testScheduler.runCurrent()
        assertEquals(1, store.state.value.mutationDoneToken)

        store.deleteAction("storefront", "ship")
        socket.receive("""{"t":"deleteAction","ok":true,"project":"storefront","key":"ship"}""")
        testScheduler.runCurrent()
        assertEquals(2, store.state.value.mutationDoneToken)

        store.loadActionBody("storefront", "gone")
        testScheduler.advanceTimeBy(20_001)
        testScheduler.runCurrent()
        assertEquals("The Mac didn't respond. Try again.", store.state.value.actionBodyError)
    }
}
