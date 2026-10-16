package cx.lpm.mobile.feature.config.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ProfileFormTest {
    @get:Rule
    val compose = createComposeRule()

    private var latest = ProfileEditorUiState()
    private var deletes = 0

    private fun show(initial: ProfileEditorUiState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            latest = state
            LpmTheme {
                Column {
                    ProfileForm(
                        state = state,
                        onNameChange = { state = state.copy(name = it) },
                        onToggle = { s ->
                            val picked = state.selected
                            state = state.copy(
                                selected = if (s in picked) picked - s else picked + s
                            )
                        },
                        onDelete = { deletes++ }
                    )
                }
            }
        }
    }

    @Test
    fun pickingServicesAndANameMakesItSaveable() {
        show(ProfileEditorUiState(services = listOf("web", "api")))
        compose.onNodeWithText("Starting this profile runs exactly these services.").assertExists()
        compose.onNodeWithText("Delete profile").assertDoesNotExist()
        compose.onNodeWithText("Name").performTextInput("frontend")
        compose.onNodeWithText("api").performClick()
        compose.runOnIdle {
            assertEquals("frontend", latest.name)
            assertEquals(listOf("api"), latest.selected)
            assertEquals(true, latest.canSave)
        }
    }

    @Test
    fun aProjectWithNoServicesSaysToAddOne() {
        show(ProfileEditorUiState(editing = true))
        compose.onNodeWithText("Add a service first, then group services into profiles.")
            .assertExists()
        compose.onNodeWithText("Delete profile").performClick()
        assertEquals(1, deletes)
    }
}
