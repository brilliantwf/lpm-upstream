package cx.lpm.mobile.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LpmStepperTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setStepper(start: Int) {
        compose.setContent {
            LpmTheme {
                var size by remember { mutableIntStateOf(start) }
                StepperRow(
                    title = "Font size",
                    value = size,
                    onValueChange = { size = it },
                    range =
                        8..10
                )
            }
        }
    }

    @Test
    fun stepsUpToTheMaximumThenStops() {
        setStepper(start = 9)
        val plus = compose.onNodeWithContentDescription("Increment Font size")
        plus.performClick()
        compose.onNodeWithText("10").assertExists()
        plus.assertIsNotEnabled().performClick()
        compose.onNodeWithText("10").assertExists()
        compose.onNodeWithContentDescription("Decrement Font size").assertIsEnabled()
    }

    @Test
    fun stepsDownToTheMinimumThenStops() {
        setStepper(start = 9)
        val minus = compose.onNodeWithContentDescription("Decrement Font size")
        minus.performClick()
        compose.onNodeWithText("8").assertExists()
        minus.assertIsNotEnabled().performClick()
        compose.onNodeWithText("8").assertExists()
        compose.onNodeWithContentDescription("Increment Font size").assertIsEnabled()
    }
}
