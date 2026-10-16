package cx.lpm.mobile.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LpmAlertTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonRunsItsActionThenDismisses() {
        var saved = ""
        var shown by mutableStateOf(true)
        compose.setContent {
            LpmTheme {
                var name by remember { mutableStateOf("") }
                if (shown) {
                    LpmAlert(
                        title = "Rename",
                        message = "Leave blank to use the name it reports.",
                        textField = { AlertTextField(name, { name = it }, placeholder = "Name") },
                        buttons = listOf(
                            AlertButton("Cancel", AlertRole.Cancel),
                            AlertButton("Save") { saved = name }
                        ),
                        onDismissRequest = { shown = false }
                    )
                }
            }
        }
        compose.onNodeWithText("Leave blank to use the name it reports.").assertExists()
        compose.onNodeWithText("Name").performTextInput("Studio")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals("Studio", saved)
        assertFalse(shown)
    }

    @Test
    fun twoButtonsSitSideBySideWithCancelFirst() {
        compose.setContent {
            LpmTheme {
                LpmAlert(
                    title = "Remove “Studio”?",
                    buttons = listOf(
                        AlertButton("Remove", AlertRole.Destructive),
                        AlertButton("Cancel", AlertRole.Cancel)
                    ),
                    onDismissRequest = {}
                )
            }
        }
        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        val remove = compose.onNodeWithText("Remove").getBoundsInRoot()
        assertTrue(cancel.right < remove.left)
        assertEquals(cancel.top, remove.top)
    }

    @Test
    fun threeButtonsStackWithCancelLast() {
        compose.setContent {
            LpmTheme {
                LpmAlert(
                    title = "Couldn't reach Studio",
                    buttons = listOf(
                        AlertButton("Try Again"),
                        AlertButton("Enter Code"),
                        AlertButton("Cancel", AlertRole.Cancel)
                    ),
                    onDismissRequest = {}
                )
            }
        }
        val tryAgain = compose.onNodeWithText("Try Again").getBoundsInRoot()
        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue(cancel.top > tryAgain.bottom)
    }

    @Test
    fun confirmationDialogIsAPopoverWithoutCancelAndRunsTheChoice() {
        var signedOut = false
        var shown by mutableStateOf(true)
        compose.setContent {
            LpmTheme {
                if (shown) {
                    LpmConfirmationDialog(
                        title = "Sign out of Tailscale?",
                        buttons = listOf(
                            AlertButton("Sign Out", AlertRole.Destructive) {
                                signedOut =
                                    true
                            }
                        ),
                        onDismissRequest = { shown = false }
                    )
                }
            }
        }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("Sign Out").performClick()
        compose.waitForIdle()
        assertTrue(signedOut)
        assertFalse(shown)
    }

    @Test
    fun capsuleButtonReaches48dpToTouch() {
        var taps = 0
        compose.setContent {
            LpmTheme { CapsuleButton(text = "Try again", onClick = { taps++ }, prominent = true) }
        }
        compose.onNode(hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, taps)
    }
}
