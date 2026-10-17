package cx.lpm.mobile.core.push

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Where FCM delivers this phone's token and its data messages. The token comes to
 * [onRegistered], which replaced the deprecated `onNewToken` that lint still asks for.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
@AndroidEntryPoint
class LpmMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var tokens: FcmPushTokens

    @Inject
    lateinit var notifier: PushNotifier

    override fun onRegistered(token: String) = tokens.update(token)

    override fun onMessageReceived(message: RemoteMessage) = notifier.handle(message.data)
}
