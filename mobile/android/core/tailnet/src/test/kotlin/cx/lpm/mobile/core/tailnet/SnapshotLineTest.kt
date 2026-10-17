package cx.lpm.mobile.core.tailnet

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotLineTest {
    @Test
    fun writesTheFormatTheGoNodeReads() {
        val line = snapshotLine(
            name = "wlan0",
            index = 30,
            mtu = 1500,
            up = true,
            broadcast = true,
            loopback = false,
            pointToPoint = false,
            multicast = true,
            addresses = listOf(
                InetAddress.getByName("fe80::2f60:2c82:4163:8389%1") to 64,
                InetAddress.getByName("10.1.10.131") to 24
            )
        )
        assertEquals(
            "wlan0 30 1500 true true false false true | fe80:0:0:0:2f60:2c82:4163:8389/64 10.1.10.131/24",
            line
        )
    }

    @Test
    fun anInterfaceWithoutAddressesKeepsItsBar() {
        val line =
            snapshotLine("rmnet_data0", 21, 1500, false, false, false, true, false, emptyList())
        assertEquals("rmnet_data0 21 1500 false false false true false | ", line)
    }
}
