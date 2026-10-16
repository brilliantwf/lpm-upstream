package cx.lpm.mobile.feature.automations.conversation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.automations.FollowupComposer
import cx.lpm.mobile.feature.automations.format.AutomationTone
import cx.lpm.mobile.feature.automations.job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationConversationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<String>()

    private fun message(
        at: Long,
        question: String = "",
        quiet: Boolean = false,
        output: String = "## Summary\nAll **25** tests passed in `6.9s`."
    ) = MessageUi(
        at = at,
        question = question,
        output = output,
        quiet = quiet,
        label = "Done",
        tone = AutomationTone.Green,
        duration = "1 min 12 sec",
        cost = null,
        condensed = false,
        clock = "Today at 3:00 AM"
    )

    private fun show(state: AutomationConversationUiState) = compose.setContent {
        LpmTheme {
            AutomationConversationScreen(
                state = state,
                onBack = {},
                composer = {
                    ReplyBar(
                        FollowupComposer(
                            session = SessionKey.Live,
                            composerId = "automation:storefront:nightly:1",
                            project = "storefront",
                            label = "Nightly tests",
                            disabled = state.sending,
                            placeholder = if (state.sending) {
                                "Automation is running…"
                            } else {
                                "Reply to this run"
                            },
                            onSend = { sent += it }
                        )
                    )
                }
            )
        }
    }

    @Test
    fun turnsReadTopToBottomAndAReplyGoesOut() {
        show(
            AutomationConversationUiState(
                job = job(""""runKind":"prompt""""),
                messages = listOf(
                    message(1),
                    message(2, question = "Why?", quiet = true, output = "Because it's green.")
                )
            )
        )
        compose.onNodeWithText("Nightly tests").assertExists()
        compose.onNodeWithText("Summary").assertExists()
        compose.onNodeWithText("Why?").assertExists()
        compose.onNodeWithText("All 25 tests passed in 6.9s.").assertExists()
        compose.onNodeWithText("Reply to this run").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("  Run it again  ")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(listOf("Run it again"), sent)
    }

    @Test
    fun copyingConfirmsWithACheckmark() {
        show(AutomationConversationUiState(job = job(), messages = listOf(message(1))))
        compose.onNodeWithContentDescription("Copy message").performClick()
        compose.onNodeWithContentDescription("Copied").assertExists()
    }

    @Test
    fun aPendingReplyWaitsAndHoldsTheComposer() {
        show(
            AutomationConversationUiState(
                job = job(""""runKind":"prompt""""),
                messages = listOf(message(1)),
                pendingMessage = "And now?",
                sending = true
            )
        )
        compose.onNodeWithText("And now?").assertExists()
        compose.onNodeWithText("Waiting for reply…").assertExists()
        compose.onNodeWithText("Automation is running…").assertExists()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    @Test
    fun aCommandRunHasNoComposer() {
        show(
            AutomationConversationUiState(
                job = job(""""runKind":"cmd""""),
                messages = listOf(message(1))
            )
        )
        compose.onNodeWithText("Reply to this run").assertDoesNotExist()
    }
}
