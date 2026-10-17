package cx.lpm.mobile.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether lpm Link may reach Macs on this Wi-Fi: not asked yet, refused, or allowed. */
enum class LocalNetworkAccess { NotDetermined, Denied, Allowed }

/**
 * Android 17's local network permission (iOS asks for Local Network on first use by
 * itself), kept current when the user comes back from Settings, and the way to ask
 * for it. Always allowed before Android 17.
 */
@Composable
fun rememberLocalNetworkAccess(): Pair<LocalNetworkAccess, () -> Unit> {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var access by rememberSaveable { mutableStateOf(context.localNetworkAccess(asked)) }
    val permission = ActivityResultContracts.RequestPermission()
    val launcher = rememberLauncherForActivityResult(permission) { granted ->
        asked = true
        access = if (granted) LocalNetworkAccess.Allowed else LocalNetworkAccess.Denied
    }
    LifecycleResumeEffect(asked) {
        access = context.localNetworkAccess(asked)
        onPauseOrDispose {}
    }
    val request = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
    return access to request
}

private fun Context.localNetworkAccess(asked: Boolean): LocalNetworkAccess = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN -> LocalNetworkAccess.Allowed

    checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED -> LocalNetworkAccess.Allowed

    !asked -> LocalNetworkAccess.NotDetermined

    else -> LocalNetworkAccess.Denied
}
