package cx.lpm.mobile.feature.projects.list

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.AgentState
import cx.lpm.mobile.core.model.ProjectAgentRow
import cx.lpm.mobile.feature.projects.project
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MachineSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun header(
        detail: MachineDetail? = null,
        count: Int? = 2,
        opensOnlyBySwitching: Boolean = false,
        canOpen: Boolean = true
    ) = ProjectsListRow.MachineEntry(
        slug = "box",
        name = "Build Box",
        linuxHost = true,
        detail = detail,
        count = count,
        opensOnlyBySwitching = opensOnlyBySwitching,
        canOpen = canOpen,
        expanded = true
    )

    private fun row(
        name: String,
        canOpen: Boolean = true,
        agents: List<ProjectAgentRow> = emptyList()
    ) = ProjectsListRow.MachineProjectEntry("box", project(name), agents, canOpen, stale = false)

    private fun show(vararg rows: ProjectsListRow) = compose.setContent {
        LpmTheme {
            ProjectsScreen(
                state = ProjectsUiState(rows = rows.toList(), listIsEmpty = false, loaded = true),
                refreshing = false,
                onRefresh = {},
                onExitDemo = {},
                machineActions = MachineActions(
                    toggle = { slug, open -> events += "toggle $slug $open" },
                    switchTo = { events += "switch $it" },
                    open = { slug, project -> events += "open $slug $project" },
                    openTerminal = { slug, project, id -> events += "terminal $slug $project $id" }
                )
            )
        }
    }

    @Test
    fun aMachineCountsItsProjectsAndOpensThemInPlace() {
        val waiting = ProjectAgentRow(
            id = "claude_code_1",
            state = AgentState.NeedsYou,
            title = "Claude Code",
            provider = "Claude Code",
            terminalId = "t1",
            since = null,
            until = null
        )
        show(header(), row("api", agents = listOf(waiting)), row("api-2"))
        compose.onNodeWithText("Build Box").assertExists()
        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithText("Build Box").performClick()
        compose.onNodeWithText("api").performClick()
        compose.onNodeWithText("Claude Code", useUnmergedTree = true).performClick()
        assertEquals(listOf("toggle box true", "open box api", "terminal box api t1"), events)
    }

    @Test
    fun aLongPressSwitchesToTheMachine() {
        show(header(detail = MachineDetail.OpensThroughMac))
        compose.onNodeWithText("Opens through this Mac").assertExists()
        compose.onNodeWithText("Build Box").performTouchInput { longClick() }
        compose.onNodeWithText("Switch to Build Box").performClick()
        assertEquals(listOf("switch box"), events)
    }

    @Test
    fun aMachineThatCantOpenIsGrayedOutWithNoMenu() {
        show(
            header(detail = MachineDetail.NotOnPhone, canOpen = false),
            row("api", canOpen = false)
        )
        compose.onNodeWithText("Not on this phone").assertExists()
        compose.onNodeWithText("api").assertIsNotEnabled()
        compose.onNodeWithText("Build Box").performTouchInput { longClick() }
        compose.onNodeWithText("Switch to Build Box").assertDoesNotExist()
    }

    @Test
    fun anOldMacsMachineOpensBySwitching() {
        show(header(count = null, opensOnlyBySwitching = true))
        compose.onNodeWithText("Open").performClick()
        assertEquals(listOf("switch box"), events)
    }

    @Test
    fun eachStepOfAddingReads() {
        show(
            header(detail = MachineDetail.Adding).copy(slug = "a"),
            header(detail = MachineDetail.NeedsUpdate).copy(slug = "b", name = "Old Box"),
            header(
                detail = MachineDetail.Failed("It didn't answer in time.")
            ).copy(slug = "c", name = "GPU Box")
        )
        compose.onNodeWithText("Adding to this phone…").assertExists()
        compose.onNodeWithText("Update lpm on it to open it here").assertExists()
        compose.onNodeWithText("It didn't answer in time.").assertExists()
    }
}
