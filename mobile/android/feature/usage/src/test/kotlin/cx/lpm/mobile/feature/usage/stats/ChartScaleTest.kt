package cx.lpm.mobile.feature.usage.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartScaleTest {
    @Test
    fun `the token axis rounds up to whole ticks of 1, 2 or 5`() {
        assertEquals(
            TokenScale(20_000_000, listOf(0L, 5_000_000, 10_000_000, 15_000_000, 20_000_000)),
            tokenScale(16_500_000)
        )
        assertEquals(TokenScale(4_000, listOf(0L, 1_000, 2_000, 3_000, 4_000)), tokenScale(3_700))
        assertEquals(TokenScale(10, listOf(0L, 2, 4, 6, 8, 10)), tokenScale(9))
        assertEquals(TokenScale(1, listOf(0L, 1)), tokenScale(0))
    }

    @Test
    fun `the date axis marks every day, every other day, or every week`() {
        assertEquals(1, dayStride(1))
        assertEquals(1, dayStride(7))
        assertEquals(2, dayStride(14))
        assertEquals(7, dayStride(15))
        assertEquals(7, dayStride(28))
    }
}
