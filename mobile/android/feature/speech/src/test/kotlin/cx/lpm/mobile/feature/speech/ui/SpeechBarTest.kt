package cx.lpm.mobile.feature.speech.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.data.SpeechState
import cx.lpm.mobile.feature.speech.data.SpeechState.Mode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class SpeechBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: SpeechState) = compose.setContent {
        var current by remember { mutableStateOf(state) }
        LpmTheme {
            SpeechBar(
                state = current,
                onTogglePause = {
                    events += "toggle"
                    val next = if (current.mode == Mode.Paused) Mode.Speaking else Mode.Paused
                    current = current.copy(mode = next)
                },
                onSkip = { events += "skip $it" },
                onSeek = { events += "seek $it" },
                onRateChange = {
                    events += "rate $it"
                    current = current.copy(rate = it)
                },
                onStop = { events += "stop" }
            )
        }
    }

    @Test
    fun theVoiceSkipsBySentenceAndShowsHowFarItIs() {
        show(SpeechState(mode = Mode.Speaking, elapsed = 65.0, progress = 0.42))

        compose.onNodeWithText("1:05").assertIsDisplayed()
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous sentence").performClick()
        compose.onNodeWithContentDescription("Next sentence").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Stop reading").performClick()
        assertEquals(listOf("skip -1", "skip 1", "toggle", "toggle", "stop"), events)
    }

    @Test
    fun aClipSkipsBySecondsAndCountsDown() {
        show(SpeechState(mode = Mode.Speaking, elapsed = 10.0, duration = 75.0, progress = 0.13))

        compose.onNodeWithText("0:10").assertIsDisplayed()
        compose.onNodeWithText("-1:05").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back 15 seconds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Forward 15 seconds").performClick()
        compose.onNodeWithContentDescription("Playback position").assertIsDisplayed()
        assertEquals(listOf("skip 1"), events)
    }

    @Test
    fun whileTheMacRendersTheBarSaysSoAndOnlyStopWorks() {
        show(SpeechState(mode = Mode.Speaking, isLoading = true))

        compose.onNodeWithText("Preparing audio…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous sentence").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Pause").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Stop reading").performClick()
        assertEquals(listOf("stop"), events)
    }

    @Test
    fun theSpeedMenuListsTheSpeeds() {
        show(SpeechState(mode = Mode.Speaking))

        compose.onNodeWithContentDescription("Speed").performClick()
        compose.onNodeWithText("1.5x").performClick()
        assertEquals(listOf("rate 1.5"), events)
        compose.onNodeWithContentDescription("Speed")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.5x"))
    }
}
