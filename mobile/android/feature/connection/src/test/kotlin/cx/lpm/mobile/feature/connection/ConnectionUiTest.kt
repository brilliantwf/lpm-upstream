package cx.lpm.mobile.feature.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.data.link.AwaySetup
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.link.issueCopy
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.tailnet.TailnetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ConnectionUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun content(issue: ConnectionIssue, mac: String = "your Mac") =
        IssueContentState(issue = issue, copy = issue.issueCopy(mac, "phone"))

    @Test
    fun aCardShowsTheIssueAndItsButtons() {
        val taps = mutableListOf<IssueAction>()
        compose.setContent {
            LpmTheme {
                IssueCard(
                    content(
                        ConnectionIssue.PairUnreachable(overTailscale = true)
                    ),
                    onAction = {
                        taps +=
                            it
                    }
                )
            }
        }
        compose.onNodeWithText("Your Mac isn't answering").assertExists()
        compose.onNodeWithText(
            "Check that lpm is open and Tailscale is connected on both devices."
        ).assertExists()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Details").performClick()
        assertEquals(listOf(IssueAction.Retry, IssueAction.Details), taps)
    }

    @Test
    fun aRetryUnderWaySaysTrying() {
        compose.setContent {
            LpmTheme {
                IssueCard(
                    content(
                        ConnectionIssue.NotAnswering(away = false),
                        "Studio"
                    ).copy(trying = true, waitingCount = 2),
                    onAction = {},
                    hidesDetails = true
                )
            }
        }
        compose.onNodeWithText("Studio isn't answering").assertExists()
        compose.onNodeWithText("Trying…").assertIsNotEnabled()
        compose.onNodeWithText("2 actions waiting for the Mac").assertExists()
        compose.onNodeWithText("Details").assertDoesNotExist()
    }

    @Test
    fun theBarPutsItsOneFixBesideTheWords() {
        var state by mutableStateOf<IssueContentState?>(null)
        var taps = 0
        compose.setContent { LpmTheme { ConnectionStatusBar(state, onAction = { taps++ }) } }
        compose.onNodeWithText("Turn on").assertDoesNotExist()
        state =
            content(
                ConnectionIssue.AwayNeedsTailscale(AwaySetup.TurnOn)
            ).copy(failure = "No network")
        compose.onNodeWithText("Built-in Tailscale is off").assertExists()
        compose.onNodeWithText("No network").assertExists()
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.onNodeWithText("Turn on").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun tailscaleSettingsShowTheStatusOnceOn() {
        var state by mutableStateOf(TailnetSettingsUiState(deviceName = "lpm-phone"))
        var switched: Boolean? = null
        var retries = 0
        compose.setContent {
            LpmTheme {
                TailnetSettingsContent(
                    state = state,
                    onEnabledChange = { switched = it },
                    onSignIn = {},
                    onSignOut = {},
                    onOpenAdmin = {},
                    onTryAgain = { retries++ }
                )
            }
        }
        compose.onNodeWithText("How it works").assertExists()
        compose.onNodeWithText(
            "Built-in Tailscale runs while lpm is open. It joins your tailnet as lpm-phone, and your Tailscale account is free for personal use."
        ).assertExists()
        compose.onNode(isToggleable()).performClick()
        assertEquals(true, switched)

        state =
            state.copy(
                enabled = true,
                status = TailnetStatus(state = "error", error = "Can't start")
            )
        compose.onNodeWithText("Couldn't connect to Tailscale").assertExists()
        compose.onNodeWithText("Can't start").assertExists()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)

        state = state.copy(status = TailnetStatus(state = "needsLogin"))
        compose.onNodeWithText("Sign in with Tailscale").assertExists()
        state =
            state.copy(
                status = TailnetStatus(
                    state = "running",
                    account = "me@example.com",
                    ip = "100.64.9.9"
                )
            )
        compose.onNodeWithText("Connected").assertExists()
        compose.onNodeWithText("100.64.9.9").assertExists()
        compose.onNodeWithText("Sign Out").performClick()
        compose.onNodeWithText("Sign out of Tailscale?").assertExists()
        assertTrue(true)
    }
}
