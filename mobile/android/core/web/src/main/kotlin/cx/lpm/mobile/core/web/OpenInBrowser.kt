package cx.lpm.mobile.core.web

import androidx.compose.ui.platform.UriHandler

/** Opens [url] outside the app; nothing happens if no app can open it. */
internal fun UriHandler.openInBrowser(url: String) {
    try {
        openUri(url)
    } catch (_: IllegalArgumentException) {
    }
}
