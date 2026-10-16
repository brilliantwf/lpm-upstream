package cx.lpm.mobile.feature.config.dirs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.DirListing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class DirBrowserScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: DirBrowserUiState) {
        compose.setContent {
            LpmTheme {
                DirBrowserScreen(
                    title = "Project folder",
                    state = state,
                    onOpen = { events += "open $it" },
                    onOpenChild = { events += "child $it" },
                    onPick = { events += "pick $it" },
                    onBack = { events += "back" }
                )
            }
        }
    }

    @Test
    fun aFolderListsItsChildrenAndHandsItsPathBack() {
        show(DirBrowserUiState(DirListing("/Users/demo", "/Users", listOf("Projects"))))
        compose.onNodeWithText("Project folder").assertExists()
        compose.onNodeWithText("/Users/demo").assertExists()
        compose.onNodeWithText("Up").performClick()
        compose.onNodeWithText("Projects").performClick()
        compose.onNodeWithText("Use This Folder").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(
            listOf("open /Users", "child Projects", "pick /Users/demo", "back"),
            events
        )
    }

    @Test
    fun anEmptyFolderSaysSoAndTheRootHasNoWayUp() {
        show(DirBrowserUiState(DirListing("/", null, emptyList())))
        compose.onNodeWithText("No folders here.").assertExists()
        compose.onNodeWithText("Up").assertDoesNotExist()
    }

    @Test
    fun nothingCanBePickedBeforeTheFirstListing() {
        show(DirBrowserUiState(loading = true))
        compose.onNodeWithText("Loading…").assertExists()
        compose.onNodeWithText("…").assertExists()
        compose.onNode(hasText("Use This Folder") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun aFolderThatWontOpenSaysWhy() {
        show(DirBrowserUiState(error = "Permission denied"))
        compose.onNodeWithText("Can't open that folder").assertExists()
        compose.onNodeWithText("Permission denied").assertExists()
    }
}
