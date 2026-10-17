package cx.lpm.mobile.core.data.push

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.LpmClientFactory
import cx.lpm.mobile.core.data.client.sendPushToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One Mac's registration connection (iOS `PushRegistrar.Attempt`): dials, sends the
 * registration once the session is ready, and resolves once — on the Mac's ack, on a
 * terminal failure, or when the time runs out — hanging up on the way out. It
 * subscribes to nothing and is never handed to the session.
 */
internal class PushAttempt(private val clients: LpmClientFactory, private val deviceName: String) {
    suspend fun register(
        endpoint: LpmClient.Endpoint,
        credential: LpmClient.Credential,
        pin: () -> String?,
        registration: PushRegistration,
        onIdentity: (serverId: String?, serverName: String?, platform: String?) -> Unit,
        timeoutMs: Long
    ): Boolean {
        val acked = CompletableDeferred<Boolean>()
        val c = clients.create(endpoint, credential, deviceName, pinProvider = pin)
        c.onState = { state ->
            when (state) {
                LpmClient.State.Ready -> c.sendPushToken(registration)
                is LpmClient.State.Failed -> acked.complete(false)
                else -> Unit
            }
        }
        c.onPushToken = { ok -> acked.complete(ok) }
        c.onIdentity = onIdentity
        return try {
            c.connect()
            withTimeoutOrNull(timeoutMs) { acked.await() } ?: false
        } finally {
            // Dropped first: shutting down reports `Idle` to a dead attempt otherwise.
            c.onState = null
            c.onPushToken = null
            c.onIdentity = null
            c.shutdown()
        }
    }
}
