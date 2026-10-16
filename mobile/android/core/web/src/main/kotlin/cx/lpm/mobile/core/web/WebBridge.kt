package cx.lpm.mobile.core.web

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Receives the shim's envelopes, from the page origin's main frame only. */
internal class BridgeListener(private val onMessage: (PageMessage) -> Unit) :
    WebViewCompat.WebMessageListener {
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        if (message.type != WebMessageCompat.TYPE_STRING) return
        receive(message.data, sourceOrigin, isMainFrame)
    }

    fun receive(data: String?, sourceOrigin: Uri, isMainFrame: Boolean) {
        if (!isMainFrame || !WebPages.isPageOrigin(sourceOrigin)) return
        PageMessage.parse(data)?.let(onMessage)
    }
}

/**
 * Wires [handlers] (the names iOS registers with its WKUserContentController) to
 * [onMessage]. Must run before the page loads. Returns the shim to inject when the
 * page starts if this WebView can't add document-start scripts, else null.
 */
internal fun WebView.installBridge(
    handlers: Collection<String>,
    onMessage: (PageMessage) -> Unit
): String? {
    val origins = setOf(WebPages.ORIGIN)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            this,
            WebkitShim.BRIDGE_OBJECT,
            origins,
            BridgeListener(onMessage)
        )
    } else {
        Log.e(
            WEB_LOG_TAG,
            "This WebView can't post messages to the app; update Android System WebView"
        )
        return null
    }
    val shim = WebkitShim.script(handlers)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, shim, origins)
        return null
    }
    return shim
}

internal const val WEB_LOG_TAG = "LpmWeb"
