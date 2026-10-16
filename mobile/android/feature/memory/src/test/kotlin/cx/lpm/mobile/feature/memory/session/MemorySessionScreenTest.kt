package cx.lpm.mobile.feature.memory.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemorySessionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val loaded = MemorySessionUiState(
        name = "checkout-redesign",
        title = "Checkout redesign",
        content = "# Checkout redesign\n\n## Goal\nRebuild checkout.",
        updated = "2 hours ago",
        agent = "claude"
    )

    private fun show(state: MemorySessionUiState, editing: Boolean = false) = compose.setContent {
        LpmTheme {
            MemorySessionScreen(
                state = state,
                refreshing = false,
                onRefresh = {},
                editing = editing,
                actions = MemorySessionActions(
                    back = { events += "back" },
                    edit = { events += "edit" },
                    delete = { events += "delete" },
                    retry = { events += "retry" },
                    dismissConflict = { events += "dismiss" }
                )
            )
        }
    }

    @Test
    fun theDocumentWithWhoWroteItLast() {
        show(loaded)
        compose.onNodeWithText("Updated 2 hours ago by claude").assertExists()
        compose.onNodeWithText("checkout-redesign").assertExists()
        compose.onNodeWithText("Rebuild checkout.").assertExists()
        compose.onNodeWithText("Edit").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Delete session").performClick()
        assertEquals(listOf("edit", "delete"), events)
    }

    @Test
    fun noAgentNamedSaysOnlyWhen() {
        show(loaded.copy(agent = ""))
        compose.onNodeWithText("Updated 2 hours ago").assertExists()
    }

    @Test
    fun editWaitsForTheDocument() {
        show(MemorySessionUiState(name = "a", title = "a", loading = true))
        compose.onNodeWithText("Edit").assertIsNotEnabled()
    }

    @Test
    fun aMissingSessionOffersToTryAgain() {
        show(MemorySessionUiState(name = "a", title = "a", error = "This session was removed."))
        compose.onNodeWithText("Can't open this session").assertExists()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun theConflictNoticeShowsUntilDismissedAndHidesBehindTheEditor() {
        val notice = "Changed on your Mac by an agent — reloaded the latest version."
        var editing by mutableStateOf(false)
        compose.setContent {
            LpmTheme {
                MemorySessionScreen(
                    state = loaded.copy(conflictNotice = notice),
                    refreshing = false,
                    onRefresh = {},
                    editing = editing,
                    actions = MemorySessionActions(dismissConflict = { events += "dismiss" })
                )
            }
        }
        compose.onNodeWithText(notice).assertExists()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf("dismiss"), events)
        editing = true
        compose.onNodeWithText(notice).assertDoesNotExist()
    }
}
