package cx.lpm.mobile.feature.speech.playback

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the media controls show; null while nothing is being read. */
data class NowPlayingInfo(
    val title: String,
    val subtitle: String,
    val playing: Boolean,
    val seekable: Boolean = false
)

/**
 * [NowPlaying] as [SpeechPlaybackService]: started with the first reading, it shows
 * [info] in its media session and notification and stops itself once it's null.
 */
@Singleton
class ServiceNowPlaying @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NowPlaying {
    private val mutableInfo = MutableStateFlow<NowPlayingInfo?>(null)
    val info: StateFlow<NowPlayingInfo?> = mutableInfo.asStateFlow()

    override fun update(title: String, subtitle: String, playing: Boolean, seekable: Boolean) {
        val starting = mutableInfo.value == null
        mutableInfo.value = NowPlayingInfo(title, subtitle, playing, seekable)
        if (!starting) return
        // Reading starts from a tap in the app, so the service may start in the
        // foreground; if the system refuses, reading goes on without the controls.
        runCatching {
            context.startForegroundService(Intent(context, SpeechPlaybackService::class.java))
        }
    }

    override fun clear() {
        mutableInfo.value = null
    }
}
