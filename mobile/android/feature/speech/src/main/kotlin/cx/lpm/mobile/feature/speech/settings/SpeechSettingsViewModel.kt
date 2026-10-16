package cx.lpm.mobile.feature.speech.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.data.prefs.setSpeechEngine
import cx.lpm.mobile.core.data.prefs.setSpeechRate
import cx.lpm.mobile.core.data.prefs.setSpeechVoiceId
import cx.lpm.mobile.core.data.prefs.speechPrefs
import cx.lpm.mobile.core.data.prefs.speechPrefsFlow
import cx.lpm.mobile.feature.speech.data.SpeechStore
import cx.lpm.mobile.feature.speech.engine.SpeechEngine
import cx.lpm.mobile.feature.speech.engine.SpeechVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SpeechSettingsUiState(
    val prefs: SpeechPrefs = SpeechPrefs(),
    val voices: List<SpeechVoice> = emptyList(),
    /** What copy calls this device ("This phone"). */
    val deviceNoun: String = "phone"
) {
    val usesDevice: Boolean get() = prefs.engine != SpeechPrefs.ENGINE_MAC

    /**
     * Every installed voice is a basic one. The better voices are a system download
     * this app can't trigger, so all it can do is say so.
     */
    val onlyBasicVoices: Boolean
        get() = voices.firstOrNull()?.let { it.quality == SpeechVoice.Quality.Default } ?: false
}

/**
 * Settings → Read aloud (iOS `SpeechSettingsView`): the engine, the speed, the
 * installed voices and a sample. Choosing a voice or a speed plays the sample at once —
 * a list of names says nothing about how any of them sound.
 */
@HiltViewModel
class SpeechSettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val speech: SpeechStore,
    engine: SpeechEngine,
    device: ThisDevice
) : ViewModel() {
    val uiState: StateFlow<SpeechSettingsUiState> = combine(
        prefs.speechPrefsFlow(),
        engine.voices
    ) { speechPrefs, voices -> SpeechSettingsUiState(speechPrefs, voices, device.noun) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SpeechSettingsUiState(prefs.speechPrefs, engine.voices.value, device.noun)
        )

    init {
        engine.prepare()
    }

    fun setEngine(engine: String) = prefs.setSpeechEngine(engine)

    fun setRate(rate: Double) {
        if (rate == prefs.speechPrefs.rate) return
        prefs.setSpeechRate(rate)
        playSample()
    }

    fun selectVoice(voiceId: String) {
        if (voiceId == prefs.speechPrefs.voiceId) return
        prefs.setSpeechVoiceId(voiceId)
        playSample()
    }

    fun playSample() = speech.preview(prefs.speechPrefs.voiceId)
}
