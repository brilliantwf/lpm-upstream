package cx.lpm.mobile.core.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Phone-local read-aloud preferences (iOS `SpeechPrefs`): which engine speaks, which
 * installed voice and how fast. Nothing here syncs to the Mac — the voices are
 * whatever this phone has, so the choice only makes sense locally.
 */
data class SpeechPrefs(
    /** [ENGINE_DEVICE] synthesizes on the phone; [ENGINE_MAC] asks the paired Mac for audio. */
    val engine: String = ENGINE_DEVICE,
    /** An installed voice, or empty for the best one available. */
    val voiceId: String = "",
    val rate: Double = DEFAULT_RATE
) {
    companion object {
        const val ENGINE_DEVICE = "device"
        const val ENGINE_MAC = "mac"
        const val DEFAULT_RATE = 1.0
        val RATES = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
    }
}

/** A missing or non-positive rate reads as the default, as `UserDefaults.double` returns 0. */
val AppPreferences.speechPrefs: SpeechPrefs
    get() = SpeechPrefs(
        engine = string(PreferenceKeys.SPEECH_ENGINE) ?: SpeechPrefs.ENGINE_DEVICE,
        voiceId = string(PreferenceKeys.SPEECH_VOICE_ID).orEmpty(),
        rate = double(PreferenceKeys.SPEECH_RATE)?.takeIf { it > 0 } ?: SpeechPrefs.DEFAULT_RATE
    )

fun AppPreferences.speechPrefsFlow(): Flow<SpeechPrefs> =
    data.map { speechPrefs }.distinctUntilChanged()

fun AppPreferences.setSpeechEngine(engine: String) = setString(PreferenceKeys.SPEECH_ENGINE, engine)

fun AppPreferences.setSpeechVoiceId(voiceId: String) =
    setString(PreferenceKeys.SPEECH_VOICE_ID, voiceId)

fun AppPreferences.setSpeechRate(rate: Double) = setDouble(PreferenceKeys.SPEECH_RATE, rate)
