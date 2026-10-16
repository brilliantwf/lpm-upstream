package cx.lpm.mobile.feature.files.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteCountTest {
    @Test
    fun `sizes read as iOS's file byte counts`() {
        assertEquals("Zero KB", ByteCount.format(0))
        assertEquals("1 byte", ByteCount.format(1))
        assertEquals("999 bytes", ByteCount.format(999))
        assertEquals("1 KB", ByteCount.format(1_000))
        assertEquals("262 KB", ByteCount.format(262_144))
        assertEquals("1 MB", ByteCount.format(999_999))
        assertEquals("1 MB", ByteCount.format(1_000_000))
        assertEquals("1.5 MB", ByteCount.format(1_500_000))
        assertEquals("12.3 MB", ByteCount.format(12_345_678))
        assertEquals("1.07 GB", ByteCount.format(1L shl 30))
    }
}
