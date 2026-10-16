package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchTargetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glassButtonTouchAreaIs48dpWhileLayoutKeeps44dp() {
        compose.setContent {
            LpmTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    GlassIconButton(painterResource(R.drawable.ic_arrow_back_ios_new), "Back", {})
                    Box(Modifier.size(10.dp).testTag("next"))
                }
            }
        }
        compose.onNodeWithContentDescription("Back")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("next").assertLeftPositionInRootIsEqualTo(55.dp)
    }

    @Test
    fun linkButtonTouchAreaReaches48dp() {
        compose.setContent {
            LpmTheme { LinkButton(text = "Stop all", onClick = {}) }
        }
        compose.onNodeWithText("Stop all").assertHeightIsAtLeast(48.dp)
    }
}
