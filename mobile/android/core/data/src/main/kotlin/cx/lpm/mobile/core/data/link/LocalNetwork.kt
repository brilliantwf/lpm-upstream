package cx.lpm.mobile.core.data.link

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android 17's local network permission, its counterpart of iOS's Local Network
 * prompt: an app targeting it reaches a LAN address or browses Bonjour only once the
 * user allows it. Earlier versions grant it with INTERNET.
 */
object LocalNetwork {
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
}
