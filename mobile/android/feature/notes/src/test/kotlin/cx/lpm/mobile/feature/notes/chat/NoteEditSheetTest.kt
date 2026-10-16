package cx.lpm.mobile.feature.notes.chat

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteEditSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<String>()

    @Test
    fun savesTheEditedTextButNeverAnEmptyNote() {
        compose.setContent {
            LpmTheme { NoteEditSheet(text = "Old note", onSave = { saved += it }, onDismiss = {}) }
        }
        compose.onNodeWithText("Edit note").assertExists()
        compose.onNodeWithText("Old note").performTextReplacement("  ")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("  ").performTextReplacement("New note")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(listOf("New note"), saved)
    }
}
