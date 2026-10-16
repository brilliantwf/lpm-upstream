package cx.lpm.mobile.feature.git.ask

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.feature.git.review.AskContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentAskSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<Pair<String, String>>()

    private fun terminal(id: String, label: String, cli: String = "", emoji: String = "") =
        TerminalInfo(
            id = id,
            label = label,
            project = "storefront",
            cols = 80,
            rows = 24,
            remote = false,
            pinned = false,
            emoji = emoji,
            cli = cli
        )

    private fun show(terminals: List<TerminalInfo>, diff: String = "@@ -1 +1 @@\n-a\n+b") =
        compose.setContent {
            LpmTheme {
                AgentAskSheet(
                    context = AskContext("src/app.ts", diff),
                    terminals = terminals,
                    onSend = { term, prompt -> sent += term.id to prompt },
                    onDismiss = {}
                )
            }
        }

    @Test
    fun theAgentTerminalIsPickedAndThePromptCarriesTheDiff() {
        show(
            listOf(
                terminal("t1", "Terminal 1"),
                terminal("t2", "Claude", cli = "claude", emoji = "🤖")
            )
        )
        compose.onNodeWithText("Ask Agent").assertExists()
        compose.onNodeWithText("🤖 Claude").assertExists()
        compose.onNodeWithText("src/app.ts").assertExists()
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithText("What should the agent do?").performTextInput("  Explain this ")
        compose.onNodeWithText("Send").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(
            listOf(
                "t2" to
                    "Explain this\n\nRegarding these changes in src/app.ts:\n```diff\n@@ -1 +1 @@\n-a\n+b\n```"
            ),
            sent
        )
    }

    @Test
    fun anotherTerminalCanBePicked() {
        show(listOf(terminal("t1", "Terminal 1"), terminal("t2", "Claude", cli = "claude")))
        compose.onNodeWithText("Terminal 1").performClick()
        compose.onNodeWithText("What should the agent do?").performTextInput("Go")
        compose.onNodeWithText("Send").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("t1", sent.single().first)
    }

    @Test
    fun withoutTerminalsThereIsNobodyToAsk() {
        show(emptyList())
        compose.onNodeWithText("No terminals").assertExists()
        compose.onNodeWithText(
            "Open a terminal for this project first, then ask the agent about these changes."
        )
            .assertExists()
        compose.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun aLongDiffPreviewsItsFirstFortyLines() {
        assertEquals(PreviewDiff.Empty, previewLines(""))
        val long = (1..45).joinToString("\n") { "+$it" }
        val cut = previewLines(long) as PreviewDiff.Cut
        assertEquals(5, cut.more)
        assertEquals("+40", cut.text.lines().last())
    }
}
