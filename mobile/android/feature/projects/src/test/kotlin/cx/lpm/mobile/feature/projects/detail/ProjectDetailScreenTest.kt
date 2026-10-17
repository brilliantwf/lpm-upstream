package cx.lpm.mobile.feature.projects.detail

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.lpm.mobile.core.data.link.QueuedAction
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.feature.projects.project
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val terminal =
        TerminalInfo("t1", "Terminal 1", "storefront", 80, 24, false, false, "", "")

    private val state = ProjectDetailUiState(
        project = project("storefront", running = true, services = listOf("web", "api", "worker")),
        title = "storefront",
        subtitle = DetailSubtitle.Running,
        tabs = listOf(TabEntry(terminal, null)),
        services = listOf(
            ServiceEntry(Service("web", 3000), running = true, pending = false),
            ServiceEntry(Service("api", 4000), running = true, pending = false),
            ServiceEntry(Service("worker", 0), running = false, pending = false)
        )
    )

    private fun show(state: ProjectDetailUiState = this.state) = compose.setContent {
        LpmTheme {
            ProjectDetailScreen(
                state = state,
                refreshing = false,
                onRefresh = {},
                actions = ProjectDetailActions(
                    openTerminal = { events += "open ${it.label}" },
                    newTerminal = { events += "new" },
                    toggleService = { events += "toggle ${it.name}" },
                    restartService = { events += "restart ${it.name}" },
                    stopService = { events += "stop ${it.name}" },
                    cancelQueued = { events += "cancel" },
                    openLogs = { events += "logs ${it.name}" },
                    stopAll = { events += "stop all" }
                )
            )
        }
    }

    @Test
    fun servicesToggleOpenLogsAndStopAll() {
        show()
        compose.onNodeWithText("Running").assertExists()
        compose.onNodeWithText("SERVICES · 2 OF 3 RUNNING").assertExists()
        compose.onNodeWithText("Tap a service for its logs.").assertExists()
        compose.onNodeWithContentDescription("worker").performClick()
        compose.onNodeWithText("web").performClick()
        compose.onNodeWithText("Stop all").performClick()
        assertEquals(listOf("toggle worker", "logs web", "stop all"), events)
    }

    @Test
    fun tabsOpenAndANewOneWaitsWhileCreating() {
        show()
        compose.onNode(hasText("Terminal 1") and hasClickAction()).performClick()
        compose.onNodeWithText("New tab").performClick()
        assertEquals(listOf("open Terminal 1", "new"), events)
    }

    @Test
    fun newTabIsOffWhileOneIsOnItsWay() {
        show(state.copy(creating = true))
        compose.onNodeWithText("New tab").assertIsNotEnabled()
    }

    @Test
    fun anEndedServiceSaysHowAndOffersRestart() {
        val crashed = Service("api", 4000, exited = true, exitCode = 1)
        show(
            state.copy(
                subtitle = DetailSubtitle.Crashed(listOf(crashed)),
                services = listOf(
                    ServiceEntry(Service("web", 3000), running = true, pending = false),
                    ServiceEntry(crashed, running = false, pending = false, ended = crashed)
                ),
                canStopServices = true
            )
        )
        compose.onNodeWithText("api exited with code 1").assertExists()
        compose.onNodeWithText("exited with code 1").assertExists()
        compose.onNodeWithContentDescription("Restart api").performClick()
        compose.onNodeWithText("api").performTouchInput { longClick() }
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf("restart api", "stop api"), events)
    }

    @Test
    fun anActionWaitingForTheMacShowsOverTheTabs() {
        show(state.copy(queued = listOf(QueuedAction("storefront", "Start", Instant.EPOCH))))
        compose.onNodeWithText("Start · waiting for the Mac").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun nothingToListOffersANewTerminal() {
        show(state.copy(tabs = emptyList(), services = emptyList()))
        compose.onNodeWithText("No Terminals").assertExists()
        compose.onNodeWithText("New Terminal").performClick()
        assertEquals(listOf("new"), events)
    }
}
