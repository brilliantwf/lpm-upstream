package cx.lpm.mobile.core.testing

import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import cx.lpm.mobile.core.tailnet.TailnetException
import cx.lpm.mobile.core.tailnet.TailnetStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Built-in Tailscale whose state the test sets with [show]. [signIn] answers
 * [signInPage], or fails with [signInFailure]; every address is dialed directly.
 */
class FakeTailscale(override val deviceName: String = "lpm-phone") : BuiltInTailscale {
    override val enabled = MutableStateFlow(false)
    override val status = MutableStateFlow(TailnetStatus())
    override val state = MutableStateFlow(status.value.state)
    var signInPage: String? = null
    var signInFailure: String? = null
    var signOuts = 0

    fun show(status: TailnetStatus) {
        this.status.value = status
        state.value = status.state
    }

    override fun setEnabled(on: Boolean) {
        enabled.value = on
        if (!on) show(TailnetStatus())
    }

    override suspend fun signIn(): String? {
        if (!enabled.value) setEnabled(true)
        signInFailure?.let { throw TailnetException(it) }
        return signInPage
    }

    override suspend fun signOut() {
        signOuts++
    }

    override fun route(host: String, port: Int): Pair<String, Int> = host to port
}
