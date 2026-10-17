package cx.lpm.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.ui.LocalNetworkAccess
import cx.lpm.mobile.core.ui.rememberLocalNetworkAccess

/**
 * With a Mac saved, Android 17 keeps the phone off its Wi-Fi address until the user
 * allows local network access, where iOS asks on the first connection by itself. Ask
 * once the projects are up, and try the saved addresses again the moment it's allowed.
 */
@Composable
internal fun LocalNetworkRequest(session: MacSession) {
    val (access, request) = rememberLocalNetworkAccess()
    var blocked by remember { mutableStateOf(access != LocalNetworkAccess.Allowed) }
    LaunchedEffect(access) {
        when (access) {
            LocalNetworkAccess.NotDetermined -> request()

            LocalNetworkAccess.Allowed -> if (blocked) {
                blocked = false
                session.retryConnection()
            }

            LocalNetworkAccess.Denied -> blocked = true
        }
    }
}
