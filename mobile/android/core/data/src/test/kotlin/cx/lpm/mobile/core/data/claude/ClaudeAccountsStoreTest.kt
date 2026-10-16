package cx.lpm.mobile.core.data.claude

import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.ClaudeAccountChoice
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClaudeAccountsStoreTest {
    private val accounts =
        """{"t":"claudeAccounts","ok":true,"accounts":[""" +
            """{"id":"default","signedIn":true,"email":"you@example.com","plan":"max"},""" +
            """{"id":"work","label":"Work","signedIn":true,"email":"you@work.example"}],""" +
            """"switching":{"active":true,"pools":[{"members":["default","work"],"current":"default"}]}}"""

    @Test
    fun `the accounts arrive, and a failed read keeps the last ones`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ClaudeAccountsStore>()
        assertNull(store.state.value.accounts)

        socket.receive(accounts)
        testScheduler.runCurrent()
        assertEquals(listOf("default", "work"), store.state.value.accounts?.accounts?.map { it.id })
        assertEquals("default", store.state.value.accounts?.switching?.current?.get(""))

        socket.receive("""{"t":"claudeAccounts","ok":false}""")
        testScheduler.runCurrent()
        assertEquals(2, store.state.value.accounts?.accounts?.size)
    }

    @Test
    fun `loading asks for the readings too when none have arrived`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        h.session.store<ClaudeAccountsStore>().load()
        assertEquals(1, socket.sentOf("claudeAccounts").size)
        assertEquals(1, socket.sentOf("limits").size)
    }

    @Test
    fun `a switch shows at once, and a refusal raises it and rereads`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        socket.receive("""{"t":"projects","projects":[{"name":"storefront","running":true}]}""")
        testScheduler.runCurrent()
        val store = h.session.store<ClaudeAccountsStore>()

        store.setClaudeAccount("storefront", ClaudeAccountChoice.Pin("work"))
        assertEquals(
            ClaudeOwnChoice(account = "work"),
            h.projects.state.value.project("storefront")?.claude
        )
        val sent = socket.sentOf("setClaudeAccount").single()
        assertEquals("\"storefront\"", sent["project"].toString())

        val projectsBefore = socket.sentOf("projects").size
        socket.receive(
            """{"t":"setClaudeAccount","project":"storefront","ok":false,"error":"Not yours"}"""
        )
        testScheduler.runCurrent()
        assertEquals("Not yours", h.session.alerts.alerts.value.actionError)
        assertEquals(projectsBefore + 1, socket.sentOf("projects").size)
        assertEquals(1, socket.sentOf("claudeAccounts").size)
    }
}
