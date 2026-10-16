package cx.lpm.mobile.feature.claudeaccount

import cx.lpm.mobile.core.data.claude.ClaudeAccountsStore
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.data.usage.UsageStore
import cx.lpm.mobile.core.model.ClaudeAccountChoice
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClaudeAccountMenuViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.demo(): TestSession = TestSession(
        this,
        setOf(SessionStore.Factory(::ClaudeAccountsStore), SessionStore.Factory(::UsageStore))
    ).apply {
        enterDemo()
        store<ClaudeAccountsStore>().load()
        settle()
    }

    private fun TestSession.settle(millis: Long = 1_000) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private fun TestScope.menu(t: TestSession, project: String = "storefront") =
        ClaudeAccountMenuViewModel(project, SessionKey.Live, t.sessions, t.clock).also { vm ->
            backgroundScope.launch { vm.uiState.collect() }
            t.settle()
        }

    @Test
    fun `the menu shows once the Mac has accounts beyond the main login`() = runTest {
        val t = demo()
        val state = menu(t).uiState.value
        assertTrue(state.shown)
        assertEquals(
            listOf("Main login", "Work", "Personal", "Client", "Old laptop"),
            state.accounts?.accounts?.map { it.label }
        )
        assertTrue(state.project?.claude?.usesMainAccounts(isCopy = false) == true)
    }

    @Test
    fun `the menu starts from what the stores already hold`() = runTest {
        val t = demo()
        val vm = ClaudeAccountMenuViewModel("storefront", SessionKey.Live, t.sessions, t.clock)
        assertTrue(vm.uiState.value.shown)
    }

    @Test
    fun `pinning an account writes it, and picking the current choice sends nothing`() = runTest {
        val t = demo()
        val vm = menu(t)
        assertFalse(vm.pick(ClaudeAccountChoice.Main))

        assertTrue(vm.pick(ClaudeAccountChoice.Pin("work")))
        assertEquals(ClaudeOwnChoice(account = "work"), vm.uiState.value.project?.claude)
        t.settle()
        assertEquals(ClaudeOwnChoice(account = "work"), vm.uiState.value.project?.claude)
    }

    @Test
    fun `a confirmed account joins the list at once`() = runTest {
        val t = demo()
        val vm = menu(t)
        assertTrue(vm.setList(listOf("work")))
        t.settle()
        assertNull(vm.uiState.value.consent)
        assertEquals(listOf("work"), vm.uiState.value.project?.claude?.list)
    }

    @Test
    fun `an unconfirmed account waits for consent, then joins confirmed`() = runTest {
        val t = demo()
        val vm = menu(t)
        assertFalse(vm.setList(listOf("personal")))
        val request = vm.uiState.value.consent
        assertEquals(listOf("personal"), request?.ids)
        assertEquals(listOf("Personal"), request?.accounts?.map { it.label })
        assertTrue(vm.uiState.value.project?.claude?.list?.isEmpty() == true)

        vm.confirmConsent()
        t.settle()
        val state = vm.uiState.value
        assertNull(state.consent)
        assertEquals(listOf("personal"), state.project?.claude?.list)
        assertTrue(state.accounts?.account("personal")?.confirmed == true)
        assertEquals("personal", state.accounts?.switching?.current?.get("storefront"))
    }

    @Test
    fun `cancelling consent leaves the list as it was`() = runTest {
        val t = demo()
        val vm = menu(t, project = "mobile-app")
        vm.setList(listOf("work", "default", "personal"))
        vm.dismissConsent()
        t.settle()
        assertNull(vm.uiState.value.consent)
        assertEquals(listOf("work", "default"), vm.uiState.value.project?.claude?.list)
    }

    @Test
    fun `rows carry each account's current usage`() = runTest {
        val t = demo()
        val usage = menu(t).uiState.value.usage
        assertTrue(usage["work"].orEmpty().startsWith("5h "))
        assertNull(usage["laptop"])
    }
}
