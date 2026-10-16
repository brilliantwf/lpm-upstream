package cx.lpm.mobile.feature.notes.list

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.model.NoteSearchHit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val checkout =
        NoteChatRowUi(NoteChat("c1", "Checkout redesign", 1, 2), "22m ago", busy = false)
    private val untitled = NoteChatRowUi(NoteChat("c2", "", 1, 1), "1w ago", busy = false)

    private fun show(state: NotesUiState, searchActive: Boolean = false) = compose.setContent {
        LpmTheme {
            NotesScreen(
                state = state,
                refreshing = false,
                onRefresh = {},
                searchActive = searchActive,
                actions = NotesActions(
                    back = { events += "back" },
                    newChat = { events += "new" },
                    search = { events += "search $it" },
                    beginSearch = { events += "begin" },
                    endSearch = { events += "end" },
                    open = { events += "open ${it.id}" },
                    openHit = { events += "hit ${it.chatId}" },
                    rename = { events += "rename ${it.id}" },
                    delete = { events += "delete ${it.id}" },
                    dismissError = { events += "dismiss" }
                )
            )
        }
    }

    @Test
    fun chatsOpenRenameAndDelete() {
        show(NotesUiState(project = "storefront", chats = listOf(checkout, untitled)))
        compose.onNodeWithText("Notes").assertExists()
        compose.onNodeWithText("22m ago").assertExists()
        compose.onNodeWithText("Untitled").assertExists()
        compose.onNode(hasText("Checkout redesign") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("New Chat").performClick()
        customAction("Rename")
        customAction("Delete")
        assertEquals(listOf("open c1", "new", "rename c1", "delete c1"), events)
    }

    @Test
    fun typingSearchesAndHitsOpenTheirChat() {
        val hit = NoteSearchHit("n7", "c1", "Checkout redesign", 1, "Warming the mock fixes it")
        show(
            NotesUiState(
                project = "storefront",
                search = "mock",
                hits = listOf(NoteHitRowUi(hit, "3d ago"))
            )
        )
        compose.onNodeWithText("Warming the mock fixes it").performClick()
        compose.onNodeWithText("Search notes").assertDoesNotExist()
        compose.onNodeWithText("mock").performTextReplacement("mocks")
        assertEquals(listOf("hit c1", "begin", "search mocks"), events)
    }

    @Test
    fun focusingTheSearchBeginsSearching() {
        show(NotesUiState(project = "storefront", chats = listOf(checkout)))
        compose.onNodeWithText("Search notes").performClick()
        assertEquals(listOf("begin"), events)
    }

    @Test
    fun searchingTakesTheBarsPlaceUntilClosed() {
        show(NotesUiState(project = "storefront", search = "mock"), searchActive = true)
        compose.onNodeWithText("Notes").assertDoesNotExist()
        compose.onNodeWithContentDescription("New Chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("end"), events)
    }

    @Test
    fun overlaysSayWhyThereIsNothing() {
        show(NotesUiState(project = "p", overlay = NotesOverlay.Empty))
        compose.onNodeWithText("No notes yet").assertExists()
        compose.onNodeWithText(
            "Keep ideas, links and snippets for this project here. They stay on your Mac."
        ).assertExists()
    }

    @Test
    fun noMatchesNameTheQuery() {
        show(
            NotesUiState(project = "p", search = "zebra", overlay = NotesOverlay.NoResults("zebra"))
        )
        compose.onNodeWithText("No Results for “zebra”").assertExists()
        compose.onNodeWithText("Check the spelling or try a new search.").assertExists()
    }

    @Test
    fun aFailedChatChangeShowsInTheBannerAndCloses() {
        show(
            NotesUiState(
                project = "p",
                chats = listOf(checkout),
                chatError = "Creating the chat timed out. Try again.",
                creating = true
            )
        )
        compose.onNodeWithText("Creating the chat timed out. Try again.").assertExists()
        compose.onNodeWithContentDescription("New Chat").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("dismiss"), events)
    }

    /** The first row's swipe action, as TalkBack offers it. */
    private fun customAction(label: String) {
        val node = compose.onAllNodes(hasCustomAction(label))[0].fetchSemanticsNode()
        compose.runOnIdle {
            node.config[SemanticsActions.CustomActions].first { it.label == label }.action()
        }
    }

    private fun hasCustomAction(label: String) = SemanticsMatcher("has the $label action") { node ->
        SemanticsActions.CustomActions in node.config &&
            node.config[SemanticsActions.CustomActions].any { it.label == label }
    }
}
