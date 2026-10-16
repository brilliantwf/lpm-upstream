package cx.lpm.mobile.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SegmentedControlTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingASegmentSelectsIt() {
        var selected = -1
        compose.setContent {
            LpmTheme {
                var index by remember { mutableIntStateOf(0) }
                SegmentedControl(
                    options = listOf("All", "Agents", "Services", "Automations"),
                    selectedIndex = index,
                    onSelect = {
                        index = it
                        selected = it
                    }
                )
            }
        }
        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Services").assertIsNotSelected().performClick()
        compose.onNodeWithText("Services").assertIsSelected()
        compose.onNodeWithText("All").assertIsNotSelected()
        assertEquals(2, selected)
    }
}
