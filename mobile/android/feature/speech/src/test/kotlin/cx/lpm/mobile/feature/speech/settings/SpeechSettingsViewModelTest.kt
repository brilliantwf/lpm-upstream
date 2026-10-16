package cx.lpm.mobile.feature.speech.settings

import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.data.prefs.setSpeechVoiceId
import cx.lpm.mobile.core.data.prefs.speechPrefs
import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.core.testing.keepCollecting
import cx.lpm.mobile.feature.speech.SpeechFixture
import cx.lpm.mobile.feature.speech.engine.SpeechVoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun SpeechFixture.viewModel() =
        SpeechSettingsViewModel(prefs, store, engine, ThisDevice("Pixel", noun = "phone"))

    @Test
    fun `opening the page lists the engine's voices with the saved choices`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechVoiceId("b")
        val vm = f.viewModel()
        keepCollecting(vm.uiState)

        f.engine.voices.value =
            listOf(SpeechVoice("a", "A", SpeechVoice.Quality.Enhanced), SpeechVoice("b", "B"))

        assertEquals(1, f.engine.prepared)
        val state = vm.uiState.value
        assertEquals("b", state.prefs.voiceId)
        assertEquals(listOf("a", "b"), state.voices.map { it.id })
        assertTrue(state.usesDevice)
        assertFalse(state.onlyBasicVoices)
        assertEquals("phone", state.deviceNoun)
    }

    @Test
    fun `only basic voices is said when the best one has no quality`() = runTest {
        val f = SpeechFixture(this)
        val vm = f.viewModel()
        keepCollecting(vm.uiState)

        f.engine.voices.value = listOf(SpeechVoice("a", "A"))

        assertTrue(vm.uiState.value.onlyBasicVoices)
    }

    @Test
    fun `choosing a voice or a speed saves it and plays the sample`() = runTest {
        val f = SpeechFixture(this)
        val vm = f.viewModel()

        vm.selectVoice("b")
        assertEquals("b", f.prefs.speechPrefs.voiceId)
        assertEquals("b", f.engine.queue.single().voiceId)

        vm.setRate(1.5)
        assertEquals(1.5, f.prefs.speechPrefs.rate, 0.0)
        assertEquals(1.5f, f.engine.queue.single().rate)
    }

    @Test
    fun `picking what's already chosen plays nothing`() = runTest {
        val f = SpeechFixture(this)
        val vm = f.viewModel()

        vm.selectVoice("")
        vm.setRate(SpeechPrefs.DEFAULT_RATE)

        assertTrue(f.engine.queue.isEmpty())
    }

    @Test
    fun `the Mac engine is saved and hides the voices`() = runTest {
        val f = SpeechFixture(this)
        val vm = f.viewModel()
        keepCollecting(vm.uiState)

        vm.setEngine(SpeechPrefs.ENGINE_MAC)

        assertEquals(SpeechPrefs.ENGINE_MAC, f.prefs.speechPrefs.engine)
        assertFalse(vm.uiState.value.usesDevice)
    }
}
