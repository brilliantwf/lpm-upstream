package cx.lpm.mobile.core.designsystem.theme

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppAppearanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aSheetOverAForcedDarkScreenTakesTheAppsAppearance() {
        var forced: Boolean? = null
        var sheet: Boolean? = null
        compose.setContent {
            LpmTheme(darkTheme = false) {
                LpmTheme(darkTheme = true) {
                    forced = LpmTheme.colors.isDark
                    AppAppearance { sheet = LpmTheme.colors.isDark }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(true, forced)
        assertEquals(false, sheet)
    }
}
