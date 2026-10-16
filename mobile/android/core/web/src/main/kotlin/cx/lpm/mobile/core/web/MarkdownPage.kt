package cx.lpm.mobile.core.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

/** The native side of web/markdown/markdown.html: render, links, copy, images. */
internal class MarkdownPage(scope: CoroutineScope) {
    val images = MarkdownImageLoader(scope)
    var onOpenFile: (List<String>) -> Unit = {}
    var onOpenUrl: (String) -> Unit = {}

    /** Bumped when the page is lost, so the view builds a fresh WebView. */
    var generation by mutableIntStateOf(0)
        private set

    private var view: WebView? = null
    private var text: String? = null
    private var rendered: String? = null
    private var loaded = false

    fun create(context: Context, dark: Boolean): WebView {
        val themed =
            ContextThemeWrapper(context, if (dark) R.style.LpmWeb_Dark else R.style.LpmWeb_Light)
        val web = WebView(themed)
        web.applyLpmSettings()
        // markdown.css sizes text from `-apple-system-body` (17pt at the default
        // text size), which only Apple's WebKit knows; start from the same 17px and
        // follow the system font scale as iOS follows Dynamic Type.
        web.settings.defaultFontSize = BODY_FONT_SIZE
        web.settings.textZoom = (context.resources.configuration.fontScale * 100).roundToInt()
        web.setBackgroundColor(Color.TRANSPARENT)
        // A replaced WebView can still report in; only the current one counts.
        val fallback = web.installBridge(HANDLERS) { if (view === web) onMessage(it) }
        web.webViewClient = LpmWebClient(
            assets = WebPages.assetLoader(context),
            documentStartFallback = fallback,
            onGone = { if (view === web) pageLost() },
            onLoaded = { if (view === web) pageLoaded() },
            intercept = images::intercept
        )
        loaded = false
        rendered = null
        view = web
        web.loadUrl(WebPages.MARKDOWN_URL)
        return web
    }

    fun release(web: WebView) {
        if (view === web) view = null
        images.cancelAll()
        web.destroy()
    }

    fun show(text: String) {
        this.text = text
        render()
    }

    private fun render() {
        val web = view ?: return
        val text = text ?: return
        if (!loaded || text == rendered) return
        rendered = text
        web.evaluateJavascript("renderMarkdown(${JsLiteral.string(text)})", null)
    }

    private fun pageLoaded() {
        loaded = true
        render()
    }

    /** The renderer died; a new WebView renders the same text again. */
    private fun pageLost() {
        loaded = false
        rendered = null
        generation++
    }

    private fun onMessage(message: PageMessage) {
        val body = message.string ?: return
        when (message.name) {
            "link" -> when (val link = MarkdownLink.resolve(body, images.path)) {
                is MarkdownLink.External -> onOpenUrl(link.url)
                is MarkdownLink.File -> onOpenFile(link.paths)
                MarkdownLink.Anchor -> Unit
            }

            "copy" -> view?.let { copy(it, body) }
        }
    }

    private fun copy(web: WebView, text: String) {
        web.context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("code", text))
        web.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private companion object {
        val HANDLERS = listOf("link", "copy")
        const val BODY_FONT_SIZE = 17
    }
}
