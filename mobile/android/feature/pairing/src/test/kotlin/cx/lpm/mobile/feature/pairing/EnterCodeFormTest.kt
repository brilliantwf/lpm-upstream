package cx.lpm.mobile.feature.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterCodeFormTest {
    @Test
    fun theCodePairsWithoutSpacesInCapitals() {
        assertEquals("AB12CD34", EnterCodeForm(code = " ab12 cd34\n").cleanCode)
        assertEquals("AB12-CD34", EnterCodeForm(code = "ab12-cd34").cleanCode)
    }

    @Test
    fun anAddressMayCarryItsOwnPort() {
        assertEquals("192.168.1.20" to 9000, EnterCodeForm(host = " 192.168.1.20:9000 ").target)
        assertEquals("192.168.1.20" to 8765, EnterCodeForm(host = "192.168.1.20").target)
        assertEquals("studio" to 7000, EnterCodeForm(host = "studio", port = "7000").target)
        // Out of range, so it's all address, and the port field counts.
        assertEquals(
            "studio:70000" to 8765,
            EnterCodeForm(host = "studio:70000", port = "8765").target
        )
    }

    @Test
    fun thePortFieldOnlyCountsWithoutAnInlinePort() {
        assertTrue(EnterCodeForm(host = "a", port = "65535").portIsValid)
        assertFalse(EnterCodeForm(host = "a", port = "0").portIsValid)
        assertFalse(EnterCodeForm(host = "a", port = "65536").portIsValid)
        assertFalse(EnterCodeForm(host = "a", port = "").portIsValid)
        assertTrue(EnterCodeForm(host = "a:9000", port = "").portIsValid)
    }

    @Test
    fun pairsOnlyWithACodeAnAddressAndAPort() {
        assertFalse(EnterCodeForm(code = "", host = "a").canPair)
        assertFalse(EnterCodeForm(code = "x", host = " ").canPair)
        assertFalse(EnterCodeForm(code = "x", host = "a", port = "x").canPair)
        assertTrue(EnterCodeForm(code = "x", host = "a").canPair)
    }

    @Test
    fun saysWhatTheAddressIsGoodFor() {
        assertEquals(EnterCodeForm.Hint.Tailscale, EnterCodeForm(host = "100.64.1.2").hint)
        assertEquals(
            EnterCodeForm.Hint.Tailscale,
            EnterCodeForm(host = "studio.tail1234.ts.net").hint
        )
        assertEquals(EnterCodeForm.Hint.Home, EnterCodeForm(host = "192.168.1.20").hint)
        assertEquals(EnterCodeForm.Hint.Home, EnterCodeForm(host = "studio.local").hint)
        assertNull(EnterCodeForm(host = "lpm.example.com").hint)
        assertNull(EnterCodeForm(host = "").hint)
    }
}
