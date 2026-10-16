package cx.lpm.mobile.feature.actions.run

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.BackgroundRunInfo
import cx.lpm.mobile.feature.actions.action
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class RunSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun runWaitsForTheRequiredInputs() {
        val submitted = mutableListOf<Map<String, String>>()
        val newPost = action(
            """{"name":"new-post","label":"New post","inputs":[""" +
                """{"key":"title","label":"Title","required":true,"placeholder":"Post title"},""" +
                """{"key":"kind","label":"Kind","type":"radio","options":[""" +
                """{"value":"draft","label":"Draft"},""" +
                """{"value":"live","label":"Live"}]}]}"""
        )
        compose.setContent {
            LpmTheme { ActionInputsSheet(newPost, onSubmit = { submitted += it }, onDismiss = {}) }
        }
        compose.onNodeWithText("Run").assertIsNotEnabled()
        compose.onNodeWithText("Post title").performTextInput("Hello")
        compose.onNodeWithText("Live").performClick()
        compose.onNodeWithText("Run").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(mapOf("title" to "Hello", "kind" to "live")), submitted)
    }

    @Test
    fun aConfirmedActionReadsNext() {
        val deploy = action(
            """{"name":"d","label":"Deploy","confirm":true,"inputs":[{"key":"env",""" +
                """"default":"prod"}]}"""
        )
        compose.setContent { LpmTheme { ActionInputsSheet(deploy, onSubmit = {}, onDismiss = {}) } }
        compose.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun backgroundRunRowsShowWhereEachStands() {
        val opened = mutableListOf<String>()
        fun row(id: String, status: RunStatus, running: Boolean) =
            BackgroundRunRow(BackgroundRunInfo(id, "storefront", "Run $id", 0), status, running)
        compose.setContent {
            LpmTheme {
                BackgroundRunsList(
                    listOf(
                        row("a", RunStatus.Running, true),
                        row("b", RunStatus.Done, false),
                        row("c", RunStatus.NotStarted("nope"), false),
                        row("d", RunStatus.Failed("exit 1"), false)
                    )
                ) { opened += it.runId }
            }
        }
        compose.onNodeWithText("Background runs", ignoreCase = true).assertExists()
        compose.onNodeWithText("Running").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Couldn't start").assertExists()
        compose.onNodeWithText("Failed").assertExists()
        compose.onNodeWithText("Run b").performClick()
        assertEquals(listOf("b"), opened)
    }
}
