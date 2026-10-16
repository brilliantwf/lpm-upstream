package cx.lpm.mobile.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.data.prefs.NotificationPrefs
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.AppearanceMode
import cx.lpm.mobile.core.ui.NotificationAccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class SettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    @Test
    fun settingsListTheSectionsAndOpenTheirPages() {
        compose.setContent {
            var appearance by remember { mutableStateOf(AppearanceMode.System) }
            LpmTheme {
                SettingsContent(
                    state = SettingsUiState(appearance, TailscaleSummary.Off),
                    onAppearanceChange = {
                        appearance = it
                        events += "theme ${it.rawValue}"
                    },
                    onOpen = { events += "open $it" }
                )
            }
        }
        compose.onNodeWithText("Away from home").assertIsDisplayed()
        compose.onNodeWithText("Off").assertIsDisplayed()
        compose.onNodeWithText("Font size and color scheme for the built-in terminal.")
            .assertIsDisplayed()
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Built-in Tailscale").performClick()
        compose.onNodeWithText("Read aloud").performClick()
        compose.onNodeWithText("Notifications").performClick()
        assertEquals(
            listOf("theme dark", "open Tailscale", "open ReadAloud", "open Notifications"),
            events
        )
    }

    private fun showNotifications(
        prefs: NotificationPrefs = NotificationPrefs(),
        connected: Boolean = true,
        access: NotificationAccess = NotificationAccess.Allowed
    ) = compose.setContent {
        var current by remember { mutableStateOf(prefs) }
        LpmTheme {
            // Scrolling, as on its page, so rows take their own height.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NotificationSettingsContent(
                    state = NotificationSettingsUiState(current, connected),
                    access = access,
                    onAllow = { events += "allow" },
                    onOpenSettings = { events += "settings" },
                    onChange = { current = it(current) }
                )
            }
        }
    }

    @Test
    fun theMasterSwitchDimsTheKindsButKeepsThem() {
        showNotifications()
        compose.onNodeWithContentDescription("Started").assertIsOff()
        compose.onNodeWithContentDescription("Waiting for you").assertIsOn()
        compose.onNodeWithContentDescription("Push notifications").performClick()
        compose.onNodeWithContentDescription("Push notifications").assertIsOff()
        compose.onNodeWithContentDescription("Waiting for you").assertIsOn().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("Finished").onFirst().assertIsNotEnabled()
    }

    @Test
    fun aPhoneThatWasNeverAskedOffersThePrompt() {
        showNotifications(connected = false, access = NotificationAccess.NotDetermined)
        compose.onNodeWithText(
            "Not connected to your Mac — changes apply the next time this phone connects."
        ).assertIsDisplayed()
        compose.onNodeWithText("Allow notifications").performClick()
        assertEquals(listOf("allow"), events)
    }

    @Test
    fun aRefusedPermissionPointsAtSystemSettings() {
        showNotifications(access = NotificationAccess.Denied)
        compose.onNodeWithText("Notifications are turned off for lpm in your device settings.")
            .assertIsDisplayed()
        compose.onNodeWithText("Open Settings").performClick()
        assertEquals(listOf("settings"), events)
    }
}
