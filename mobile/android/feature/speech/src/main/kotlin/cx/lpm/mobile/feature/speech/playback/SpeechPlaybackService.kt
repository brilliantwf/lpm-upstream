package cx.lpm.mobile.feature.speech.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.feature.speech.R
import cx.lpm.mobile.feature.speech.data.SpeechStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps reading going with the screen off and puts it on the lock screen and in the
 * notification shade (iOS's `.playback` audio session, Now Playing and remote
 * commands): a media-playback foreground service with a media session whose play,
 * pause, skip and stop drive the [SpeechStore].
 */
@AndroidEntryPoint
class SpeechPlaybackService : Service() {
    @Inject lateinit var nowPlaying: ServiceNowPlaying

    @Inject lateinit var speech: SpeechStore

    private val scope = MainScope()
    private lateinit var session: MediaSession
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.speech_title),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        session = MediaSession(this, TAG).apply {
            setCallback(commands)
            isActive = true
        }
        scope.launch { nowPlaying.info.collect(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_TOGGLE -> speech.togglePause()
            ACTION_PREVIOUS -> speech.skip(-1)
            ACTION_NEXT -> speech.skip(1)
            ACTION_STOP -> speech.stop()
        }
        val info = nowPlaying.info.value
        startForegroundCompat(notification(info ?: NowPlayingInfo("", "", playing = false)))
        if (info == null) finish()
        return START_NOT_STICKY
    }

    /** Swiping the app away ends the reading, as quitting it does on iOS. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        speech.stop()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun render(info: NowPlayingInfo?) {
        if (info == null) {
            finish()
            return
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, info.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, info.subtitle)
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(
                    if (info.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (info.playing) 1f else 0f
                )
                .build()
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(info))
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(info: NowPlayingInfo): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(DesignR.drawable.ic_volume_up)
            .setContentTitle(info.title)
            .setContentText(info.subtitle)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                action(
                    DesignR.drawable.ic_skip_previous_fill,
                    if (info.seekable) R.string.speech_back_15 else PREVIOUS_SENTENCE,
                    ACTION_PREVIOUS
                )
            )
            .addAction(
                if (info.playing) {
                    action(DesignR.drawable.ic_pause_fill, R.string.speech_pause, ACTION_TOGGLE)
                } else {
                    action(DesignR.drawable.ic_play_arrow_fill, R.string.speech_play, ACTION_TOGGLE)
                }
            )
            .addAction(
                action(
                    DesignR.drawable.ic_skip_next_fill,
                    if (info.seekable) R.string.speech_forward_15 else NEXT_SENTENCE,
                    ACTION_NEXT
                )
            )
            .addAction(action(DesignR.drawable.ic_close, R.string.speech_stop_reading, ACTION_STOP))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun action(
        @DrawableRes icon: Int,
        @StringRes label: Int,
        action: String
    ): Notification.Action {
        val intent = Intent(this, SpeechPlaybackService::class.java).setAction(action)
        val pending =
            PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
        val image = Icon.createWithResource(this, icon)
        return Notification.Action.Builder(image, getString(label), pending).build()
    }

    /** The lock screen's and headphones' controls (iOS `MPRemoteCommandCenter`). */
    private val commands = object : MediaSession.Callback() {
        override fun onPlay() = speech.resume()

        override fun onPause() = speech.pause()

        override fun onStop() = speech.stop()

        override fun onSkipToNext() = speech.skip(1)

        override fun onSkipToPrevious() = speech.skip(-1)
    }

    private companion object {
        val PREVIOUS_SENTENCE = R.string.speech_previous_sentence
        val NEXT_SENTENCE = R.string.speech_next_sentence
        const val TAG = "lpm-speech"
        const val CHANNEL = "read-aloud"
        const val NOTIFICATION_ID = 0x5EEC
        const val ACTION_TOGGLE = "cx.lpm.mobile.speech.TOGGLE"
        const val ACTION_PREVIOUS = "cx.lpm.mobile.speech.PREVIOUS"
        const val ACTION_NEXT = "cx.lpm.mobile.speech.NEXT"
        const val ACTION_STOP = "cx.lpm.mobile.speech.STOP"
        const val ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    }
}
