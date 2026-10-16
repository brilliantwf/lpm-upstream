package cx.lpm.mobile.feature.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Service
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A phone's height, so the service card and footer are laid out under the rows.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ActivityScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val activity = build(
        listOf(
            project(
                "storefront",
                running = true,
                services = listOf(Service("web", 3000), Service("worker", 0)),
                started = listOf("web"),
                statusEntries = listOf(
                    status("claude_code_1", "Waiting", pane = "t1"),
                    status("codex_1", "Done", pane = "t2")
                )
            ),
            project("blog")
        ),
        titles = mapOf("storefront" to mapOf("t1" to "Checkout bug"))
    )

    private fun show(state: ActivityUiState = ActivityUiState(activity = activity)) {
        compose.setContent {
            var query by remember { mutableStateOf("") }
            var searching by remember { mutableStateOf(false) }
            var kind by remember { mutableStateOf(ActivityKindFilter.All) }
            LpmTheme {
                ActivityScreen(
                    state = state,
                    query = query,
                    searching = searching,
                    kind = kind,
                    now = NOW,
                    refreshing = false,
                    actions = ActivityActions(
                        search = { query = it },
                        beginSearch = { searching = true },
                        endSearch = {
                            query = ""
                            searching = false
                        },
                        pickKind = { kind = it },
                        toggleGrouped = { events += "group" },
                        list = ActivityListActions(
                            open = { events += "open ${it.id}" },
                            dismiss = { events += "clear ${it.id}" },
                            explain = { events += "why $it" },
                            runChip = { project, chip -> events += "chip $project ${chip.name}" },
                            stop = { events += "stop $it" },
                            openProject = { events += "project $it" }
                        )
                    )
                )
            }
        }
    }

    @Test
    fun rowsReadAsOnIosAndOpenOnTap() {
        show()
        compose.onNodeWithText("Checkout bug").assertIsDisplayed()
        compose.onNodeWithText("Needs you").assertIsDisplayed()
        compose.onNodeWithText("waiting 1m").assertIsDisplayed()
        compose.onNodeWithText("1 other project has nothing running.").assertIsDisplayed()
        compose.onNodeWithText("Checkout bug").performClick()
        assertEquals(listOf("open agent:storefront:claude_code_1"), events)
    }

    /** The row holding [text], whose swipe actions TalkBack reaches as custom actions. */
    private fun swipeable(text: String) = compose.onNode(
        hasAnyDescendant(hasText(text)) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
    )

    private fun swipe(text: String, label: String) {
        val actions = swipeable(text).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == label }.action() }
    }

    @Test
    fun swipeActionsClearAFinishAndExplainAWait() {
        show()
        swipe("Checkout bug", "Why?")
        swipe("Done", "Clear")
        assertEquals(
            listOf(
                "why The agent is asking for you — answering it is what clears this.",
                "clear agent:storefront:codex_1"
            ),
            events
        )
    }

    @Test
    fun theServiceCardStartsAndStops() {
        show()
        compose.onNodeWithText("SERVICES").assertIsDisplayed()
        compose.onNodeWithText("1 of 2 running").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start the worker service").performClick()
        compose.onNodeWithContentDescription("Stop everything in storefront").performClick()
        assertEquals(listOf("chip storefront worker", "stop storefront"), events)
    }

    @Test
    fun filtersNarrowTheListUntilNothingMatches() {
        show()
        compose.onNodeWithText("Services").performClick()
        compose.onNodeWithText("Checkout bug").assertDoesNotExist()
        compose.onNodeWithText("Agents").performClick()
        compose.onNodeWithText("SERVICES").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        compose.onNodeWithText("Nothing matches").assertIsDisplayed()
        compose.onNodeWithText("Try a different search, or switch back to All.").assertIsDisplayed()
    }

    @Test
    fun searchingTakesTheBarsPlaceUntilClosed() {
        show()
        compose.onNodeWithText("Activity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()

        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        compose.onNodeWithText("Activity").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Nothing matches").assertIsDisplayed()

        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Activity").assertIsDisplayed()
        compose.onNodeWithText("Checkout bug").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun anEmptyMacSaysNothingIsRunningAndARefusingOneSaysWhy() {
        show(ActivityUiState())
        compose.onNodeWithText("Nothing is running").assertIsDisplayed()
        compose.onNodeWithContentDescription("Group by project").performClick()
        assertEquals(listOf("group"), events)
    }

    @Test
    fun aMacRefusingThisPhoneHasNothingToReport() {
        show(ActivityUiState(notConnectedTo = "Studio Mac"))
        compose.onNodeWithText("Not connected to Studio Mac").assertIsDisplayed()
        compose.onNodeWithText("Its activity shows again once it's paired.").assertIsDisplayed()
    }
}
