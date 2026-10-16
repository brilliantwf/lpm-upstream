package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.demo.DemoServer
import cx.lpm.mobile.core.protocol.Wire
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// Demo Mode: outbound frames go to the in-process DemoServer (see LpmClient.send)
// and its replies come back through `injectInbound`, the normal inbound path.

private const val DEMO_HANDSHAKE_MS = 400L

/**
 * No socket and no auth: after a short simulated handshake, inject a `ready` so its
 * usual effects run (state, re-subscribe, flush, identity). Idempotent while live.
 */
internal fun LpmClient.connectDemo() {
    wantConnected = true
    if (state == LpmClient.State.Ready) return
    set(LpmClient.State.Connecting)
    env.scope.launch {
        delay(DEMO_HANDSHAKE_MS)
        if (!wantConnected || state == LpmClient.State.Ready) return@launch
        val ready = buildJsonObject {
            put("t", "ready")
            put("serverId", DemoServer.SERVER_ID)
            put("serverName", DemoServer.SERVER_NAME)
            putJsonArray("features") {
                add(LpmClient.FILE_UPLOAD_FEATURE)
                add(LpmClient.SERVICE_LOGS_WATCH_FEATURE)
            }
        }
        injectInbound(Wire.json(ready))
    }
}
