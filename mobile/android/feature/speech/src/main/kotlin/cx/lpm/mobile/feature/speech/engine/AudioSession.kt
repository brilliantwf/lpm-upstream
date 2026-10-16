package cx.lpm.mobile.feature.speech.engine

/**
 * The phone's audio for reading (iOS `AVAudioSession` in `.playback` / `.spokenAudio`):
 * held only while reading, so merely opening the app never interrupts the user's
 * music, and other audio pauses rather than ducking under the voice.
 */
interface AudioSession {
    var listener: Listener?

    fun activate()

    /** Hands the audio back to whatever was playing. */
    fun deactivate()

    interface Listener {
        /** A call, an alarm or another app took the audio. */
        fun onInterruptionBegan()

        /** The audio came back after a short interruption, so reading picks up again. */
        fun onInterruptionEnded()

        /** Headphones pulled out: pause instead of blasting the result from the speaker. */
        fun onRouteLost()
    }
}
