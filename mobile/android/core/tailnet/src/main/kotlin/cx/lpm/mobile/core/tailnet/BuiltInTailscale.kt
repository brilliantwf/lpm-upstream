package cx.lpm.mobile.core.tailnet

import cx.lpm.mobile.core.data.client.TailnetRouter
import kotlinx.coroutines.flow.StateFlow

/**
 * lpm Link's built-in Tailscale node (iOS `BuiltInTailscale`): it joins the user's
 * tailnet as a device of its own, so the phone reaches a Mac's tailnet address from
 * any network without the Tailscale app. The screens read [status] and switch it with
 * [setEnabled]; the data layer routes through it as a [TailnetRouter].
 */
interface BuiltInTailscale : TailnetRouter {
    val status: StateFlow<TailnetStatus>

    /** The name it joins the tailnet as ("lpm-phone"). */
    val deviceName: String

    fun setEnabled(on: Boolean)

    /** Sign out, which also removes this phone from the tailnet's device list. */
    suspend fun signOut()

    companion object {
        const val ADMIN_URL = "https://login.tailscale.com/admin/machines"
    }
}

/**
 * The node's state as it reports it: `state` is "off", "starting", "running",
 * "needsLogin", "needsApproval", "stopped" or "error".
 */
data class TailnetStatus(
    val state: String = "off",
    val authUrl: String? = null,
    val ip: String? = null,
    val dnsName: String? = null,
    val account: String? = null,
    val tailnet: String? = null,
    val error: String? = null
)

/** A failure the node reported, with a message to show as is. */
class TailnetException(message: String) : Exception(message)
