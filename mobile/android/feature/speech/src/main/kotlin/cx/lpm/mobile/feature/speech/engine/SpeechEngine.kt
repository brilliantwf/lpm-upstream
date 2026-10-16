package cx.lpm.mobile.feature.speech.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's own voice (iOS `AVSpeechSynthesizer`): a queue of utterances spoken
 * back to back, reporting each one's start, the word being spoken and its end on the
 * main thread. Android's engine has no pause, so the store stops and re-queues.
 */
interface SpeechEngine {
    /** The installed voices for the phone's language, best first ([SpeechVoices]). */
    val voices: StateFlow<List<SpeechVoice>>

    var listener: Listener?

    /** Connect to the engine and list its voices; speaking prepares it too. */
    fun prepare()

    /** Replace whatever is queued with [utterances]. */
    fun speak(utterances: List<Utterance>)

    fun stop()

    interface Listener {
        fun onStart(utteranceId: String)

        /** The word at `[start, end)` of the utterance's text is being spoken. */
        fun onRange(utteranceId: String, start: Int, end: Int)

        /** Spoken to the end, or failed: either way the queue moves on. */
        fun onDone(utteranceId: String)

        /** The engine can't speak at all (none installed, or it failed to start). */
        fun onUnavailable()
    }
}

/** One thing to say: [voiceId] empty means the best available, [pauseAfterMs] the silence after. */
data class Utterance(
    val id: String,
    val text: String,
    val voiceId: String,
    val rate: Float,
    val pauseAfterMs: Long = 0
)

/** A voice the engine can speak with on this phone. */
data class SpeechVoice(
    val id: String,
    val name: String,
    val quality: Quality = Quality.Default,
    /** BCP 47, for ordering the phone's own region first. */
    val languageTag: String = "",
    val isEngineDefault: Boolean = false
) {
    enum class Quality { Default, Enhanced, Premium }
}
