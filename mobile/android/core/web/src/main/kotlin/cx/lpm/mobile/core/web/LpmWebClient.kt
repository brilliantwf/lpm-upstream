package cx.lpm.mobile.core.web

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Serves the bundled pages, keeps the WebView on them (any other navigation is
 * dropped, as the iOS views cancel it) and reports a lost renderer.
 */
// onRenderProcessGone is implemented below; the detector reads Kotlin's
// supertype constructor call as a bare WebViewClient.
@SuppressLint("MissingOnRenderProcessGone")
internal class LpmWebClient(
    private val assets: WebViewAssetLoader,
    private val documentStartFallback: String?,
    private val onGone: () -> Unit,
    private val onLoaded: () -> Unit = {},
    private val intercept: (Uri) -> WebResourceResponse? = { null }
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? = intercept(request.url) ?: assets.shouldInterceptRequest(request.url)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !WebPages.isPage(request.url)

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        documentStartFallback?.let { view.evaluateJavascript(it, null) }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (url?.startsWith(WebPages.PAGES) == true) onLoaded()
    }

    /**
     * The renderer died (usually reclaimed while the app was in the background).
     * Returning true keeps the app alive; the WebView itself is dead and must be
     * replaced, which [onGone] arranges.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(WEB_LOG_TAG, "WebView renderer gone (crashed: ${detail.didCrash()})")
        onGone()
        return true
    }
}

/** Page console output in logcat, for debug builds. */
internal object ConsoleLogger : WebChromeClient() {
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val priority = when (message.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            else -> Log.DEBUG
        }
        Log.println(
            priority,
            WEB_LOG_TAG,
            "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
        )
        return true
    }
}

/**
 * Locks the WebView down to running our own pages: JavaScript on, no file or
 * content access, no mixed content, Safe Browsing on, no zoom, no windows.
 */
@SuppressLint("SetJavaScriptEnabled") // only our bundled pages ever load (LpmWebClient)
internal fun WebView.applyLpmSettings() {
    settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = true
        setGeolocationEnabled(false)
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }
    // Under wrap_content (AndroidView's default) a WebView lays its page out
    // against a zero-height viewport, so `height: 100%` collapses.
    layoutParams =
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (debuggable) {
        webChromeClient = ConsoleLogger
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
