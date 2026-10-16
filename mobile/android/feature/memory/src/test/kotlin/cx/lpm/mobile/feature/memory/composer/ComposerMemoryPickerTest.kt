package cx.lpm.mobile.feature.memory.composer

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposerMemoryPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val rows = listOf(
        PickerRowUi("checkout-redesign", "Checkout redesign", "2 hours ago"),
        PickerRowUi("payments-module", "Payments module", "yesterday"),
        PickerRowUi("cart-badge-bug", "Cart badge not updating", "5 days ago")
    )

    private fun show(state: ComposerMemoryUiState) = compose.setContent {
        LpmTheme {
            ComposerMemoryPicker(
                state = state,
                actions = PickerActions(
                    cancel = { events += "cancel" },
                    remember = { events += "remember" },
                    pick = { events += "pick ${it.name}" },
                    preview = { events += "preview ${it.name}" },
                    delete = { events += "delete ${it.name}" },
                    search = { events += "search $it" }
                )
            )
        }
    }

    @Test
    fun rememberPickOrPreview() {
        show(ComposerMemoryUiState(sessions = rows, visible = rows, loading = false))
        compose.onNodeWithText("Memory").assertExists()
        compose.onNodeWithText("Continue a session").assertExists()
        compose.onNodeWithText("Search sessions").assertDoesNotExist()
        compose.onNodeWithText("Remember this conversation").performClick()
        compose.onNode(hasText("Payments module") and hasClickAction()).performClick()
        compose.onAllNodesWithContentDescription("Preview").onFirst().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(
            listOf("remember", "pick payments-module", "preview checkout-redesign", "cancel"),
            events
        )
    }

    @Test
    fun fourSessionsAreSearchedInsteadOfHeaded() {
        val four = rows + PickerRowUi("release-notes", "Release notes", "now")
        show(ComposerMemoryUiState(sessions = four, visible = four, loading = false))
        compose.onNodeWithText("Search sessions").assertExists()
        compose.onNodeWithText("Continue a session").assertDoesNotExist()
    }

    @Test
    fun aSearchThatMatchesNothingSaysSo() {
        val four = rows + PickerRowUi("release-notes", "Release notes", "now")
        show(
            ComposerMemoryUiState(
                sessions = four,
                visible = emptyList(),
                query = "zzz",
                loading = false
            )
        )
        compose.onNodeWithText("No Results for “zzz”").assertExists()
        compose.onNodeWithText("Check the spelling or try a new search.").assertExists()
    }

    @Test
    fun nothingSavedStillOffersToRemember() {
        show(ComposerMemoryUiState(loading = false))
        compose.onNodeWithText("Nothing saved yet").assertExists()
        compose.onNodeWithText("Sessions you save show up here.").assertExists()
        compose.onNodeWithText("Continue a session").assertDoesNotExist()
        compose.onNodeWithText("Remember this conversation").assertExists()
    }

    @Test
    fun loadingShowsNoEmptyState() {
        show(ComposerMemoryUiState())
        compose.onNodeWithText("Nothing saved yet").assertDoesNotExist()
    }
}
