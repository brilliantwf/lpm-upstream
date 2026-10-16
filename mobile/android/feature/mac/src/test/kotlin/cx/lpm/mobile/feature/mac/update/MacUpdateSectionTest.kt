package cx.lpm.mobile.feature.mac.update

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.mac.data.MacUpdateLookup
import cx.lpm.mobile.feature.mac.data.MacUpdatePhase
import cx.lpm.mobile.feature.mac.data.MacUpdateState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MacUpdateSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val upToDate = MacUpdateState(version = "0.6.10", canUpdate = true)

    private fun show(update: MacUpdateState, ready: Boolean = true) = compose.setContent {
        LpmTheme {
            Column {
                MacUpdateSection(
                    state = MacUpdateUiState(update, ready = ready, macLabel = "Studio Mac"),
                    actions = MacUpdateBarActions(
                        install = { events += "install" },
                        cancel = { events += "cancel" }
                    )
                )
            }
        }
    }

    @Test
    fun anUpToDateMacSaysSoWithTheRestartWarning() {
        show(upToDate)
        compose.onNodeWithText("lpm").assertExists()
        compose.onNodeWithText("Version").assertExists()
        compose.onNodeWithText("0.6.10").assertExists()
        compose.onNodeWithText("Status").assertExists()
        compose.onNodeWithText("Up to date").assertExists()
        compose.onNodeWithText(
            "Updating restarts lpm there, which stops agents mid-task. Running services keep going."
        ).assertExists()
    }

    @Test
    fun aMacThatCantUpdateOnlyShowsItsVersion() {
        show(upToDate.copy(canUpdate = false))
        compose.onNodeWithText("0.6.10").assertExists()
        compose.onNodeWithText("Status").assertDoesNotExist()
        compose.onNodeWithText(
            "Updating restarts lpm there, which stops agents mid-task. Running services keep going."
        ).assertDoesNotExist()
    }

    @Test
    fun noVersionNoSection() {
        show(MacUpdateState())
        compose.onNodeWithText("Version").assertDoesNotExist()
    }

    @Test
    fun aNewerReleaseIsStillOfferedAfterTheBarWasClosed() {
        show(upToDate.copy(newer = "0.6.12", dismissed = "0.6.12"))
        compose.onNodeWithText("Update to 0.6.12").performClick()
        assertEquals(listOf("install"), events)
    }

    @Test
    fun aDownloadCountsItsPercentAndCanBeCancelled() {
        show(upToDate.copy(newer = "0.6.12", phase = MacUpdatePhase.Downloading(42)))
        compose.onNodeWithText("Downloading · 42%").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun theRestartAndTheLandingRead() {
        show(upToDate.copy(phase = MacUpdatePhase.Updated("0.6.12")))
        compose.onNodeWithText("Updated to 0.6.12").assertExists()
    }

    @Test
    fun aFailureShowsUnderTheStatus() {
        show(upToDate.copy(newer = "0.6.12", error = "Another install is running."))
        compose.onNodeWithText("Another install is running.").assertExists()
        compose.onNodeWithText("Update to 0.6.12").assertExists()
    }

    @Test
    fun theMenuChecksOnlyWhileItCan() {
        compose.setContent {
            LpmTheme {
                ContextMenu(expanded = true, onDismissRequest = {}) {
                    MacUpdateMenuItem(
                        MacUpdateUiState(upToDate, ready = true),
                        onCheck = { events += "check" }
                    )
                    MacUpdateMenuItem(
                        MacUpdateUiState(
                            upToDate.copy(lookup = MacUpdateLookup.Checking),
                            ready = true
                        ),
                        onCheck = { events += "busy" }
                    )
                    MacUpdateMenuItem(
                        MacUpdateUiState(upToDate.copy(canUpdate = false), ready = true),
                        onCheck = { events += "never" }
                    )
                }
            }
        }
        val rows = compose.onAllNodesWithText("Check for lpm Updates…")
        rows.assertCountEquals(2)
        rows[1].assertIsNotEnabled()
        rows[0].performClick()
        assertEquals(listOf("check"), events)
    }
}
