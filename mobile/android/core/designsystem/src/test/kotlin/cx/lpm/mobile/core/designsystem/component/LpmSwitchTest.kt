package cx.lpm.mobile.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
class LpmSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tapTogglesTheSwitch() {
        compose.setContent {
            LpmTheme {
                var on by remember { mutableStateOf(false) }
                LpmSwitch(checked = on, onCheckedChange = {
                    on = it
                }, contentDescription = "worker")
            }
        }
        val switch = compose.onNodeWithContentDescription("worker")
        switch.assertIsOff()
        switch.performClick()
        switch.assertIsOn()
        switch.performClick()
        switch.assertIsOff()
    }

    @Test
    fun disabledSwitchIgnoresTaps() {
        var changes = 0
        compose.setContent {
            LpmTheme {
                LpmSwitch(checked = true, onCheckedChange = {
                    changes++
                }, enabled = false, contentDescription = "web")
            }
        }
        compose.onNodeWithContentDescription("web").performClick().assertIsOn()
        assertEquals(0, changes)
    }

    @Test
    fun toggleRowNamesItsSwitchWithTheTitle() {
        compose.setContent {
            LpmTheme {
                var on by remember { mutableStateOf(true) }
                ListCard {
                    ToggleRow(title = "api", detail = ":4000", checked = on, onCheckedChange = {
                        on =
                            it
                    })
                }
            }
        }
        compose.onNodeWithContentDescription("api").assertIsOn().performClick().assertIsOff()
    }
}
