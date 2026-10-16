package cx.lpm.mobile.feature.speech.data

import cx.lpm.mobile.core.data.prefs.SpeechPrefs

/** What the reader is doing (iOS `SpeechStore`'s observable properties). Times are seconds. */
data class SpeechState(
    val mode: Mode = Mode.Idle,
    val title: String = "",
    val subtitle: String = "",
    /** Which message is being read, so its own button can become a stop button. */
    val sourceId: String? = null,
    val elapsed: Double = 0.0,
    val progress: Double = 0.0,
    /** The markdown block being spoken, for the reader's highlight. */
    val speakingBlock: Int? = null,
    /** Surfaced by the reader bar when audio couldn't be produced. */
    val error: SpeechError? = null,
    /** True while the Mac is rendering audio and nothing is audible yet. */
    val isLoading: Boolean = false,
    /** Set while the Mac's clip plays: a real clip has a duration to seek in. */
    val duration: Double = 0.0,
    val rate: Double = SpeechPrefs.DEFAULT_RATE
) {
    enum class Mode { Idle, Speaking, Paused }

    val isActive: Boolean get() = mode != Mode.Idle
    val canSeek: Boolean get() = duration > 0
}

sealed interface SpeechError {
    /** The Mac's own explanation. */
    data class Mac(val message: String) : SpeechError

    /** Nothing could synthesize the text. */
    data object Synthesis : SpeechError

    /** The Mac's audio arrived but couldn't be played. */
    data object Playback : SpeechError
}

/** What tapping a message's read-aloud button did, for its haptic. */
enum class ReadAloudResult { Started, Stopped, NothingToRead }
