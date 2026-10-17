package cx.lpm.mobile.feature.automations.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorFormTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        AutomationEditorUiState(
            projects = listOf(
                EditorChoice("storefront", "storefront"),
                EditorChoice("blog", "blog")
            ),
            deletable = true
        )
    )
    private var deleted = false

    private fun show() = compose.setContent {
        LpmTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EditorForm(
                    state = state,
                    onEdit = { change -> state = state.copy(draft = change(state.draft)) },
                    deleteCopies = false,
                    onDeleteCopiesChange = {},
                    onDelete = { deleted = true }
                )
            }
        }
    }

    @Test
    fun pickingProjectsSaysWhereTheJobRuns() {
        show()
        compose.onNodeWithText("No project selected — runs on its own in your home folder.")
            .assertExists()
        compose.onNode(hasText("storefront") and hasClickAction()).performClick()
        compose.onNodeWithText("Runs in the selected project.").assertExists()
        compose.onNode(hasText("blog") and hasClickAction()).performClick()
        compose.onNodeWithText("Runs in the selected projects.").assertExists()
        assertEquals(listOf("storefront", "blog"), state.draft.targets)
    }

    @Test
    fun certainDaysShowTheWeekAndTheSummaryFollows() {
        show()
        compose.onNodeWithText("Every day").performScrollTo().performClick()
        compose.onNodeWithText("On certain days").performClick()
        assertEquals(listOf("mon"), state.draft.days)
        compose.onAllNodesWithText("T")[0].performScrollTo().performClick()
        assertEquals(listOf("mon", "tue"), state.draft.days)
        compose.onNodeWithText(
            "Mon, Tue at 09:00. It starts within a few minutes of that time, so automations " +
                "set to the same hour don't all begin at once."
        ).assertExists()
    }

    @Test
    fun aRandomWindowAddsItsEndAndRunsADay() {
        show()
        compose.onNodeWithContentDescription("A random time in a window").performScrollTo()
            .performClick()
        assertTrue(state.draft.randomWindow)
        compose.onNodeWithText("Between").assertExists()
        compose.onNodeWithText("Runs a day").assertExists()
    }

    @Test
    fun aKeptScheduleIsShownNotEdited() {
        state = state.copy(keptSchedule = true, keptScheduleText = "Cron 0 9 * * 1", editing = true)
        show()
        compose.onNodeWithText("Cron 0 9 * * 1").performScrollTo().assertExists()
        compose.onNodeWithText("Repeat").assertDoesNotExist()
        compose.onNodeWithText("Delete automation").performScrollTo().performClick()
        assertTrue(deleted)
    }

    @Test
    fun anActionHasNoCheckAfterwards() {
        state = state.copy(
            draft = JobEditorDraft(
                runMode = RunMode.Action,
                action = "test",
                targets = listOf("storefront")
            ),
            actions = listOf(EditorChoice("test", "test"))
        )
        show()
        compose.onNodeWithText("Only when there's work (optional)").performScrollTo().assertExists()
        compose.onNodeWithText("Check the work afterwards (optional)").assertDoesNotExist()
    }
}
