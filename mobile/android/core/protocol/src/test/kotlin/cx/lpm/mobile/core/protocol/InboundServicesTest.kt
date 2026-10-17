package cx.lpm.mobile.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboundServicesTest {
    @Test
    fun restartAndStopRepliesCarryOnlyAFailure() {
        assertNull(decode<Inbound.ServiceOp>("""{ "t": "restartService", "ok": true }""").error)
        assertEquals(
            "web isn't declared",
            decode<Inbound.ServiceOp>(
                """{ "t": "stopService", "ok": false, "error": "web isn't declared" }"""
            ).error
        )
        assertEquals(
            "Couldn't change the service.",
            decode<Inbound.ServiceOp>("""{ "t": "restartService" }""").error
        )
    }
}
