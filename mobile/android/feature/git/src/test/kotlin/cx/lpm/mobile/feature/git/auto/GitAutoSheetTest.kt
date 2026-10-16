package cx.lpm.mobile.feature.git.auto

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitAutoKind
import cx.lpm.mobile.core.model.GitAutoRun
import cx.lpm.mobile.core.model.GitAutoStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitAutoSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: GitAutoUiState) = compose.setContent {
        LpmTheme {
            GitAutoSheet(
                state = state,
                onDismiss = { events += "dismiss" },
                actions = GitAutoActions(
                    stop = { events += "stop" },
                    retry = { events += "retry" },
                    switchToBase = { events += "switch" }
                )
            )
        }
    }

    private fun run(kind: GitAutoKind, phase: GitAutoRun.Phase) =
        GitAutoRun.placeholder(kind, "N").copy(phase = phase)

    @Test
    fun aRunningRunCanBeClosedOrStopped() {
        val running = run(GitAutoKind.Commit, GitAutoRun.Phase.Running).copy(
            steps = listOf(GitAutoStep("commit", GitAutoStep.Status.Running, "Committing…"))
        )
        show(GitAutoUiState(run = running))
        compose.onNodeWithText("Auto Commit").assertExists()
        compose.onNodeWithText("Commit changes").assertExists()
        compose.onNodeWithText("Committing…").assertExists()
        compose.onNodeWithText("AI writes the message, then commits").assertExists()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf("stop", "dismiss"), events)
    }

    @Test
    fun stoppingWaitsForTheMac() {
        show(
            GitAutoUiState(
                run = run(GitAutoKind.Commit, GitAutoRun.Phase.Running).copy(canceling = true)
            )
        )
        compose.onNodeWithText("Stopping…").assertIsNotEnabled()
    }

    @Test
    fun aCommitShowsItsMessage() {
        val done = run(
            GitAutoKind.CommitPush,
            GitAutoRun.Phase.Done
        ).copy(message = "feat: x", rebased = true)
        show(GitAutoUiState(run = done))
        compose.onNodeWithText(
            "Committed, rebased onto new remote commits, and pushed"
        ).assertExists()
        compose.onNodeWithText("feat: x").assertExists()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun aPullRequestOffersOpenAndSwitchingToItsBase() {
        val done = run(GitAutoKind.Pr, GitAutoRun.Phase.Done).copy(
            url = "https://github.com/demo/storefront/pull/42",
            branch = "feat/x",
            base = "main"
        )
        show(GitAutoUiState(run = done))
        compose.onNodeWithText("Pull request created").assertExists()
        compose.onNodeWithText("feat/x → main").assertExists()
        compose.onNodeWithText("Open on GitHub").assertExists()
        compose.onNodeWithText("Switch to main and pull").performClick()
        assertEquals(listOf("switch"), events)
    }

    @Test
    fun switchingHoldsTheSheet() {
        val done = run(GitAutoKind.Pr, GitAutoRun.Phase.Done).copy(base = "main")
        show(GitAutoUiState(run = done, switching = true))
        compose.onNodeWithText("Switching to main…").assertExists()
        compose.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun aFailureSaysWhyAndCanRetry() {
        val failed = run(
            GitAutoKind.Commit,
            GitAutoRun.Phase.Failed
        ).copy(error = "No changes to commit.")
        show(GitAutoUiState(run = failed))
        compose.onNodeWithText("No changes to commit.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun aCanceledRunSaysSo() {
        show(GitAutoUiState(run = run(GitAutoKind.Commit, GitAutoRun.Phase.Canceled)))
        compose.onNodeWithText("Canceled.").assertExists()
        compose.onNodeWithText("Retry").assertExists()
    }
}
