package cx.lpm.mobile.feature.speech.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.engine.SpeechVoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class SpeechSettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: SpeechSettingsUiState) = compose.setContent {
        LpmTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SpeechSettingsContent(
                    state = state,
                    onEngineChange = { events += "engine $it" },
                    onRateChange = { events += "rate $it" },
                    onVoiceSelect = { events += "voice $it" },
                    onPlaySample = { events += "sample" }
                )
            }
        }
    }

    @Test
    fun thePhonesVoicesAreListedUnderTheEngineAndSpeed() {
        show(
            SpeechSettingsUiState(
                voices = listOf(
                    SpeechVoice("a", "English (US) · SFG", SpeechVoice.Quality.Enhanced),
                    SpeechVoice("b", "English (United Kingdom) · GBA")
                )
            )
        )

        compose.onNodeWithText("Engine").assertIsDisplayed()
        compose.onNodeWithText("This phone").assertIsDisplayed()
        compose.onNodeWithText(
            "Speech is generated on this phone. Works offline and nothing leaves the device."
        )
            .assertIsDisplayed()
        compose.onNodeWithText("Speed").assertIsDisplayed()
        compose.onNodeWithText("1x").assertIsDisplayed()
        compose.onNodeWithText(
            "Voice and speed for reading automation replies out loud."
        ).assertIsDisplayed()
        compose.onNodeWithText("Voice").assertIsDisplayed()
        compose.onNodeWithText("Best available").assertIsDisplayed()
        compose.onNodeWithText("Enhanced").assertIsDisplayed()
        compose.onNodeWithText("Hear a sample").performClick()
        compose.onNodeWithText("English (United Kingdom) · GBA").performClick()
        compose.onNodeWithText("1x").performClick()
        compose.onNodeWithText("2x").performClick()
        assertEquals(listOf("sample", "voice b", "rate 2.0"), events)
    }

    @Test
    fun theMacEngineHidesThePhonesVoices() {
        show(SpeechSettingsUiState(prefs = SpeechPrefs(engine = SpeechPrefs.ENGINE_MAC)))

        compose.onNodeWithText("Mac").assertIsDisplayed()
        compose.onNodeWithText(
            "Your Mac synthesizes the speech with OpenAI and sends the audio over. Better " +
                "voice, and you can scrub through it — but it needs your Mac, and the text is " +
                "sent to OpenAI."
        ).assertIsDisplayed()
        compose.onNodeWithText("Best available").assertDoesNotExist()
    }

    @Test
    fun onlyBasicVoicesPointsToTheSystemDownload() {
        show(SpeechSettingsUiState(voices = listOf(SpeechVoice("a", "Basic"))))

        compose.onNodeWithText(
            "Your installed voices are all basic ones. Download a Premium voice in Settings → " +
                "Accessibility → Text-to-speech output for much more natural speech."
        ).assertIsDisplayed()
    }
}
