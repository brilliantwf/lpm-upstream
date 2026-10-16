package cx.lpm.mobile.core.data.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Built-in Tailscale as the data layer sees it (iOS `BuiltInTailscale`). `:core:tailnet`
 * binds the real node; until then [NoTailnet] dials every address directly, which is
 * how a phone using the Tailscale app reaches its Macs anyway.
 */
interface TailnetRouter {
    /** Whether Built-in Tailscale is switched on. */
    val enabled: StateFlow<Boolean>

    /** The node's state: "off", "starting", "running", "needsLogin", "needsApproval", … */
    val state: StateFlow<String>

    /** Where to dial `host:port`: a loopback bridge for a tailnet address when up. */
    fun route(host: String, port: Int): Pair<String, Int>

    /** Turns the node on; returns the sign-in page to open when it has one. */
    suspend fun signIn(): String?
}

object NoTailnet : TailnetRouter {
    override val enabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val state: StateFlow<String> = MutableStateFlow("off")

    override fun route(host: String, port: Int): Pair<String, Int> = host to port

    override suspend fun signIn(): String? = null
}
