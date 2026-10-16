package cx.lpm.mobile.feature.config.hub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.Profile
import cx.lpm.mobile.core.model.Service
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ProjectConfigScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun action(json: String) = Action.parse(Json.parseToJsonElement(json) as JsonObject)

    private fun show() {
        val state = ProjectConfigUiState(
            services = listOf(Service("web", 3000), Service("worker", 0)),
            profiles = listOf(
                Profile("frontend", listOf("web")),
                Profile("all", listOf("web", "worker"))
            ),
            actions = listOf(
                action("""{"name":"test","label":"Test"}"""),
                action(
                    """{"name":"ship","label":"Ship","emoji":"🚀",""" +
                        """"children":[{"name":"ship:prod","label":"Prod"}]}"""
                )
            )
        )
        compose.setContent {
            LpmTheme {
                ProjectConfigScreen(
                    state = state,
                    onDone = { events += "done" },
                    onOpen = { events += it.toString() },
                    onDeleteService = { events += "delete service $it" },
                    onDeleteProfile = { events += "delete profile $it" },
                    onDeleteAction = { events += "delete action $it" }
                )
            }
        }
    }

    @Test
    fun eachRowSaysWhatItIsAndOpensItsEditor() {
        show()
        compose.onNodeWithText("Port 3000").assertExists()
        compose.onNodeWithText("1 service").assertExists()
        compose.onNodeWithText("2 services").assertExists()
        compose.onNodeWithText("🚀  Ship").performScrollTo().assertExists()
        compose.onNodeWithText("Menu").assertExists()
        compose.onNodeWithText("web").performScrollTo().performClick()
        compose.onNodeWithText("Add Profile").performScrollTo().performClick()
        compose.onNodeWithText("Edit Configuration File").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("EditService(key=web)", "AddProfile", "Yaml", "done"), events)
    }

    @Test
    fun aRowSwipesToDelete() {
        show()
        val row = compose.onNode(hasDeleteAction and hasAnyDescendant(hasText("worker")))
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == "Delete" }.action() }
        assertEquals(listOf("delete service worker"), events)
    }

    private val hasDeleteAction = SemanticsMatcher("can delete") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any {
            it.label == "Delete"
        }
    }
}
