package cx.lpm.mobile.core.designsystem.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchFieldTest {
    @get:Rule
    val compose = createComposeRule()

    private var searches = 0

    private fun show() {
        compose.setContent {
            LpmTheme {
                SearchField(query = "work", onQueryChange = {
                }, placeholder = "Search", onSearch = { searches++ })
            }
        }
        compose.onNode(hasSetTextAction()).performClick()
    }

    @Test
    fun keyboardSearchKeySubmits() {
        show()
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(1, searches)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hardwareReturnSubmitsToo() {
        show()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, searches)
    }
}
