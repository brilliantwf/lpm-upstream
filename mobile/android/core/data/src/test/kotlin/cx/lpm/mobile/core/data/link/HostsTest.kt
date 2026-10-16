package cx.lpm.mobile.core.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The address rules from iOS AppModel (`mergedHosts`, `unionHosts`, `isCGNAT`, …). */
class HostsTest {
    @Test
    fun `CGNAT is 100_64_0_0 slash 10 only`() {
        assertTrue(Hosts.isCGNAT("100.64.0.1"))
        assertTrue(Hosts.isCGNAT("100.127.255.254"))
        assertFalse(Hosts.isCGNAT("100.63.1.1"))
        assertFalse(Hosts.isCGNAT("100.128.0.1"))
        assertFalse(Hosts.isCGNAT("192.168.1.2"))
        assertFalse(Hosts.isCGNAT("mac.tail1234.ts.net"))
        assertEquals(
            "100.101.1.2",
            Hosts.cgnatHost(listOf("192.168.1.2", "100.101.1.2", "100.64.0.9"))
        )
        assertNull(Hosts.cgnatHost(listOf("192.168.1.2", "mac.local")))
    }

    @Test
    fun `IPv4 literals are four octets in range`() {
        assertTrue(Hosts.isIPv4Literal("10.0.0.1"))
        assertFalse(Hosts.isIPv4Literal("10.0.0.256"))
        assertFalse(Hosts.isIPv4Literal("10.0.0"))
        assertFalse(Hosts.isIPv4Literal("mac.local"))
        assertFalse(Hosts.isIPv4Literal("fe80::1"))
    }

    @Test
    fun `a discovered address leads and replaces stale LAN literals only`() {
        val existing = listOf("192.168.1.20", "100.101.1.2", "mac.local", "fe80::1", "10.0.0.5")
        assertEquals(
            listOf("192.168.1.31", "100.101.1.2", "mac.local", "fe80::1"),
            Hosts.merged(existing, listOf("192.168.1.31"))
        )
        // A fresh address already saved just moves to the front; blanks and repeats drop.
        assertEquals(
            listOf("100.101.1.2", "mac.local"),
            Hosts.merged(
                listOf("mac.local", "100.101.1.2"),
                listOf("", "100.101.1.2", "100.101.1.2")
            )
        )
    }

    @Test
    fun `advertised addresses are folded in without dropping anything`() {
        val existing = listOf("203.0.113.7", "192.168.1.20")
        assertEquals(
            listOf("192.168.1.31", "100.101.1.2", "203.0.113.7", "192.168.1.20"),
            Hosts.union(existing, listOf("192.168.1.31", "", "100.101.1.2", "192.168.1.31"))
        )
        assertEquals(
            listOf("192.168.1.20", "203.0.113.7"),
            Hosts.union(existing, listOf("192.168.1.20", "203.0.113.7"))
        )
    }

    @Test
    fun `address kinds follow how people set them up`() {
        assertEquals(AddressKind.ThisDevice, AddressKind.of("127.0.0.1"))
        assertEquals(AddressKind.Tailscale, AddressKind.of("100.101.1.2"))
        assertEquals(AddressKind.Tailscale, AddressKind.of("studio.tail1234.ts.net"))
        assertEquals(AddressKind.Tailscale, AddressKind.of("fd7a:115c:a1e0::1"))
        assertEquals(AddressKind.Home, AddressKind.of("192.168.1.20"))
        assertEquals(AddressKind.Home, AddressKind.of("172.20.0.4"))
        assertEquals(AddressKind.Home, AddressKind.of("fe80::1"))
        assertEquals(AddressKind.Internet, AddressKind.of("203.0.113.7"))
        assertEquals(AddressKind.LocalName, AddressKind.of("Studio.local"))
        assertEquals(AddressKind.LocalName, AddressKind.of("studio"))
        assertEquals(AddressKind.Internet, AddressKind.of("mac.example.com"))
        assertTrue(AddressKind.Tailscale.worksAway)
        assertFalse(AddressKind.Home.worksAway)
    }

    @Test
    fun `wss URLs bracket IPv6 and refuse what can't be dialed`() {
        assertEquals(
            "wss://192.168.1.20:8765/",
            cx.lpm.mobile.core.data.client.WssUrl.make("192.168.1.20", 8765)
        )
        assertEquals(
            "wss://[fe80::1]:8765/",
            cx.lpm.mobile.core.data.client.WssUrl.make("fe80::1", 8765)
        )
        assertNull(cx.lpm.mobile.core.data.client.WssUrl.make("", 8765))
        assertNull(cx.lpm.mobile.core.data.client.WssUrl.make("bad host", 8765))
    }

    @Test
    fun `the identity code is the first twelve hex digits in groups`() {
        assertEquals("AB12 · CD34 · EF56", IdentityCode.of("ab12cd34ef567890"))
        assertEquals("AB12", IdentityCode.of("ab:12"))
    }
}
