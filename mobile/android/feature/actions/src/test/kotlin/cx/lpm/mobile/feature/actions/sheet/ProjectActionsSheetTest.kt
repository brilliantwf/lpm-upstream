package cx.lpm.mobile.feature.actions.sheet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.actions.ActionMemoryState
import cx.lpm.mobile.feature.actions.action
import cx.lpm.mobile.feature.actions.button.ActionCallbacks
import cx.lpm.mobile.feature.actions.layout.ActionRows
import cx.lpm.mobile.feature.actions.zone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ProjectActionsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val zones = listOf(
        zone("""{"name":"checks","label":"Checks","rows":1,"display":"header","position":1}"""),
        zone(
            """{"name":"agents","label":"Agents","rows":2,"display":"header","position":2,""" +
                """"layers":[{"name":"claude","label":"Claude"},{"name":"codex",""" +
                """"label":"Codex"}]}"""
        )
    )
    private val actions = listOf(
        action("""{"name":"test","label":"Test","display":"checks"}"""),
        action("""{"name":"lint","label":"Lint","display":"checks"}"""),
        action(
            """{"name":"fix","label":"Fix tests","display":"agents","layer":"claude",""" +
                """"color":"claude"}"""
        ),
        action("""{"name":"codex","label":"Ask Codex","display":"agents","layer":"codex"}"""),
        action(
            """{"name":"ship","label":"Ship","display":"footer","primary":"prod","children":[""" +
                """{"name":"ship:staging","label":"Staging"},{"name":"ship:prod",""" +
                """"label":"Prod"}]}"""
        ),
        action("""{"name":"clean","label":"Clean","display":"menu"}""")
    )

    private fun show(openLayers: Map<String, String> = emptyMap()) {
        compose.setContent {
            LpmTheme {
                ProjectActionsSheet(
                    state = ProjectActionsUiState(
                        project = "storefront",
                        zones = zones,
                        rows = ActionRows.of(actions, zones),
                        memory = ActionMemoryState(openLayers = openLayers)
                    ),
                    callbacks = ActionCallbacks(
                        run = { events += "run ${it.name}" },
                        edit = { events += "edit ${it.name}" }
                    ),
                    onShowLayer = { zone, layer -> events += "layer $zone/$layer" },
                    onNew = { events += "new" }
                )
            }
        }
    }

    @Test
    fun theSheetListsTheRowsAndRunsWhatsTapped() {
        show()
        compose.onNodeWithText("Header", ignoreCase = true).assertExists()
        compose.onNodeWithText("Footer", ignoreCase = true).assertExists()
        compose.onNodeWithText("Menu", ignoreCase = true).assertExists()
        compose.onNodeWithText("Test").performClick()
        // A split group's face runs its primary child.
        compose.onNodeWithText("Prod").performClick()
        compose.onNodeWithContentDescription("More Ship Actions").performClick()
        compose.onNodeWithText("Staging").performClick()
        compose.onNodeWithContentDescription("New Action").performClick()
        assertEquals(listOf("run test", "run ship:prod", "run ship:staging", "new"), events)
    }

    @Test
    fun aLongPressOffersTheEditor() {
        show()
        compose.onNodeWithText("Lint").performTouchInput { longClick() }
        compose.onNodeWithText("Edit Action…").performClick()
        assertEquals(listOf("edit lint"), events)
    }

    @Test
    fun aZoneShowsOneLayerAtATime() {
        show()
        compose.onNodeWithText("Fix tests").assertIsDisplayed()
        compose.onNodeWithText("Ask Codex").assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "Layer"
        ).performSemanticsAction(SemanticsActions.SetProgress) {
            it(1f)
        }
        assertEquals(listOf("layer agents/codex"), events)
    }

    @Test
    fun aRememberedLayerOpensFirst() {
        show(openLayers = mapOf("storefront/agents" to "codex"))
        compose.onNodeWithText("Ask Codex").assertIsDisplayed()
        compose.onNodeWithText("Fix tests").assertDoesNotExist()
    }
}
