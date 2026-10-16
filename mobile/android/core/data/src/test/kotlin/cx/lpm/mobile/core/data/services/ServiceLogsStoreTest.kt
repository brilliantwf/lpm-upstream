package cx.lpm.mobile.core.data.services

import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.int
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceLogsStoreTest {
    @Test
    fun `a pane's output lands under its key until it's consumed`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ServiceLogsStore>()
        store.fetch("storefront", paneIndex = 1)
        assertEquals(200, socket.sentOf("serviceLogs").single().int("lines"))

        socket.receive(
            """{"t":"serviceLogs","ok":true,"project":"storefront",""" +
                """"paneIndex":1,"text":"ready on :3000"}"""
        )
        testScheduler.runCurrent()
        val key = store.key("storefront", 1)
        assertEquals("ready on :3000", store.state.value.results[key])

        socket.receive(
            """{"t":"serviceLogs","ok":false,"project":"storefront",""" +
                """"paneIndex":2,"error":"gone"}"""
        )
        testScheduler.runCurrent()
        assertEquals("gone", store.state.value.errors[store.key("storefront", 2)])

        store.consume(key)
        assertTrue(key !in store.state.value.results)
    }
}
