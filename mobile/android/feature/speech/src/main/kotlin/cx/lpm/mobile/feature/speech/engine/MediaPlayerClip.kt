package cx.lpm.mobile.feature.speech.engine

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import java.io.IOException
import javax.inject.Inject

/** [ClipPlayer] over `MediaPlayer`, reading the clip from memory. */
class MediaPlayerClip(private val onFinish: () -> Unit) : ClipPlayer {
    private var player: MediaPlayer? = null
    private var rate = 1f

    override val duration: Double get() = (player?.duration ?: 0).coerceAtLeast(0) / MS
    override val currentTime: Double get() = (player?.currentPosition ?: 0) / MS

    override fun load(audio: ByteArray, rate: Double): Boolean {
        val p = MediaPlayer()
        return try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(BytesSource(audio))
            p.prepare()
            p.setOnCompletionListener { if (player === it) onFinish() }
            this.rate = ClipPlayer.clamped(rate)
            player = p
            true
        } catch (_: IOException) {
            p.release()
            false
        } catch (_: IllegalStateException) {
            p.release()
            false
        }
    }

    override fun play() {
        val p = player ?: return
        p.start()
        applyRate(p)
    }

    override fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    override fun stop() {
        player?.release()
        player = null
    }

    override fun seekTo(seconds: Double) {
        val p = player ?: return
        val end = (p.duration - END_MARGIN_MS).coerceAtLeast(0)
        p.seekTo((seconds * MS).toLong().coerceIn(0, end.toLong()), MediaPlayer.SEEK_CLOSEST)
    }

    override fun setRate(rate: Double) {
        this.rate = ClipPlayer.clamped(rate)
        player?.takeIf { it.isPlaying }?.let(::applyRate)
    }

    // A non-zero speed starts a paused player, so the speed only goes on while playing.
    private fun applyRate(p: MediaPlayer) {
        p.playbackParams = p.playbackParams.setSpeed(rate)
    }

    private class BytesSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }

    class Factory @Inject constructor() : ClipPlayer.Factory {
        override fun create(onFinish: () -> Unit): ClipPlayer = MediaPlayerClip(onFinish)
    }

    private companion object {
        const val MS = 1000.0
        const val END_MARGIN_MS = 50
    }
}
