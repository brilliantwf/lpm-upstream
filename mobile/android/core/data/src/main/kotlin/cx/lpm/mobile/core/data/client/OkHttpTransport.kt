package cx.lpm.mobile.core.data.client

import android.annotation.SuppressLint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The real transport: an OkHttp WebSocket whose TLS trusts exactly what `trust`
 * accepts. Hostname verification is off on purpose — the Mac's self-signed
 * certificate names no host, and the pinned fingerprint is its identity.
 */
class OkHttpTransport @Inject constructor(private val base: OkHttpClient) : SocketTransport {
    override fun open(url: String, trust: X509TrustManager, listener: SocketListener): Socket {
        val client = base.pinnedTo(trust)
        val socket = OkHttpSocket()
        socket.ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket.opened = true
                    listener.onOpen(socket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(socket, text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    listener.onFailure(socket, null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(socket, t)
                }
            }
        )
        return socket
    }

    private class OkHttpSocket : Socket {
        @Volatile var ws: WebSocket? = null

        @Volatile var opened = false

        override fun send(text: String): Boolean = ws?.send(text) ?: false

        override fun close() {
            val ws = ws ?: return
            // A socket that never opened has nothing to say goodbye on.
            if (!opened || !ws.close(GOING_AWAY, null)) ws.cancel()
        }
    }

    companion object {
        const val GOING_AWAY = 1001

        /** The defaults every lpm socket starts from; the reconnect loop owns retrying. */
        fun baseClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

@SuppressLint("BadHostnameVerifier")
internal fun OkHttpClient.pinnedTo(trust: X509TrustManager): OkHttpClient {
    val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return newBuilder()
        .sslSocketFactory(tls.socketFactory, trust)
        .hostnameVerifier { _, _ -> true }
        .build()
}
