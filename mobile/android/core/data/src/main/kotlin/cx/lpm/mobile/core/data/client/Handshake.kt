package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.protocol.Wire

/**
 * How a connection opens: with a one-time pairing code, an approve-on-Mac request,
 * or the stored credential — carried on to one of the Mac's machines with `relay`.
 */
internal class Handshake(
    private val deviceName: String,
    private val relaySlug: String?,
    private val phoneId: () -> String
) {
    private var pairingCode: String? = null

    // Set while an approve-on-Mac pairing is in flight: the name sent in its first
    // frame. That mode never auto-retries (a retry pops a second Allow dialog) and
    // runs the pair guard instead of the connect watchdog (approval takes longer).
    private var pairRequestName: String? = null

    // Re-pairing a Mac that rejected this phone's credential: the device id the Mac
    // should drop as it mints the new one (older Macs ignore it).
    private var replacesDeviceId: String? = null

    val approving: Boolean get() = pairRequestName != null

    fun pairWithCode(code: String, replaces: String?) {
        pairingCode = code
        replacesDeviceId = replaces
    }

    fun requestApproval(replaces: String?) {
        pairRequestName = deviceName
        replacesDeviceId = replaces
    }

    /** The first frame of an attempt; null when there's nothing to authenticate with. */
    fun frame(credential: LpmClient.Credential?): String? {
        val code = pairingCode
        return when {
            pairRequestName != null -> Wire.pairRequest(deviceName, phoneId(), replacesDeviceId)

            code != null -> Wire.pair(code, deviceName, phoneId(), replacesDeviceId)

            credential != null && relaySlug != null ->
                Wire.relay(credential.deviceId, credential.token, relaySlug, phoneId())

            credential != null -> Wire.auth(credential.deviceId, credential.token, phoneId())

            else -> null
        }
    }

    /** An approve-on-Mac request ended (or the client stopped); a code stays for a retry. */
    fun endApproval() {
        pairRequestName = null
        replacesDeviceId = null
    }

    /** Paired: from now on the connection signs in with the new credential. */
    fun paired() {
        pairingCode = null
        endApproval()
    }
}
