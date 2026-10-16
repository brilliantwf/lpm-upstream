package cx.lpm.mobile.feature.actions.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.actions.zone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ActionFormTest {
    @get:Rule
    val compose = createComposeRule()

    private var latest = ActionDraft()

    private fun show(initial: ActionDraft, editing: Boolean = false) {
        compose.setContent {
            var draft by remember { mutableStateOf(initial) }
            latest = draft
            LpmTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ActionForm(
                        state = ActionEditorUiState(
                            editing = editing,
                            draft = draft,
                            zones = listOf(zone("""{"name":"checks","label":"Checks"}"""))
                        ),
                        onEdit = { change -> draft = change(draft) },
                        onDelete = {}
                    )
                }
            }
        }
    }

    @Test
    fun aCommandButtonSaysWhereItRuns() {
        show(ActionDraft())
        compose.onNodeWithText(
            "The name is how this action is stored. The label is what you see on the button."
        )
            .assertExists()
        compose.onNodeWithText("npm run deploy").assertExists()
        compose.onNodeWithText("Opens a new terminal each time.").assertExists()
        compose.onNode(
            hasContentDescription("Reuse the same tab") and isToggleable()
        ).performClick()
        compose.onNodeWithText("Reuses the same terminal each time.").assertExists()
        compose.onNodeWithText("Add input").performScrollTo().performClick()
        compose.onNodeWithText("Remove input").assertExists()
        compose.onNodeWithText("Delete action").assertDoesNotExist()
    }

    @Test
    fun aMenuListsItsItemsAndWhatTheButtonRuns() {
        show(ActionDraft(kind = ActionKind.Menu), editing = true)
        compose.onNodeWithText("Add item").performScrollTo().performClick()
        compose.onNodeWithText("Each item runs its own command from the menu.").assertExists()
        compose.onNodeWithText("Nothing, opens the menu").assertExists()
        compose.onNodeWithText("Delete action").performScrollTo().assertExists()
        assertEquals(1, latest.children.size)
    }

    @Test
    fun aZonePlacementNamesItsBar() {
        show(ActionDraft(placement = ActionPlacement.InZone("checks", null)))
        compose.onNodeWithText(
            "In the Checks zone of the top bar."
        ).performScrollTo().assertExists()
    }
}
