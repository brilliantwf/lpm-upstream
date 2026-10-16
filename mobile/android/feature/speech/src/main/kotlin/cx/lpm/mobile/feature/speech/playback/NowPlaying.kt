package cx.lpm.mobile.feature.speech.playback

/**
 * The system's media controls for the reading (iOS Now Playing): the title and
 * project on the lock screen and in the notification shade, playing or paused. No
 * duration is knowable up front, so none is shown, as iOS shows a live stream.
 * [seekable] is the Mac's clip, whose skips move by 15 seconds instead of a sentence.
 */
interface NowPlaying {
    fun update(title: String, subtitle: String, playing: Boolean, seekable: Boolean)

    fun clear()
}
