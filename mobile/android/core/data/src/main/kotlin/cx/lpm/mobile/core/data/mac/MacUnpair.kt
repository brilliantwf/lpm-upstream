package cx.lpm.mobile.core.data.mac

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.PinningTrustManager
import cx.lpm.mobile.core.data.client.Socket
import cx.lpm.mobile.core.data.client.SocketListener
import cx.lpm.mobile.core.data.client.SocketTransport
import cx.lpm.mobile.core.data.client.TailnetRouter
import cx.lpm.mobile.core.data.client.WssUrl
import cx.lpm.mobile.core.protocol.Wire
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells a Mac the user removed it from this phone, so it drops this phone's device
 * record instead of trusting — and pushing to — a credential the phone no longer
 * has. One short-lived connection per address, on the pinned certificate only. Best
 * effort: removal never waits for it.
 */
class MacUnpair(
    private val scope: CoroutineScope,
    private val transport: SocketTransport,
    private val tailnet: TailnetRouter,
    private val phoneId: () -> String
) {
    fun send(hosts: List<String>, port: Int, credential: LpmClient.Credential, pin: String?) {
        // Without a pin, whoever answers at the address could be anyone; the token
        // isn't handed to them.
        if (pin == null) return
        val frame = Wire.unpair(credential.deviceId, credential.token, phoneId())
        scope.launch {
            for (host in hosts) {
                if (host.isNotEmpty() && deliver(frame, host, port, pin)) return@launch
            }
        }
    }

    /** Whether the Mac at `host` answered at all, so the next address isn't tried. */
    private suspend fun deliver(frame: String, host: String, port: Int, pin: String): Boolean {
        val (dialHost, dialPort) = tailnet.route(host, port)
        val url = WssUrl.make(dialHost, dialPort) ?: return false
        var socket: Socket? = null
        val answered = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SocketListener {
                    override fun onOpen(socket: Socket) {
                        if (!socket.send(frame) && cont.isActive) cont.resume(false)
                    }

                    override fun onMessage(socket: Socket, text: String) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onFailure(socket: Socket, error: Throwable?) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
                socket = transport.open(url, PinningTrustManager({ pin }, null), listener)
            }
        } ?: false
        socket?.close()
        return answered
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
