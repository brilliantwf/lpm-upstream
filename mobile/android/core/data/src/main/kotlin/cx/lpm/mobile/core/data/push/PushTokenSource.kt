package cx.lpm.mobile.core.data.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * This phone's push channel as the data layer sees it. `:core:push` binds Firebase
 * Cloud Messaging; without it (or in a build with no Firebase config) there is no
 * token, and no Mac is asked to push.
 */
interface PushTokenSource {
    /** The `platform` a Mac stores beside the token. */
    val platform: String

    /** The current registration token; null until the push service hands one out. */
    val token: StateFlow<String?>

    /** Whether the phone lets lpm Link post notifications at all. */
    fun notificationsAllowed(): Boolean
}

object NoPushTokens : PushTokenSource {
    override val platform = "fcm"
    override val token: StateFlow<String?> = MutableStateFlow(null)

    override fun notificationsAllowed() = false
}
