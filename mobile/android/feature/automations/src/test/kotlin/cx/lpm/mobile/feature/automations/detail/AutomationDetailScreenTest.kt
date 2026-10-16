package cx.lpm.mobile.feature.automations.detail

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.automations.format.AutomationTone
import cx.lpm.mobile.feature.automations.job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun run(at: Long, label: String = "Done") = RunRowUi(
        rootAt = at,
        label = label,
        tone = AutomationTone.Green,
        unread = at == 9L,
        preview = null,
        meta = "Today at 3:00 AM · 1 min 12 sec"
    )

    private val state = AutomationDetailUiState(
        loaded = true,
        job = job(""""emoji":"🌙","unread":1,"runKind":"cmd","description":"npm test""""),
        scope = "storefront",
        pill = "Next run in 9 hours",
        pillTone = AutomationTone.Green,
        schedule = "Every day at 03:00",
        nextRun = "Tomorrow at 3:00 AM",
        lastRun = TimeRowUi("Today at 3:00 AM", "Done · 9 hours ago", AutomationTone.Green),
        setup = SetupUi("npm test", true, "npm test", null, duplicate = true, runsIn = null),
        runs = (9L downTo 1L).map { run(it) }
    )

    private fun show(state: AutomationDetailUiState = this.state) = compose.setContent {
        LpmTheme {
            AutomationDetailScreen(
                state = state,
                refreshing = false,
                onRefresh = {},
                actions = AutomationDetailActions(
                    edit = { events += "edit" },
                    setEnabled = { events += "enabled $it" },
                    toggleSchedule = { events += "schedule" },
                    toggleSetup = { events += "setup" },
                    markRead = { events += "mark read" },
                    openRun = { events += "run $it" },
                    runBar = { events += "run bar" }
                )
            )
        }
    }

    @Test
    fun foldedGroupsShowTheirOneFactAndTheRunBarRuns() {
        show()
        compose.onNodeWithText("Next run in 9 hours").assertExists()
        compose.onNodeWithText("Every day at 03:00").assertExists()
        compose.onNodeWithText("Automation on").assertDoesNotExist()
        compose.onNode(hasText("Schedule") and hasClickAction()).performClick()
        compose.onNode(hasText("Does") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Edit automation").performClick()
        compose.onNodeWithText("Run now").performClick()
        assertEquals(listOf("schedule", "setup", "edit", "run bar"), events)
    }

    @Test
    fun openGroupsShowTheSwitchTimesAndSetup() {
        show(state.copy(scheduleOpen = true, setupOpen = true))
        compose.onNodeWithText("Next run").assertExists()
        compose.onNodeWithText("Done · 9 hours ago").assertExists()
        compose.onNodeWithText("Works in a fresh copy of the project").assertExists()
        compose.onNodeWithContentDescription("Automation on").performClick()
        assertEquals(listOf("enabled false"), events)
    }

    @Test
    fun theHistoryPreviewsEightRunsUntilAskedForAll() {
        show()
        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("Mark read").performClick()
        compose.onNodeWithText("Show all 9 runs").performScrollTo().performClick()
        compose.onNodeWithText("Show all 9 runs").assertDoesNotExist()
        compose.onNodeWithText("New").performScrollTo().performClick()
        assertEquals(listOf("mark read", "run 9"), events)
    }

    @Test
    fun aPendingRequestHoldsTheRunBar() {
        show(state.copy(pending = true))
        compose.onNode(hasText("Run now") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun aRunningJobOffersToStopAndShowsItsOutput() {
        show(
            state.copy(
                job = job(""""running":true,"runningSince":1,"runningCount":2"""),
                live = "line 1\nline 2"
            )
        )
        compose.onNodeWithText("Live output").assertExists()
        compose.onNodeWithText("line 1\nline 2").assertExists()
        compose.onNodeWithText("Stop 2 runs").performClick()
        assertEquals(listOf("run bar"), events)
    }

    @Test
    fun aMissingJobSaysSo() {
        show(AutomationDetailUiState(loaded = true))
        compose.onNodeWithText("Automation").assertExists()
        compose.onNodeWithText("Automation not found").assertExists()
        compose.onNodeWithText("Run now").assertDoesNotExist()
    }
}
