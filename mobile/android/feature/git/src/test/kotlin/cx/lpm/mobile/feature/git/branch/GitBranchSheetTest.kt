package cx.lpm.mobile.feature.git.branch

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitBranch
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitBranchSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: GitBranchUiState) = compose.setContent {
        LpmTheme {
            GitBranchSheet(
                state = state,
                onDismiss = { events += "cancel" },
                onNewBranch = { events += "new" },
                onCheckout = { events += "checkout ${it.id}" },
                onRetry = { events += "retry" }
            )
        }
    }

    private val branches = listOf(
        GitBranch("feat/x", "", ""),
        GitBranch("main", "", ""),
        GitBranch("main", "", "origin")
    )

    @Test
    fun localThenRemoteWithTheCurrentOneUntappable() {
        show(GitBranchUiState(branches = branches, current = "feat/x"))
        compose.onNodeWithText("Switch Branch").assertExists()
        compose.onNodeWithText("Local").assertExists()
        compose.onNodeWithText("Remote").assertExists()
        compose.onNodeWithText("origin").assertExists()
        compose.onNode(hasText("feat/x") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("origin").performClick()
        compose.onNodeWithContentDescription("New branch").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("checkout origin/main", "new", "cancel"), events)
    }

    @Test
    fun nothingCanBePickedWhileACheckoutRuns() {
        val withDev = branches + GitBranch("dev", "", "upstream")
        show(GitBranchUiState(branches = withDev, current = "feat/x", checkingOut = "main"))
        compose.onNode(hasText("upstream") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun nothingCanBePickedWhileTheListRefreshes() {
        show(GitBranchUiState(branches = branches, current = "feat/x", loading = true))
        compose.onNode(hasText("origin") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("feat/x") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("origin") and hasClickAction()).assertIsNotSelected()
    }

    @Test
    fun loadingFailedAndEmptyStates() {
        show(GitBranchUiState())
        compose.onNodeWithText("Loading branches…").assertExists()
    }

    @Test
    fun aFailedLoadOffersRetry() {
        show(GitBranchUiState(error = "Couldn't load branches. Try again."))
        compose.onNodeWithText("Couldn't load branches").assertExists()
        compose.onNodeWithText("Retry").assertHasClickAction().performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun noBranches() {
        show(GitBranchUiState(branches = emptyList()))
        compose.onNodeWithText("No branches").assertExists()
    }

    @Test
    fun commitDatesReadAsHowLongAgo() {
        val now = Instant.parse("2026-10-16T12:00:00Z")
        val utc = ZoneOffset.UTC
        assertEquals("2 days ago", branchSubtitle("2026-10-14T12:00:00Z", now, utc))
        assertEquals("3 hours ago", branchSubtitle("${now.epochSecond - 3 * 3_600}", now, utc))
        assertEquals("1 week ago", branchSubtitle("2026-10-06T12:00:00+00:00", now, utc))
        assertEquals("yesterday-ish", branchSubtitle("yesterday-ish", now, utc))
        assertEquals(null, branchSubtitle("  ", now, utc))
    }
}
