package cx.lpm.mobile.core.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import cx.lpm.mobile.core.data.push.PushTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Firebase Cloud Messaging's registration token, the Android counterpart of the
 * APNs device token. A build without a Firebase config (no `google-services.json`)
 * never gets one, so no Mac is asked to push to it.
 */
@Singleton
class FcmPushTokens @Inject constructor(@param:ApplicationContext private val context: Context) :
    PushTokenSource {
    private val mutableToken = MutableStateFlow<String?>(null)

    override val platform = "fcm"
    override val token: StateFlow<String?> = mutableToken.asStateFlow()

    private val configured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Register with FCM, which answers with the current token in
     * [LpmMessagingService.onRegistered] — and again whenever the token rotates.
     */
    fun register() {
        if (configured) FirebaseMessaging.getInstance().register()
    }

    fun update(token: String?) {
        mutableToken.value = token?.takeIf { it.isNotEmpty() }
    }

    override fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
