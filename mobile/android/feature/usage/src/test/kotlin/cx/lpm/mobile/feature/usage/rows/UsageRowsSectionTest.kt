package cx.lpm.mobile.feature.usage.rows

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ClaudeAccountInfo
import cx.lpm.mobile.core.model.ClaudeInUse
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.model.LimitWindow
import cx.lpm.mobile.core.model.LimitsSnapshot
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.ProviderLimits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageRowsSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = 1_792_065_600_000.0
    private val hour = 3_600_000.0
    private val events = mutableListOf<String>()

    private fun window(used: Double, resetsIn: Double) =
        LimitWindow(used, ((now + resetsIn) / 1000).toLong())

    private val snapshot = LimitsSnapshot(
        mapOf(
            "claude:default" to ProviderLimits(
                "claude",
                "default",
                "Sonnet 4.6",
                window(62.0, 3 * hour),
                window(27.0, 96 * hour),
                now.toLong()
            ),
            "claude:work" to ProviderLimits(
                "claude",
                "work",
                "Opus 4.6",
                window(34.0, 2 * hour),
                window(11.0, 106 * hour),
                now.toLong()
            )
        ),
        claudeEnabled = true,
        nowMs = now
    )
    private val accounts = listOf(
        ClaudeAccountInfo("default", "Main login", null, true, "you@example.com", "max", true),
        ClaudeAccountInfo("work", "Work", "Work", true, "you@work.example", "max", true)
    )
    private val storefront = Project.parse(
        Json.parseToJsonElement("""{"name": "storefront"}""").jsonObject
    )

    private fun state(
        inUse: ClaudeInUse?,
        switches: Boolean = false,
        shown: Boolean = true
    ): UsageRowsUiState {
        val rows = usageRows(snapshot, null, accounts, now, now)
        return UsageRowsUiState(
            rows = rows.map {
                UsageRowItem(
                    it,
                    when (it.account) {
                        inUse?.current -> UsageAccountMark.Current
                        null -> null
                        else -> UsageAccountMark.Standby
                    }
                )
            },
            inUse = inUse,
            switches = switches,
            now = now,
            macNow = now,
            shown = shown
        )
    }

    private fun show(state: UsageRowsUiState) = compose.setContent {
        LpmTheme {
            UsageRowsSection(
                state = state,
                onDetails = { events += "details" },
                onSwitch = if (state.switches) ({ events += "switch $it" }) else null
            )
        }
    }

    @Test
    fun tappingARowShowsBothWindowsAndTappingAgainHidesThem() {
        show(state(ClaudeInUse("default", listOf("work"), null, pinned = false)))
        compose.onNodeWithText("USAGE").assertExists()
        compose.onNodeWithContentDescription("Work, pool").assertExists()
        compose.onNodeWithContentDescription("Claude, in use").assertExists()

        compose.onNodeWithContentDescription("Work, pool").performClick()
        compose.onNodeWithText("OPUS 4.6").assertExists()
        compose.onNodeWithText("you@work.example").assertExists()
        compose.onNodeWithText("The main accounts can switch to this account").assertExists()

        compose.onNodeWithContentDescription("Work, pool").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("OPUS 4.6").assertDoesNotExist()
    }

    @Test
    fun theRowsStayHiddenUntilTheListAboveHasLoaded() {
        show(state(ClaudeInUse("default", listOf("work"), null, pinned = false), shown = false))
        compose.onNodeWithContentDescription("Work, pool").assertDoesNotExist()
    }

    @Test
    fun detailsOpensTheUsageScreen() {
        show(state(ClaudeInUse("default", listOf("work"), null, pinned = false)))
        compose.onNodeWithText("Details").performClick()
        assertEquals(listOf("details"), events)
    }

    @Test
    fun onAProjectAnExpandedAccountSwitchesIt() {
        val inUse = ClaudeInUse("default", listOf("work"), storefront, pinned = false)
        show(state(inUse, switches = true))
        compose.onNodeWithContentDescription("Work, pool").performClick()
        compose.onNodeWithText("New sessions in storefront").assertExists()
        compose.onNodeWithText("Switch to Work").performClick()
        compose.onNodeWithContentDescription("Work, pool").performClick()

        compose.onNodeWithContentDescription("Claude, in use").performClick()
        compose.onNodeWithText("Always use Claude").performClick()
        assertEquals(listOf("switch work", "switch default"), events)
    }

    @Test
    fun aLongPressOffersTheSwitchAndAPinnedAccountCantBePickedAgain() {
        val pinned =
            ClaudeInUse("work", emptyList(), storefront.withClaude(ClaudeOwnChoice("work")), true)
        show(state(pinned, switches = true))
        compose.onNodeWithContentDescription("Work, in use").performTouchInput { longClick() }
        compose.onNodeWithText("Switch to Work").assertExists()
        compose.onNodeWithText("Switch to Work").performClick()
        assertEquals(emptyList<String>(), events)
    }
}
