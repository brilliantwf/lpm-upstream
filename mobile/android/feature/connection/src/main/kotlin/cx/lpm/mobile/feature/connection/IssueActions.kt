package cx.lpm.mobile.feature.connection

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.tailnet.BuiltInTailscale

/** Carries out an issue's button: the phone's own screens here, the rest in the session. */
internal fun Context.performIssueAction(
    action: IssueAction,
    issue: ConnectionIssue,
    perform: (IssueAction, ConnectionIssue) -> Unit
) {
    when (action) {
        IssueAction.OpenAdmin -> openInBrowser(BuiltInTailscale.ADMIN_URL)
        IssueAction.PhoneSettings -> openNetworkSettings()
        else -> perform(action, issue)
    }
}

/** A web page in the in-app browser (iOS `SafariView`). */
internal fun Context.openInApp(url: String) {
    runCatching { CustomTabsIntent.Builder().build().launchUrl(this, url.toUri()) }
        .onFailure { openInBrowser(url) }
}

/** A link handed to whichever app opens it (iOS `UIApplication.open`). */
internal fun Context.openInBrowser(url: String) {
    startSafely(Intent(Intent.ACTION_VIEW, url.toUri()))
}

/** iOS opens Settings for an offline phone; Android's closest is its internet panel. */
private fun Context.openNetworkSettings() {
    val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }
    startSafely(panel)
}

/** This app's page in system settings (iOS `openSettingsURLString`). */
internal fun Context.openAppSettings() {
    startSafely(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing on this phone handles it; the button simply does nothing.
    }
}
