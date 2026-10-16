package cx.lpm.mobile.feature.usage.stats

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class RelativeTimeTest {
    private val now = 1_792_065_600_000L
    private val hour = 3_600_000L

    private fun ago(millis: Long) = relativeUsageTime(now - millis, now, ZoneOffset.UTC)

    @Test
    fun `the largest unit that differs, named for one day or more`() {
        assertEquals("now", ago(0))
        assertEquals("5 minutes ago", ago(5 * 60_000))
        assertEquals("2 hours ago", ago(2 * hour))
        assertEquals("yesterday", ago(30 * hour))
        assertEquals("3 days ago", ago(72 * hour))
        assertEquals("last week", ago(8 * 24 * hour))
        assertEquals("in 2 hours", relativeUsageTime(now + 2 * hour, now, ZoneOffset.UTC))
    }
}
