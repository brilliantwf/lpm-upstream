package cx.lpm.mobile.feature.git.review

import cx.lpm.mobile.core.data.git.GitStore
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.data.terminals.TerminalHooks
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.navigation.GitReviewKey
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.git.demoSession
import cx.lpm.mobile.feature.git.diff.DiffBlockKind
import cx.lpm.mobile.feature.git.settle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitReviewViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val paths = listOf(
        "components/CheckoutForm.tsx",
        "app/checkout/page.tsx",
        "lib/payments.ts",
        "README.md"
    )

    private fun TestScope.review(t: TestSession): GitReviewViewModel {
        val vm = GitReviewViewModel(
            GitReviewKey("storefront"),
            t.sessions,
            StandardTestDispatcher(testScheduler)
        )
        keepCollecting(vm.uiState)
        vm.appear()
        t.settle()
        return vm
    }

    private val GitReviewUiState.headers get() = rows.filterIsInstance<ReviewRow.FileHeader>()

    private fun GitReviewUiState.blocks(path: String) =
        rows.filterIsInstance<ReviewRow.Block>().filter { it.path == path }

    @Test
    fun `the demo's four changes arrive with every diff parsed`() = runTest {
        val t = demoSession()
        val state = review(t).uiState.value
        assertNull(state.overlay)
        assertEquals(paths, state.files.map { it.path })
        assertEquals(ReviewRow.ChangedHeader(count = 4, reviewed = 0), state.rows.first())
        assertEquals(paths, state.headers.map { it.path })
        assertTrue(state.headers.all { it.selected && !it.viewed && !it.collapsed })
        assertEquals(state.headers.sumOf { it.added ?: 0 }, state.added)
        assertEquals(state.headers.sumOf { it.removed ?: 0 }, state.removed)
        assertEquals(22, state.headers.single { it.path == "lib/payments.ts" }.added)
        assertEquals(DiffBlockKind.Hunk, state.blocks("lib/payments.ts").first().block.kind)
        assertEquals(listOf(ReviewRow.Commit, ReviewRow.CreatePullRequest), state.rows.takeLast(2))
        assertEquals(4, state.selectedCount)
        assertFalse("no message yet", state.canCommit)
    }

    @Test
    fun `marking a file viewed collapses it and counts it reviewed`() = runTest {
        val t = demoSession()
        val vm = review(t)
        vm.toggleViewed("README.md")
        t.settle(0)
        var state = vm.uiState.value
        assertEquals(ReviewRow.ChangedHeader(count = 4, reviewed = 1), state.rows.first())
        val readme = state.headers.single { it.path == "README.md" }
        assertTrue(readme.viewed && readme.collapsed)
        assertTrue(state.blocks("README.md").isEmpty())

        vm.toggleCollapsed("README.md")
        t.settle(0)
        state = vm.uiState.value
        assertFalse(state.headers.single { it.path == "README.md" }.collapsed)
        assertTrue(state.blocks("README.md").isNotEmpty())

        vm.toggleViewed("README.md")
        t.settle(0)
        assertEquals(
            ReviewRow.ChangedHeader(count = 4, reviewed = 0),
            vm.uiState.value.rows.first()
        )
    }

    @Test
    fun `the AI message fills the field and a commit takes only the selected files`() = runTest {
        val t = demoSession()
        val vm = review(t)
        vm.toggleSelected("README.md")
        t.settle(0)
        assertEquals(3, vm.uiState.value.selectedCount)
        assertFalse(vm.uiState.value.headers.single { it.path == "README.md" }.selected)

        vm.generateMessage()
        t.settle(0)
        assertTrue(vm.uiState.value.generating)
        t.settle(2_000)
        val drafted = vm.uiState.value
        assertEquals(
            "feat(checkout): redesign the checkout form and add a payments module",
            drafted.message
        )
        assertFalse(drafted.generating)
        assertTrue(drafted.canCommit)

        vm.commit()
        t.settle(0)
        assertTrue(vm.uiState.value.committing)
        t.settle(2_000)
        val after = vm.uiState.value
        assertEquals(listOf("README.md"), after.files.map { it.path })
        assertEquals("", after.message)
        assertEquals(1, after.selectedCount)
        assertEquals("Committed successfully", after.notice?.text)
    }

    @Test
    fun `a blank message can't commit`() = runTest {
        val t = demoSession()
        val vm = review(t)
        vm.setMessage("  \n ")
        t.settle(0)
        assertFalse(vm.uiState.value.canCommit)
        vm.setMessage("fix: x")
        t.settle(0)
        assertTrue(vm.uiState.value.canCommit)
    }

    @Test
    fun `ask agent attaches the diff without its file header and queues the prompt`() = runTest {
        val t = demoSession()
        val vm = review(t)
        val context = vm.askContext("lib/payments.ts")
        assertEquals("lib/payments.ts", context.path)
        assertTrue(context.diffText.startsWith("@@ -0,0 +1,22 @@"))
        assertFalse(context.diffText.contains("diff --git"))

        val terminals = t.store<TerminalsStore>()
        terminals.loadTerminals("storefront")
        t.settle()
        val terminal = vm.uiState.value.terminals.first()
        vm.sendToAgent(terminal, "Explain this")
        val submitted = mutableListOf<String>()
        terminals.openTerminalScreen(
            terminal.id,
            this,
            TerminalHooks(
                seed = { _, _, _, _ -> },
                output = {},
                submit = { submitted += it },
                capture = { _, done -> done("") }
            )
        )
        t.settle()
        assertEquals("nothing goes in before the screen shows", emptyList<String>(), submitted)
        terminals.contentShown(terminal.id)
        t.settle()
        assertEquals(listOf("Explain this"), submitted)
    }

    @Test
    fun `the screen shows a skeleton, then why there's nothing to review`() = runTest {
        val t = TestSession(this, setOf(SessionStore.Factory(::GitStore)))
        val socket = t.connectReady()
        val vm = review(t)
        assertEquals(ReviewOverlay.Skeleton, vm.uiState.value.overlay)
        assertEquals(1, socket.sentOf("gitWatch").size)

        socket.receive(
            """{"t":"git","ok":false,"project":"storefront","error":"No such project"}"""
        )
        t.settle(0)
        assertEquals(ReviewOverlay.Unreachable, vm.uiState.value.overlay)
        assertEquals("No such project", vm.uiState.value.loadError)

        vm.load()
        socket.receive("""{"t":"git","ok":true,"project":"storefront","isRepo":false}""")
        t.settle(0)
        assertEquals(ReviewOverlay.NotRepo, vm.uiState.value.overlay)
        assertTrue(vm.uiState.value.rows.isEmpty())

        vm.disappear()
        assertEquals(1, socket.sentOf("gitUnwatch").size)
    }

    @Test
    fun `coming back to the screen fetches what changed while it was away`() = runTest {
        val t = TestSession(this, setOf(SessionStore.Factory(::GitStore)))
        val socket = t.connectReady()
        val vm = review(t)
        socket.receive(
            """{"t":"git","ok":true,"project":"storefront","isRepo":true,"branch":"main",""" +
                """"files":[{"path":"a.ts","status":"conflicted"}]}"""
        )
        t.settle(0)
        assertEquals(listOf("a.ts"), vm.uiState.value.conflicted)
        vm.disappear()

        vm.appear()
        assertEquals(2, socket.sentOf("git").size)
        assertEquals(2, socket.sentOf("gitWatch").size)
    }
}
