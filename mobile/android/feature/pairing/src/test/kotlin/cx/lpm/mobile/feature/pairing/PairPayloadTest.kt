package cx.lpm.mobile.feature.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairPayloadTest {
    @Test
    fun readsEveryAddressThePortTheCodeAndTheFingerprint() {
        val payload = PairPayload.parse(
            "lpm://pair?p=9000&c=AB12CD34&h=192.168.1.20&h=100.64.1.2&h=studio.local&f=%20ABCDEF0123%20"
        )
        assertEquals(listOf("192.168.1.20", "100.64.1.2", "studio.local"), payload?.hosts)
        assertEquals("192.168.1.20", payload?.host)
        assertEquals(9000, payload?.port)
        assertEquals("AB12CD34", payload?.code)
        assertEquals("abcdef0123", payload?.fingerprint)
    }

    @Test
    fun dropsEmptyAddressesAndDefaultsThePort() {
        val payload = PairPayload.parse("lpm://pair?c=X&h=&h=10.0.0.2&p=port")
        assertEquals(listOf("10.0.0.2"), payload?.hosts)
        assertEquals(8765, payload?.port)
        assertNull(payload?.fingerprint)
        assertEquals(8765, PairPayload.parse("lpm://pair?c=X&h=10.0.0.2")?.port)
    }

    @Test
    fun decodesPercentEscapesButKeepsPlus() {
        val payload = PairPayload.parse("lpm://pair?c=A%2BB+C&h=fe80%3A%3A1")
        assertEquals("A+B+C", payload?.code)
        assertEquals(listOf("fe80::1"), payload?.hosts)
    }

    @Test
    fun anEmptyFingerprintIsNone() {
        assertNull(PairPayload.parse("lpm://pair?c=X&h=a&f=")?.fingerprint)
        assertNull(PairPayload.parse("lpm://pair?c=X&h=a&f=%20")?.fingerprint)
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(PairPayload.parse("lpm://pair?h=10.0.0.2"))
        assertNull(PairPayload.parse("lpm://pair?c=X"))
        assertNull(PairPayload.parse("lpm://pair?c=X&h="))
        assertNull(PairPayload.parse("https://pair?c=X&h=a"))
        assertNull(PairPayload.parse("lpm://open?c=X&h=a"))
        assertNull(PairPayload.parse("not a url"))
    }

    @Test
    fun aCodeWithNoValueIsMissing() {
        assertNull(PairPayload.parse("lpm://pair?c&h=a"))
        assertEquals("", PairPayload.parse("lpm://pair?c=&h=a")?.code)
    }
}
