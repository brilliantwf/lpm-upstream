package cx.lpm.mobile.feature.speech.engine

/**
 * Playback for pre-rendered audio (the Mac's OpenAI engine, iOS `SpeechClip`), as
 * opposed to the on-device voice. A real file with a known duration supports what a
 * synthesizer can't: seeking to any time. Times are in seconds.
 */
interface ClipPlayer {
    val duration: Double
    val currentTime: Double

    /** False when [audio] can't be played. The speed is applied before playback starts. */
    fun load(audio: ByteArray, rate: Double): Boolean

    fun play()

    fun pause()

    fun stop()

    /** Clamped, so a seek past the end doesn't silently stop playback. */
    fun seekTo(seconds: Double)

    fun seekBy(delta: Double) = seekTo(currentTime + delta)

    /**
     * The phone's speed preference: the Mac renders at its own configured speed, so
     * this is what makes the bar's control mean anything on that engine.
     */
    fun setRate(rate: Double)

    fun interface Factory {
        fun create(onFinish: () -> Unit): ClipPlayer
    }

    companion object {
        /** The range iOS's player guarantees, kept so a speed sounds the same on both. */
        fun clamped(rate: Double): Float = rate.coerceIn(0.5, 2.0).toFloat()
    }
}
