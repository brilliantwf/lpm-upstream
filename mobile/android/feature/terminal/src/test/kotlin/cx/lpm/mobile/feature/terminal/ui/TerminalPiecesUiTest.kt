package cx.lpm.mobile.feature.terminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.web.TerminalTheme
import cx.lpm.mobile.feature.terminal.ControlHandoffView
import cx.lpm.mobile.feature.terminal.settings.TerminalLook
import cx.lpm.mobile.feature.terminal.settings.TerminalSettingsRows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalPiecesUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the font size steps within its range and the scheme changes`() {
        var look by mutableStateOf(TerminalLook(fontSize = 23))
        compose.setContent {
            LpmTheme {
                Column {
                    TerminalSettingsRows(
                        look = look,
                        onFontSizeChange = { look = look.copy(fontSize = it) },
                        onThemeChange = { look = look.copy(theme = it) }
                    )
                }
            }
        }
        compose.onNodeWithText("23").assertExists()
        compose.onNodeWithText("$ lpm run").assertExists()
        compose.onNodeWithContentDescription("Increment Font size").performClick()
        compose.onNodeWithText("24").assertExists()
        compose.onNodeWithContentDescription("Increment Font size").assertIsNotEnabled()
        compose.onNodeWithText("Claude Dark").performClick()
        compose.onNodeWithText("Nord").performClick()
        compose.waitForIdle()
        assertEquals(TerminalLook(fontSize = 24, theme = TerminalTheme.Nord), look)
    }

    @Test
    fun `taking control asks once and says so`() {
        var claims = 0
        compose.setContent {
            LpmTheme(darkTheme = true) {
                ControlHandoffView(
                    ownerLabel = "Studio Mac",
                    background = Color.Black,
                    onTakeControl = { claims++ }
                )
            }
        }
        compose.onNodeWithText("Active on Studio Mac").assertExists()
        compose.onNodeWithText("This terminal is shown and controlled elsewhere.").assertExists()
        compose.onNodeWithText("Take control").performClick()
        compose.onNodeWithText("Taking control…").assertIsNotEnabled()
        assertEquals(1, claims)
    }
}
