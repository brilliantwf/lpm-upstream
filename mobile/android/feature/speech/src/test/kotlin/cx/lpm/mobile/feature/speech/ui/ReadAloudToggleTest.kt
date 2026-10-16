package cx.lpm.mobile.feature.speech.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadAloudToggleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theButtonBecomesStopWhileItsMessageIsRead() {
        var taps = 0
        compose.setContent { LpmTheme { ReadAloudToggle(reading = false, onClick = { taps++ }) } }
        compose.onNodeWithContentDescription("Read aloud").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun aMessageBeingReadOffersStop() {
        compose.setContent { LpmTheme { ReadAloudToggle(reading = true, onClick = {}) } }
        compose.onNodeWithContentDescription("Stop reading").assertIsDisplayed()
    }
}
