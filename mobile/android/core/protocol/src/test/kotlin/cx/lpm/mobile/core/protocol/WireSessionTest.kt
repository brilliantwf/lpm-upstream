package cx.lpm.mobile.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WireSessionTest {
    @Test
    fun pairOmitsAnEmptyReplaces() {
        assertFrame(
            """{ "t": "pair", "code": "AB12-CD34", "name": "My Pixel" }""",
            Wire.pair("AB12-CD34", "My Pixel")
        )
        assertFrame(
            """{ "t": "pair", "code": "AB12-CD34", "name": "My Pixel" }""",
            Wire.pair("AB12-CD34", "My Pixel", replaces = "")
        )
        assertFrame(
            """{ "t": "pair", "code": "AB12-CD34", "name": "My Pixel", "replaces": "d-0" }""",
            Wire.pair("AB12-CD34", "My Pixel", replaces = "d-0")
        )
        assertFrame(
            """{ "t": "pairRequest", "name": "My Pixel" }""",
            Wire.pairRequest("My Pixel", replaces = null)
        )
        assertFrame(
            """{ "t": "pairRequest", "name": "My Pixel", "replaces": "d-0" }""",
            Wire.pairRequest("My Pixel", "d-0")
        )
    }

    @Test
    fun signIn() {
        assertFrame(
            """{ "t": "auth", "deviceId": "d-1", "token": "tok" }""",
            Wire.auth("d-1", "tok")
        )
        assertFrame(
            """{ "t": "relay", "deviceId": "d-1", "token": "tok", "slug": "box" }""",
            Wire.relay("d-1", "tok", "box")
        )
        assertFrame(
            """{ "t": "unpair", "deviceId": "d-1", "token": "tok" }""",
            Wire.unpair("d-1", "tok")
        )
        assertFrame("""{ "t": "ping" }""", Wire.ping())
        assertFrame("""{ "t": "presence", "active": false }""", Wire.presence(active = false))
    }

    @Test
    fun apnsTokenNestsTheToggles() {
        assertFrame(
            """{ "t": "apnsToken", "token": "ab", "env": "production", "key": "a2V5",
                 "notify": { "waiting": true, "done": false, "error": true, "automationStarted": false,
                             "automationDone": true, "automationError": true } }""",
            Wire.apnsToken(
                token = "ab",
                env = "production",
                key = "a2V5",
                notifyWaiting = true,
                notifyDone = false,
                notifyError = true,
                notifyAutomationStarted = false,
                notifyAutomationDone = true,
                notifyAutomationError = true
            )
        )
    }

    @Test
    fun macRequests() {
        assertFrame("""{ "t": "machines" }""", Wire.machines())
        assertFrame("""{ "t": "machinePair", "slug": "box" }""", Wire.machinePair("box"))
        assertFrame("""{ "t": "stats", "days": 30 }""", Wire.stats(30))
        assertFrame("""{ "t": "limits" }""", Wire.limits())
        assertFrame(
            """{ "t": "ttsSpeak", "reqId": "r", "text": "Hello" }""",
            Wire.ttsSpeak("r", "Hello")
        )
    }

    @Test
    fun updateCheckSendsForceOnlyWhenSet() {
        assertFrame("""{ "t": "updateCheck" }""", Wire.updateCheck())
        assertFrame("""{ "t": "updateCheck", "force": true }""", Wire.updateCheck(force = true))
        assertFrame("""{ "t": "updateInstall" }""", Wire.updateInstall())
        assertFrame("""{ "t": "updateCancel" }""", Wire.updateCancel())
    }

    @Test
    fun framesAreCompactAndEscaped() {
        val text = Wire.input("t-1", "echo \"hi\"\n")
        assertFalse(text.contains(": "))
        assertFrame("""{ "t": "in", "id": "t-1", "d": "echo \"hi\"\n" }""", text)
        assertEquals("""{"t":"ping"}""", Wire.ping())
        assertEquals("""{"a":1}""", Wire.json(buildJsonObject { put("a", 1) }))
    }

    @Test
    fun helpers() {
        assertEquals("\u0000HEX:00ff7f80", Wire.hexFrame(byteArrayOf(0, -1, 127, -128)))
        assertEquals("\u0000HEX:", Wire.hexFrame(ByteArray(0)))
        val obj = Json.parseToJsonElement("""{ "label": "", "id": "t-3", "n": 7 }""").jsonObject
        assertEquals("t-3", Wire.label(obj, fallback = "id"))
        assertEquals("7", Wire.reqIdString(obj["n"]))
        assertEquals("", Wire.reqIdString(null))
    }

    @Test
    fun slugify() {
        assertEquals("auth-refactor-v2", Wire.slugify("  Auth Refactor — v2!! "))
        assertEquals("n-c-d", Wire.slugify("Ünïcødé ✨"))
        assertEquals("", Wire.slugify("✨ — ✨"))
        assertEquals(64, Wire.slugify("a".repeat(80)).length)
        assertEquals("hello-world", Wire.slugify("--Hello__World--"))
    }
}
