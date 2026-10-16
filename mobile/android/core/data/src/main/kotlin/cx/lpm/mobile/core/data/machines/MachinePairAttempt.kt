package cx.lpm.mobile.core.data.machines

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.LpmClientFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One pairing connection to a machine the Mac handed over: redeems the code, keeps
 * the credential, and hangs up. Like the push registrar's attempts it is never given
 * to the session, so it can't disturb the live link. Resolves once — on `paired`, on
 * a terminal failure, or when the time runs out.
 */
internal class MachinePairAttempt(
    private val clients: LpmClientFactory,
    private val deviceName: String
) {
    data class Pairing(
        val deviceId: String,
        val token: String,
        val serverId: String?,
        val serverName: String?,
        val platform: String?,
        val hosts: List<String>,
        val fingerprint: String?
    )

    sealed interface Outcome {
        data class Paired(val pairing: Pairing) : Outcome

        data class Failed(val reason: String) : Outcome
    }

    suspend fun pair(
        host: String,
        port: Int,
        code: String,
        fingerprint: String?,
        timeoutMs: Long
    ): Outcome {
        val result = CompletableDeferred<Outcome>()
        val c = clients.create(
            endpoint = LpmClient.Endpoint(host, port),
            credential = null,
            deviceName = deviceName,
            expectedFingerprint = fingerprint
        )
        c.onPaired = { deviceId, token, serverId, serverName, hosts, platform ->
            val pairing = Pairing(
                deviceId = deviceId,
                token = token,
                serverId = serverId,
                serverName = serverName,
                platform = platform,
                hosts = hosts,
                fingerprint = c.observedFingerprint
            )
            result.complete(Outcome.Paired(pairing))
        }
        c.onState = { state ->
            if (state is LpmClient.State.Failed) result.complete(Outcome.Failed(state.message))
        }
        return try {
            c.pair(host, port, code)
            withTimeoutOrNull(timeoutMs) { result.await() } ?: Outcome.Failed(TIMED_OUT)
        } finally {
            c.onState = null
            c.onPaired = null
            c.shutdown()
        }
    }

    companion object {
        const val TIMED_OUT = "timed out"
    }
}
