package cx.lpm.mobile.feature.git.review

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.feature.git.diff.ParsedDiff
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val app = GitFile("src/app.ts", "modified", staged = false, stamp = "1")
    private val logo = GitFile("assets/logo.png", "added", staged = false, stamp = "1")
    private val parsed = ParsedDiff.parse(
        "@@ -1,2 +1,2 @@\n-const a = 1\n+const a = 2\n keep()\n",
        "ts"
    )

    private fun show(state: GitReviewUiState) = compose.setContent {
        LpmTheme {
            GitReviewScreen(
                state = state,
                refreshing = false,
                actions = ReviewActions(
                    back = { events += "back" },
                    retry = { events += "retry" },
                    retryDiff = { events += "retry $it" },
                    toggleSelected = { events += "select $it" },
                    toggleCollapsed = { events += "collapse $it" },
                    toggleViewed = { events += "viewed $it" },
                    ask = { events += "ask $it" },
                    generateMessage = { events += "generate" },
                    commit = { events += "commit" },
                    createPullRequest = { events += "pr" }
                )
            )
        }
    }

    private fun loaded(message: String = "", canCommit: Boolean = false) = GitReviewUiState(
        project = "storefront",
        overlay = null,
        files = listOf(app, logo),
        rows = reviewRows(
            listOf(
                ReviewFile(app, null, null, parsed, true, false, false, false),
                ReviewFile(logo, null, "too big", null, false, true, false, false)
            ),
            ghCli = true
        ),
        added = 1,
        removed = 1,
        message = message,
        selectedCount = 1,
        canCommit = canCommit
    )

    @Test
    fun theBarCountsChangesAndJumpsToFiles() {
        show(loaded())
        compose.onNodeWithText("Changes").assertExists()
        // In the title, and on the file's bar.
        compose.onAllNodesWithText("+1", substring = true).assertCountEquals(2)
        compose.onAllNodesWithText("−1", substring = true).assertCountEquals(2)
        compose.onNodeWithText("CHANGED FILES (2)").assertExists()
        compose.onNodeWithContentDescription("Jump to file").performClick()
        compose.onNodeWithText("app.ts").assertExists()
        compose.onNodeWithText("logo.png").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun eachFileBarSelectsCollapsesMarksViewedAndAsks() {
        show(loaded())
        val select = compose.onAllNodesWithContentDescription("Include in commit")
        select[0].assertIsOn()
        select[0].assert(hasStateDescription("On"))
        select[1].assertIsOff()
        select[1].assert(hasStateDescription("Off"))
        compose.onNode(hasText("src/app.ts") and hasClickAction())
            .assert(hasStateDescription("Expanded"))
        compose.onAllNodesWithContentDescription("Viewed")[1].assertIsOn()
        select[1].performClick()
        compose.onAllNodesWithContentDescription("Viewed")[0].performClick()
        compose.onNode(hasText("src/app.ts") and hasClickAction()).performClick()
        compose.onAllNodesWithContentDescription("More")[0].performClick()
        compose.onNodeWithText("Ask agent…").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(
                "select assets/logo.png",
                "viewed src/app.ts",
                "collapse src/app.ts",
                "ask src/app.ts"
            ),
            events
        )
    }

    @Test
    fun aConflictedFileIsFlaggedAndNamedAboveTheCommitForm() {
        val conflicted = GitFile("src/merge.ts", "conflicted", staged = false, stamp = "1")
        show(
            loaded().copy(
                files = listOf(conflicted),
                conflicted = listOf("src/merge.ts"),
                rows = reviewRows(
                    listOf(ReviewFile(conflicted, null, null, null, true, false, true, false)),
                    ghCli = false
                )
            )
        )
        compose.onNodeWithContentDescription("Merge conflict").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Commit message"))
        compose.onNodeWithText(
            "src/merge.ts has merge conflicts. Resolve the conflict markers " +
                "(<<<<<<< … >>>>>>>) before committing."
        ).assertExists()
    }

    @Test
    fun aFailedDiffOffersRetry() {
        show(loaded())
        compose.onNodeWithText("too big").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry assets/logo.png"), events)
    }

    @Test
    fun theCommitFormNeedsAMessage() {
        show(loaded())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Create Pull Request"))
        compose.onNodeWithText("Commit message").assertExists()
        compose.onNodeWithText("Commit 1 file").assertIsNotEnabled()
        compose.onNodeWithText("Only the selected files are committed.").assertExists()
        compose.onNodeWithText("Generate message").performClick()
        compose.onNodeWithText("Create Pull Request").performClick()
        assertEquals(listOf("generate", "pr"), events)
    }

    @Test
    fun aMessageEnablesCommit() {
        show(loaded(message = "fix: x", canCommit = true))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Commit 1 file"))
        compose.onNodeWithText("fix: x").assertExists()
        compose.onNodeWithText("Commit 1 file").performClick()
        assertEquals(listOf("commit"), events)
    }

    @Test
    fun overlaysSayWhyThereIsNothingToReview() {
        show(
            GitReviewUiState(
                overlay = ReviewOverlay.Unreachable,
                loadError = "Couldn't reach your Mac. Pull to refresh."
            )
        )
        compose.onNodeWithText("Can't reach your Mac").assertExists()
        compose.onNodeWithText("Couldn't reach your Mac. Pull to refresh.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithContentDescription("Jump to file").assertDoesNotExist()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun aProjectThatIsNotARepo() {
        show(GitReviewUiState(overlay = ReviewOverlay.NotRepo))
        compose.onNodeWithText("Not a git repository").assertExists()
        compose.onNodeWithText(
            "This project isn't tracked by git, so there's nothing to review."
        ).assertExists()
    }
}
