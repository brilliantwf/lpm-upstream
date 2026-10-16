package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.demo.DemoServer
import java.time.Clock
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope

/**
 * What every client runs on. `scope` must post to the main thread rather than run
 * inline (`Dispatchers.Main`, not `.immediate`): like `DispatchQueue.main.async`,
 * a frame is handled after the code that triggered it has returned.
 */
class ClientEnvironment(
    val scope: CoroutineScope,
    val clock: Clock,
    val transport: SocketTransport,
    val tailnet: TailnetRouter,
    val random: Random = Random.Default
)

/** Builds the session's clients: a paired Mac, a pairing, or the in-process demo. */
class LpmClientFactory(private val env: ClientEnvironment) {
    /**
     * `pinProvider` (reconnect) is read fresh on each TLS handshake, so a pin saved
     * after a first connect applies to the next; `expectedFingerprint` (QR pairing)
     * must match the certificate exactly. `relay` opens one of the Mac's machines.
     */
    fun create(
        endpoint: LpmClient.Endpoint,
        credential: LpmClient.Credential?,
        deviceName: String,
        pinProvider: (() -> String?)? = null,
        expectedFingerprint: String? = null,
        relay: String? = null
    ): LpmClient = LpmClient(
        endpoint = endpoint,
        credential = credential,
        deviceName = deviceName,
        env = env,
        trust = PinningTrustManager(pinProvider, expectedFingerprint),
        demoServer = null,
        relaySlug = relay
    )

    /**
     * A client backed by an in-process [DemoServer]: no socket, and a preset demo
     * credential so control-ownership checks work before the scripted `ready`.
     */
    fun demo(deviceName: String): LpmClient = LpmClient(
        endpoint = LpmClient.Endpoint("demo", 0),
        credential = LpmClient.Credential(DemoServer.DEVICE_ID, "demo"),
        deviceName = deviceName,
        env = env,
        trust = PinningTrustManager(null, null),
        demoServer = DemoServer(env.scope, env.clock),
        relaySlug = null
    )
}
