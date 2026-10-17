package cx.lpm.mobile.core.protocol

import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Pairing, sign-in, presence, push registration, the Mac's machines and updating lpm. */
sealed interface WireSession {
    /**
     * `replaces` names a device record on the Mac this pairing should retire — a
     * re-pair after the Mac lost this phone's credential. Omitted when absent, so
     * older Macs (which ignore unknown keys) behave exactly as before.
     *
     * Every first frame carries `phoneId`, this install's own id, so a Mac can refuse
     * a phone it removed whichever credential or Mac it comes through.
     */
    fun pair(code: String, name: String, phoneId: String, replaces: String? = null): String =
        frame("pair") {
            put("code", code)
            put("name", name)
            put("phoneId", phoneId)
            putNonEmpty("replaces", replaces)
        }

    /**
     * Request approve-on-Mac pairing: no code, the Mac shows an Allow dialog with a
     * match code the phone also displays. Sent as the first frame in that mode.
     */
    fun pairRequest(name: String, phoneId: String, replaces: String? = null): String =
        frame("pairRequest") {
            put("name", name)
            put("phoneId", phoneId)
            putNonEmpty("replaces", replaces)
        }

    fun auth(deviceId: String, token: String, phoneId: String): String = frame("auth") {
        put("deviceId", deviceId)
        put("token", token)
        put("phoneId", phoneId)
    }

    /**
     * Sign in to the Mac and ask it to carry this connection to one of the machines
     * it connects to; the machine then answers as itself.
     */
    fun relay(deviceId: String, token: String, slug: String, phoneId: String): String =
        frame("relay") {
            put("deviceId", deviceId)
            put("token", token)
            put("slug", slug)
            put("phoneId", phoneId)
        }

    /** Ask the Mac to forget this phone: the user removed it here. */
    fun unpair(deviceId: String, token: String, phoneId: String): String = frame("unpair") {
        put("deviceId", deviceId)
        put("token", token)
        put("phoneId", phoneId)
    }

    fun ping(): String = frame("ping")

    fun presence(active: Boolean): String = frame("presence") { put("active", active) }

    /**
     * Register (or refresh) this device's push identity. `key` is the base64
     * AES-256 push key shared with the notification decrypter.
     */
    fun apnsToken(
        token: String,
        env: String,
        key: String,
        notifyWaiting: Boolean,
        notifyDone: Boolean,
        notifyError: Boolean,
        notifyAutomationStarted: Boolean,
        notifyAutomationDone: Boolean,
        notifyAutomationError: Boolean
    ): String = frame("apnsToken") {
        put("token", token)
        put("env", env)
        put("key", key)
        putJsonObject("notify") {
            put("waiting", notifyWaiting)
            put("done", notifyDone)
            put("error", notifyError)
            put("automationStarted", notifyAutomationStarted)
            put("automationDone", notifyAutomationDone)
            put("automationError", notifyAutomationError)
        }
    }

    /** The machines this Mac connects to (its Linux servers and other Macs). */
    fun machines(): String = frame("machines")

    /** Ask the Mac to have one of its machines arm a pairing code for this phone. */
    fun machinePair(slug: String): String = frame("machinePair") { put("slug", slug) }

    fun stats(days: Int): String = frame("stats") { put("days", days) }

    fun limits(): String = frame("limits")

    fun ttsSpeak(reqId: String, text: String): String = frame("ttsSpeak") {
        put("reqId", reqId)
        put("text", text)
    }

    fun updateCheck(force: Boolean = false): String = frame("updateCheck") {
        if (force) put("force", true)
    }

    fun updateInstall(): String = frame("updateInstall")

    fun updateCancel(): String = frame("updateCancel")
}
