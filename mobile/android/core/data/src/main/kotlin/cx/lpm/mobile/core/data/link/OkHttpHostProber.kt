package cx.lpm.mobile.core.data.link

import cx.lpm.mobile.core.data.client.PinningTrustManager
import cx.lpm.mobile.core.data.client.TailnetRouter
import cx.lpm.mobile.core.data.client.WssUrl
import cx.lpm.mobile.core.data.client.pinnedTo
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Probes with a real `wss://host:port/` open: lpm answers the WebSocket upgrade
 * before any auth, so an open proves the port is lpm's, and the socket is torn down
 * at once. It sends nothing, and checks the certificate exactly as the live link
 * does ([PinningTrustManager]) — a mismatch means the Mac answered under another
 * identity, which counts as reachable and is left for the live connection to report.
 */
class OkHttpHostProber(private val base: OkHttpClient, private val tailnet: TailnetRouter) :
    HostProber {
    override suspend fun open(
        host: String,
        port: Int,
        identity: HostProber.Identity,
        timeoutMs: Long
    ): HostProber.Outcome {
        val (dialHost, dialPort) = tailnet.route(host, port)
        val url =
            WssUrl.make(dialHost, dialPort) ?: return HostProber.Outcome(host, false, "bad address")
        val mismatched = AtomicBoolean(false)
        val trust = PinningTrustManager({ identity.pin }, identity.expected)
        trust.onMismatch = { mismatched.set(true) }
        val client = base.pinnedTo(trust)
        val detail = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val ws = client.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.cancel()
                            if (cont.isActive) cont.resume("open")
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?
                        ) {
                            if (cont.isActive) {
                                cont.resume(
                                    if (mismatched.get()) "open" else shortReason(t)
                                )
                            }
                        }
                    }
                )
                cont.invokeOnCancellation { ws.cancel() }
            }
        } ?: "timed out"
        return HostProber.Outcome(host, detail == "open", detail)
    }

    companion object {
        /** "refused" vs "no route" vs "timed out" tells LAN-blocked from Tailscale-down. */
        fun shortReason(error: Throwable?): String {
            val causes = generateSequence(error) {
                it.cause?.takeIf { c -> c !== it }
            }.take(8).toList()
            return when {
                causes.any {
                    it is UnknownHostException || it is NoRouteToHostException
                } -> "no route"

                causes.any { it is SocketTimeoutException } -> "timed out"

                causes.any { it is ConnectException } -> {
                    val message = causes.joinToString(" ") { it.message.orEmpty() }
                    when {
                        message.contains("refused", true) -> "refused"
                        message.contains("unreachable", true) -> "no route"
                        else -> "failed"
                    }
                }

                causes.any { it is SSLException } -> "failed (TLS)"

                causes.any { it.message.orEmpty().contains("reset", true) } -> "connection lost"

                else -> "failed"
            }
        }
    }
}
