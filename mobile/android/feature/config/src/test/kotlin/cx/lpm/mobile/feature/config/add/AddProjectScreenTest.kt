package cx.lpm.mobile.feature.config.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SshHostInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class AddProjectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val hosts = listOf(SshHostInfo("staging", "staging.internal", "deploy", 22, ""))

    private fun show(initial: AddProjectUiState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            LpmTheme {
                AddProjectScreen(
                    state = state,
                    actions = AddProjectActions(
                        onMode = { state = state.copy(mode = it) },
                        onBrowse = { events += "browse $it" },
                        onCloneUrl = {},
                        onBranch = {},
                        onHostPick = {
                            state = state.copy(draft = state.draft.pickingHost(it, hosts))
                        },
                        onHost = {},
                        onUser = {},
                        onPort = {},
                        onIdentityFile = {},
                        onRemoteDir = {},
                        onName = {}
                    ),
                    onCancel = { events += "cancel" },
                    onCreate = { events += "create" }
                )
            }
        }
    }

    @Test
    fun eachModeShowsItsFieldsAndItsButton() {
        show(AddProjectUiState())
        compose.onNodeWithText("Choose…").assertExists()
        compose.onNode(hasText("Create") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Folder on your Mac").performClick()
        compose.onNodeWithText("Clone").performClick()
        compose.onNodeWithText("Repository address").assertExists()
        compose.onNodeWithText("Branch (optional)").assertExists()
        compose.onNodeWithText("Clone into").performClick()
        compose.onNodeWithText("SSH").performClick()
        compose.onNodeWithText("Add").assertExists()
        assertEquals(listOf("browse ProjectFolder", "browse CloneInto"), events)
    }

    @Test
    fun aSavedHostOpensTheConnectionFields() {
        show(
            AddProjectUiState(
                mode = AddProjectMode.Ssh,
                sshHosts = hosts,
                sshHostsLoaded = true
            )
        )
        compose.onNodeWithText("Pick a saved host to fill in its details, or enter a new one.")
            .assertExists()
        compose.onNodeWithText("Host").assertDoesNotExist()
        compose.onNodeWithText("Connect to host").performClick()
        compose.onNodeWithText("staging — deploy").performClick()
        compose.onNodeWithText("deploy-staging").assertExists()
        compose.onNodeWithText("Connection").assertExists()
    }

    @Test
    fun aCompleteFormCanBeCreated() {
        show(
            AddProjectUiState(
                draft = AddProjectDraft(localRoot = "/Users/demo/site", name = "site")
            )
        )
        compose.onNodeWithText("/Users/demo/site").assertExists()
        compose.onNode(hasText("Create") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf("create"), events)
    }
}
