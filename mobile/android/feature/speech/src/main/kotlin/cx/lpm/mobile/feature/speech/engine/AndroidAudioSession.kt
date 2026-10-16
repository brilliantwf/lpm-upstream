package cx.lpm.mobile.feature.speech.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [AudioSession] as audio focus plus the "becoming noisy" broadcast. */
@Singleton
class AndroidAudioSession @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AudioSession {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var resumeOnGain = false

    override var listener: AudioSession.Listener? = null

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) listener?.onRouteLost()
        }
    }

    override fun activate() {
        if (request != null) return
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
            .build()
        request = focus
        resumeOnGain = false
        audio.requestAudioFocus(focus)
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(noisy, filter)
        }
    }

    override fun deactivate() {
        val focus = request ?: return
        request = null
        audio.abandonAudioFocusRequest(focus)
        context.unregisterReceiver(noisy)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnGain = false
                listener?.onInterruptionBegan()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnGain = true
                listener?.onInterruptionBegan()
            }

            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) {
                resumeOnGain = false
                listener?.onInterruptionEnded()
            }
        }
    }
}
