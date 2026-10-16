package cx.lpm.mobile.core.data.client

import javax.net.ssl.X509TrustManager

/**
 * Opens WebSockets for [LpmClient]: OkHttp in the app, a fake in tests. Listener
 * calls may arrive on any thread; the client hops to the main thread itself.
 */
fun interface SocketTransport {
    /** Starts connecting `url` (`wss://…`); TLS trust is decided by `trust` alone. */
    fun open(url: String, trust: X509TrustManager, listener: SocketListener): Socket
}

interface Socket {
    /** False when the text couldn't be queued (the socket is closing or gone). */
    fun send(text: String): Boolean

    /** Tears the socket down; no listener call for it matters afterwards. */
    fun close()
}

interface SocketListener {
    /** The TLS + WebSocket handshake finished. */
    fun onOpen(socket: Socket)

    fun onMessage(socket: Socket, text: String)

    /** The socket failed or the Mac closed it; `error` is null for a clean close. */
    fun onFailure(socket: Socket, error: Throwable?)
}
