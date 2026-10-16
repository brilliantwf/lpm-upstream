package cx.lpm.mobile.core.web

import android.content.Context
import android.net.Uri
import androidx.webkit.WebViewAssetLoader

/**
 * The bundled pages (mobile/shared/web, packaged under assets/web), served from
 * WebViewAssetLoader's https origin so they run as a secure context with no
 * file:// access.
 */
internal object WebPages {
    const val HOST = WebViewAssetLoader.DEFAULT_DOMAIN
    const val ORIGIN = "https://$HOST"
    private const val PATH = "/assets/web/"
    const val PAGES = "$ORIGIN$PATH"
    const val TERMINAL_URL = "${PAGES}terminal.html"
    const val MARKDOWN_URL = "${PAGES}markdown/markdown.html"

    fun isPageOrigin(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == HOST && uri.port == -1

    fun isPage(uri: Uri): Boolean = isPageOrigin(uri) && uri.path?.startsWith(PATH) == true

    /** Serves assets/web only, so a page can't read anything else the app ships. */
    fun assetLoader(context: Context): WebViewAssetLoader {
        val assets = WebViewAssetLoader.AssetsPathHandler(context)
        return WebViewAssetLoader.Builder()
            .addPathHandler(PATH) { path -> assets.handle("web/$path") }
            .build()
    }
}
