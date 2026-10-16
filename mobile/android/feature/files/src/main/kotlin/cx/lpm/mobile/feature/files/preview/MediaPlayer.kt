package cx.lpm.mobile.feature.files.preview

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Video and audio in the platform player (iOS `MediaPlayerView`, AVKit): playing
 * at once, with scrubbing and its controls; it pauses when the app leaves the
 * screen and lets go of the audio when the sheet closes.
 */
@Composable
internal fun MediaPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose(player::release) }
    LifecycleStartEffect(player) { onStopOrDispose { player.pause() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = modifier,
        onRelease = { it.player = null }
    )
}
