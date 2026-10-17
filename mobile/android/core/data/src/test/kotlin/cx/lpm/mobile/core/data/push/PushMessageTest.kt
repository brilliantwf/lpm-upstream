package cx.lpm.mobile.core.data.push

import cx.lpm.mobile.core.model.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushMessageTest {
    private val key = ByteArray(32) { it.toByte() }

    // Sealed the way the desktop's `seal_push` does: nonce(12) || ciphertext || tag(16).
    private val sealed =
        "AAECAwQFBgcICQoLPCClfreTp2nEJbWxk5oKG67npRjSCy0TUgKG8T9TIsF1f9yZybN99gCGU8/49UdSizoU" +
            "wTu0xrYdrQhKbIyHi5ZOqRSn8wpDaDXYCYr7LNIa/qwQGwo9CZHfsu4MlcvgopY4ioZ76lnyfUvWLVcNXc4C" +
            "EMaKq0T2BJt196CSQz+ZfiK1wLa/JnvDcoNquyW2X5qEFxkz5zmuZdlb2LmMl7GtvHaFnrkawlmp1+6i+62T" +
            "Ww2STb+EafTuMRHeUNo="

    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun opensWhatTheMacSealed() {
        val payload = PushCrypto.open(sealed, key)
        assertEquals("storefront", payload?.string("project"))
        assertEquals("Waiting", payload?.string("status"))
    }

    @Test
    fun anythingElseIsDropped() {
        assertNull(PushCrypto.open(sealed, ByteArray(32)))
        assertNull(PushCrypto.open(sealed, ByteArray(16)))
        assertNull(PushCrypto.open("not base64!", key))
        assertNull(PushCrypto.open("AAECAw==", key))
        assertNull(PushCrypto.open(sealed.replaceRange(30, 31, "A"), key))
    }

    @Test
    fun anAgentAlertReadsLikeIos() {
        val alert = PushAlert.parse(PushCrypto.open(sealed, key)!!)!!
        assertEquals("Storefront", alert.title)
        assertEquals("Claude — Agent is waiting for you", alert.body)
        assertEquals(PushKind.Waiting, alert.kind)
        assertEquals("srv-1", alert.serverId)
        assertEquals("terminal", alert.target)
        assertEquals("t-1", alert.terminalId)
        assertEquals("claude:t-1", alert.statusKey)

        val bare = PushAlert.parse(json("""{"project":"api","status":"Done"}"""))!!
        assertEquals("api", bare.title)
        assertEquals("Agent finished", bare.body)
        assertEquals(PushKind.Done, bare.kind)
        assertNull(bare.serverId)
        assertEquals(
            PushKind.Error,
            PushAlert.parse(json("""{"project":"a","status":"Error"}"""))?.kind
        )
        assertNull(PushAlert.parse(json("""{"project":"","status":"Done"}""")))
    }

    @Test
    fun anAutomationAlertFallsInTheKindOfItsOutcome() {
        fun kind(status: String) = PushAlert.parse(
            json(
                """{"project":"api","target":"automation","terminal":"Nightly","status":"$status"}"""
            )
        )?.kind
        assertEquals(PushKind.AutomationStarted, kind("Automation started"))
        assertEquals(
            PushKind.AutomationStarted,
            kind("Automation found work — open lpm to start it")
        )
        assertEquals(PushKind.AutomationDone, kind("Automation finished · 3 fixed"))
        assertEquals(PushKind.AutomationError, kind("Automation failed"))
        assertEquals(PushKind.AutomationError, kind("Automation timed out"))
        assertEquals(
            "Nightly — Automation finished · 3 fixed",
            PushAlert.parse(
                json(
                    """{"project":"api","target":"automation","terminal":"Nightly",""" +
                        """"status":"Automation finished · 3 fixed"}"""
                )
            )?.body
        )
    }

    @Test
    fun aWithdrawalNamesEntriesAndItsMac() {
        val clear = PushClear.parse(
            json(
                """{"serverId":"srv-1","clear":[{"project":"api","key":"k1"},""" +
                    """{"project":"web"},{"project":"web","key":"k2"}]}"""
            )
        )
        assertEquals("srv-1", clear?.serverId)
        assertEquals(
            setOf(StatusIdentity("api", "k1"), StatusIdentity("web", "k2")),
            clear?.entries
        )
        assertNull(PushClear.parse(json("""{"clear":[]}""")))
        assertNull(PushClear.parse(json("""{"project":"api"}""")))
    }
}
