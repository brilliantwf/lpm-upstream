package cx.lpm.mobile.core.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether lpm Link may post notifications: not asked yet, refused, or allowed. */
enum class NotificationAccess { NotDetermined, Denied, Allowed }

/**
 * This app's notification permission, kept current when the user comes back from
 * Settings, and the way to ask for it (Android's dialog on 13 and later).
 */
@Composable
fun rememberNotificationAccess(): Pair<NotificationAccess, () -> Unit> {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var access by rememberSaveable { mutableStateOf(context.notificationAccess(asked)) }
    val permission = ActivityResultContracts.RequestPermission()
    val launcher = rememberLauncherForActivityResult(permission) { granted ->
        asked = true
        access = if (granted) NotificationAccess.Allowed else NotificationAccess.Denied
    }
    LifecycleResumeEffect(asked) {
        access = context.notificationAccess(asked)
        onPauseOrDispose {}
    }
    val request = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return access to request
}

private fun Context.notificationAccess(asked: Boolean): NotificationAccess = when {
    NotificationManagerCompat.from(this).areNotificationsEnabled() -> NotificationAccess.Allowed

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked ->
        NotificationAccess.NotDetermined

    else -> NotificationAccess.Denied
}

/** This app's notification settings, or its page in system settings where those aren't offered. */
fun Context.openNotificationSettings() {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val app = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    for (intent in listOf(notifications, app)) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Nothing on this phone handles it; try the next page.
        }
    }
}
