package cx.lpm.mobile.core.web

/**
 * The pages post to the app the WKWebView way,
 * `window.webkit.messageHandlers[name].postMessage(body)`. This document-start
 * script defines exactly the handlers iOS registers for the page and forwards
 * each post as one JSON envelope, `{"name": …, "body": …}`, through the object
 * `WebViewCompat.addWebMessageListener` injects ([BRIDGE_OBJECT]).
 */
internal object WebkitShim {
    const val BRIDGE_OBJECT = "lpmBridge"

    fun script(handlers: Collection<String>): String {
        val names = handlers.joinToString(",", "[", "]", transform = JsLiteral::string)
        return """
            (() => {
              const bridge = window.$BRIDGE_OBJECT;
              if (!bridge || window.webkit) return;
              const messageHandlers = {};
              for (const name of $names) {
                messageHandlers[name] = Object.freeze({
                  postMessage(body) {
                    bridge.postMessage(JSON.stringify(body === undefined ? { name } : { name, body }));
                  },
                });
              }
              Object.defineProperty(window, 'webkit', {
                value: Object.freeze({ messageHandlers: Object.freeze(messageHandlers) }),
              });
            })();
        """.trimIndent()
    }
}
