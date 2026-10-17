package cx.lpm.mobile.feature.mac.machines

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.data.machines.MachineImportRow
import cx.lpm.mobile.core.data.machines.MachineImportStatus
import cx.lpm.mobile.core.data.machines.MachineSection
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.RemoteMachine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MachinesMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val added = mutableListOf<String>()

    private fun machine(slug: String, name: String, platform: String? = "linux") = RemoteMachine(
        slug = slug,
        name = name,
        platform = platform,
        serverId = null,
        state = RemoteMachine.State.Ready,
        projects = null,
        relay = true
    )

    @Test
    fun theSwitcherListsTheMacsMachinesAndOnlyRetriesWhatTappingHelps() {
        val rows = listOf(
            MachineImportRow(machine("a", "Build Box"), MachineImportStatus.Adding),
            MachineImportRow(
                machine("b", "GPU Box"),
                MachineImportStatus.Failed("It didn't answer in time.")
            ),
            MachineImportRow(machine("c", "Old Box"), MachineImportStatus.Removed),
            MachineImportRow(machine("d", "Mini", platform = "macos"), MachineImportStatus.Offline),
            MachineImportRow(machine("e", "Legacy"), MachineImportStatus.NeedsUpdate),
            MachineImportRow(machine("f", "Lab Box"), MachineImportStatus.Revoked)
        )
        compose.setContent {
            LpmTheme {
                ContextMenu(expanded = true, onDismissRequest = {}) {
                    PendingMachinesMenu("Studio Mac", rows, onAdd = { added += it })
                }
            }
        }
        compose.onNodeWithText("Connected to Studio Mac").assertExists()
        compose.onNode(hasText("Build Box")).assertIsNotEnabled()
        compose.onNodeWithText("Adding…").assertExists()
        compose.onNodeWithText("It didn't answer in time. Tap to try again.").assertExists()
        compose.onNodeWithText("Tap to add").assertExists()
        compose.onNodeWithText("Offline").assertExists()
        compose.onNodeWithText("Update lpm on it to add it here").assertExists()
        compose.onNode(hasText("Mini")).assertIsNotEnabled()
        compose.onNode(hasText("Legacy")).assertIsNotEnabled()
        compose.onNode(hasText("Old Box")).assertIsEnabled()
        compose.onNodeWithText("Removed this phone. Pair on it to add it.").assertExists()
        compose.onNode(hasText("Lab Box")).assertIsEnabled()
        compose.onNode(hasText("GPU Box")).performClick()
        assertEquals(listOf("b"), added)
    }

    @Test
    fun noPendingMachinesNoSection() {
        compose.setContent {
            LpmTheme {
                ContextMenu(expanded = true, onDismissRequest = {}) {
                    PendingMachinesMenu(null, emptyList(), onAdd = {})
                }
            }
        }
        compose.onNodeWithText("Connected to this Mac").assertDoesNotExist()
    }

    @Test
    fun aMachineOpenedThroughTheMacCanBePairedDirectly() {
        val sections = listOf(
            MachineSection(
                machine("a", "Build Box"),
                emptyList(),
                null,
                MachineImportStatus.Removed,
                true
            ),
            MachineSection(
                machine("b", "GPU Box"),
                emptyList(),
                null,
                MachineImportStatus.Failed("Its identity didn't match. Try again."),
                true
            ),
            MachineSection(
                machine("c", "Lab Box"),
                emptyList(),
                null,
                MachineImportStatus.Revoked,
                true
            )
        )
        compose.setContent {
            LpmTheme {
                ContextMenu(expanded = true, onDismissRequest = {}) {
                    PairDirectlyMenuItems(sections, onPair = { added += it })
                }
            }
        }
        compose.onNodeWithText(
            "Get its notifications and open it while this Mac sleeps"
        ).assertExists()
        compose.onNodeWithText("Its identity didn't match. Try again.").assertExists()
        compose.onNodeWithText("It removed this phone. Pair on it to add it again.").assertExists()
        compose.onNodeWithText("Pair Directly with Build Box").performClick()
        assertEquals(listOf("a"), added)
    }
}
