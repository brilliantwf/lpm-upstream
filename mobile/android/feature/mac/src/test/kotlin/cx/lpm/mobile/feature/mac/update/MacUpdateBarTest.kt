package cx.lpm.mobile.feature.mac.update

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.BusyAgentPlace
import cx.lpm.mobile.feature.mac.data.MacUpdateLookup
import cx.lpm.mobile.feature.mac.data.MacUpdatePhase
import cx.lpm.mobile.feature.mac.data.MacUpdateState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MacUpdateBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val offered = MacUpdateState(version = "0.6.10", canUpdate = true, newer = "0.6.12")

    private fun show(
        update: MacUpdateState,
        ready: Boolean = true,
        busy: List<BusyAgentPlace> = emptyList()
    ) = compose.setContent {
        LpmTheme {
            MacUpdateBar(
                state = MacUpdateUiState(update, ready = ready, macLabel = "Studio Mac"),
                actions = MacUpdateBarActions(
                    install = { events += "install" },
                    cancel = { events += "cancel" },
                    dismiss = { events += "dismiss" },
                    busyPlaces = { busy }
                )
            )
        }
    }

    @Test
    fun aNewerReleaseOffersUpdateAndClose() {
        show(offered)
        compose.onNodeWithText("lpm 0.6.12 is available").assertExists()
        compose.onNodeWithText("This Mac runs 0.6.10").assertExists()
        compose.onNodeWithText("Update").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("install", "dismiss"), events)
    }

    @Test
    fun theBarIsAwayWhileTheMacIs() {
        show(offered, ready = false)
        compose.onNodeWithText("lpm 0.6.12 is available").assertDoesNotExist()
    }

    @Test
    fun theRestartShowsEvenWithTheMacAway() {
        show(offered.copy(phase = MacUpdatePhase.Restarting), ready = false)
        compose.onNodeWithText("Restarting lpm…").assertExists()
        compose.onNodeWithText("lpm Link reconnects when it's back").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun aDownloadCanBeCancelledOnce() {
        show(offered.copy(phase = MacUpdatePhase.Downloading(-1)))
        compose.onNodeWithText("Updating to lpm 0.6.12").assertExists()
        compose.onNodeWithText("Downloading…").assertExists()
        compose.onNodeWithText("Cancel").assertIsEnabled().performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun cancellingWaitsGrayedOut() {
        show(offered.copy(phase = MacUpdatePhase.Checking, cancelling = true))
        compose.onNodeWithText("Cancelling…").assertExists()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun installingCantBeStopped() {
        show(offered.copy(phase = MacUpdatePhase.Installing))
        compose.onNodeWithText("Installing lpm 0.6.12…").assertExists()
        compose.onNodeWithText("lpm restarts when it's done").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun theNewReleaseLands() {
        show(
            MacUpdateState(
                version = "0.6.12",
                canUpdate = true,
                phase = MacUpdatePhase.Updated("0.6.12")
            )
        )
        compose.onNodeWithText("Updated to lpm 0.6.12").assertExists()
        compose.onNodeWithText("Restarted and reconnected").assertExists()
    }

    @Test
    fun aFailedInstallOffersTryAgain() {
        show(offered.copy(error = "The update didn't finish on Studio Mac. Try again."))
        compose.onNodeWithText("Couldn't update lpm").assertExists()
        compose.onNodeWithText("The update didn't finish on Studio Mac. Try again.").assertExists()
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(listOf("install"), events)
    }

    @Test
    fun aCheckFromTheMenuAnswersInTheBar() {
        show(offered.copy(newer = null, lookup = MacUpdateLookup.Checking))
        compose.onNodeWithText("Checking for updates…").assertExists()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun upToDateAndFailedChecksCanBeClosed() {
        show(offered.copy(newer = null, lookup = MacUpdateLookup.Failed("GitHub didn't answer.")))
        compose.onNodeWithText("Couldn't check for updates").assertExists()
        compose.onNodeWithText("GitHub didn't answer.").assertExists()
        compose.onNodeWithText("Update").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun busyAgentsAreWarnedAboutFirst() {
        show(
            offered,
            busy = listOf(BusyAgentPlace("web", "Web", 2), BusyAgentPlace("api", "API", 1))
        )
        compose.onNodeWithText("Update").performClick()
        compose.onNodeWithText("Agents are still running").assertExists()
        compose.onNodeWithText(
            "Updating restarts lpm on Studio Mac, which stops 3 agents mid-task in Web and API."
        ).assertExists()
        assertEquals(emptyList<String>(), events)
        compose.onNodeWithText("Update Anyway").performClick()
        assertEquals(listOf("install"), events)
    }

    @Test
    fun oneBusyAgentReadsInTheSingular() {
        show(offered, busy = listOf(BusyAgentPlace("web", "Web", 1)))
        compose.onNodeWithText("Update").performClick()
        compose.onNodeWithText("An agent is still running").assertExists()
        compose.onNodeWithText(
            "Updating restarts lpm on Studio Mac, which stops an agent mid-task in Web."
        ).assertExists()
    }
}
