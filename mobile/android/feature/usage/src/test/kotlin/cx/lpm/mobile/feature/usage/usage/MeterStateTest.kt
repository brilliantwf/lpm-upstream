package cx.lpm.mobile.feature.usage.usage

import cx.lpm.mobile.core.model.LimitWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterStateTest {
    private val now = 1_792_065_600_000.0
    private val hour = 3_600_000.0

    private fun window(used: Double, resetsIn: Double) =
        LimitWindow(used, ((now + resetsIn) / 1000).toLong())

    @Test
    fun `a fast-burning window says it runs out before it resets`() {
        // The demo's Claude 5-hour window: 71% used with 3 of 5 hours left.
        val meter = meterState(window(71.0, 3 * hour), FIVE_HOUR_MS, now)
        assertTrue(meter.hasData)
        assertEquals(71L, meter.shown)
        assertEquals("ahead of pace", meter.verdict)
        assertEquals(VerdictTone.Warning, meter.verdictTone)
        assertEquals(0.71, meter.barFraction, 1e-9)
        assertEquals(0.4, meter.tick!!, 1e-9)
        assertEquals("runs out in ~49m, before reset", meter.runsOut)
        assertTrue(meter.resetLine.startsWith("resets in 3h · "))
        assertEquals(
            "71% used, ahead of pace, 40% of the window elapsed, resets in 3h, " +
                "runs out in ~49m, before reset",
            meter.accessibilityValue
        )
    }

    @Test
    fun `a calm window has a tick but no warning`() {
        val meter = meterState(window(11.0, 106 * hour), WEEKLY_MS, now)
        assertEquals("under pace", meter.verdict)
        assertEquals(VerdictTone.Secondary, meter.verdictTone)
        assertEquals(62.0 / 168, meter.tick!!, 1e-9)
        assertEquals("", meter.runsOut)
    }

    @Test
    fun `a full window is critical, and an over-full one fills the bar only once`() {
        val meter = meterState(window(130.0, 2 * hour), FIVE_HOUR_MS, now)
        assertEquals("limit reached", meter.verdict)
        assertEquals(VerdictTone.Critical, meter.verdictTone)
        assertEquals(130L, meter.shown)
        assertEquals(1.0, meter.barFraction, 0.0)
    }

    @Test
    fun `a window just begun has no tick to judge by`() {
        val meter = meterState(window(2.0, 4.9 * hour), FIVE_HOUR_MS, now)
        assertNull(meter.tick)
        assertEquals("", meter.verdict)
    }

    @Test
    fun `a window past its reset reads as awaiting new data`() {
        val meter = meterState(window(80.0, -hour), FIVE_HOUR_MS, now)
        assertFalse(meter.hasData)
        assertEquals("window reset · awaiting new data", meter.verdict)
        assertEquals(0.0, meter.barFraction, 0.0)
        assertEquals("", meter.resetLine)
        assertEquals("window reset, awaiting new data", meter.accessibilityValue)
    }

    @Test
    fun `no reading at all`() {
        val meter = meterState(null, WEEKLY_MS, now)
        assertFalse(meter.hasData)
        assertEquals("", meter.verdict)
        assertEquals("no data yet", meter.accessibilityValue)
    }
}
