package cx.lpm.mobile.feature.usage.stats

import cx.lpm.mobile.core.model.UsageBreakdown
import cx.lpm.mobile.core.model.UsageDaily
import cx.lpm.mobile.core.model.UsageTokens
import java.util.Locale
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

class StatsFormatTest {
    private fun tokens(
        input: Long = 0,
        cacheCreation: Long = 0,
        cacheCreation1h: Long = 0,
        cacheRead: Long = 0,
        output: Long = 0
    ) = UsageTokens(
        inputTokens = input,
        cachedInputTokens = cacheCreation + cacheRead,
        cacheCreationInputTokens = cacheCreation,
        cacheCreation1hInputTokens = cacheCreation1h,
        cacheReadInputTokens = cacheRead,
        outputTokens = output,
        reasoningTokens = 0,
        totalTokens = input + output
    )

    @Test
    fun `token counts compact like the desktop, ties to even as printf rounds`() {
        assertEquals("999", formatTokenCount(999))
        assertEquals("1000K", formatTokenCount(999_999))
        assertEquals("1.0K", formatTokenCount(1_000))
        assertEquals("1.2K", formatTokenCount(1_250))
        assertEquals("1.4K", formatTokenCount(1_350))
        assertEquals("12K", formatTokenCount(12_500))
        assertEquals("5.0M", formatTokenCount(5_000_000))
        assertEquals("16M", formatTokenCount(16_500_000))
        assertEquals("127M", formatTokenCount(127_400_000))
        assertEquals("2.5B", formatTokenCount(2_500_000_000))
        assertEquals("12B", formatTokenCount(12_000_000_000))
    }

    @Test
    fun `percents and periods read as on iOS`() {
        assertEquals("50%", formatPercent(0.5))
        assertEquals("12.3%", formatPercent(0.1234, dp = 1))
        assertEquals("0%", formatPercent(Double.NaN))
        assertEquals("today", usagePeriodLabel(1))
        assertEquals("all time", usagePeriodLabel(0))
        assertEquals("the last 7 days", usagePeriodLabel(7))
    }

    @Test
    fun `day buckets parse and print as month and day`() {
        assertEquals("Oct 8", shortUsageDate("2026-10-08"))
        assertEquals("garbage", shortUsageDate("garbage"))
        assertNull(parseUsageDate("2026-13-01"))
        assertNull(parseUsageDate("2026-10"))
    }

    @Test
    fun `shares and the busiest day`() {
        val t = tokens(input = 1_000, cacheRead = 830, output = 200)
        assertEquals(0.83, cacheShare(t), 1e-9)
        assertEquals(0.0, reasoningShare(t), 1e-9)
        assertEquals(0.0, cacheShare(tokens()), 1e-9)
        val days =
            listOf(UsageDaily("a", 1, 0, 5), UsageDaily("b", 0, 0, 9), UsageDaily("c", 9, 0, 9))
        assertEquals("b", mostActiveDay(days)?.date)
        assertNull(mostActiveDay(listOf(UsageDaily("a", 0, 0, 0))))
    }

    @Test
    fun `models are priced by the first matching rate`() {
        assertEquals(4.0, pickRate("claude-opus-5-5-20261001").input, 0.0)
        assertEquals(5.0, pickRate("claude-opus-4-6").input, 0.0)
        assertEquals(6.0, pickRate("claude-opus-4-6").fast, 0.0)
        assertEquals(2.0, pickRate("GPT-5.6-Terra").input, 0.0)
        assertEquals(4.0, pickRate("mystery", provider = "codex").input, 0.0)
        assertEquals(5.0, pickRate("mystery").input, 0.0)
    }

    @Test
    fun `each token bucket is priced separately, fast mode scaled`() {
        val t = tokens(
            input = 1_000_000,
            cacheCreation = 200_000,
            cacheCreation1h = 50_000,
            cacheRead = 500_000,
            output = 100_000
        )
        assertEquals(5.6875, estimateModelCost(t, "claude-opus-4-6"), 1e-9)
        assertEquals(34.125, estimateModelCost(t, "claude-opus-4-6", fast = true), 1e-9)
        val models = listOf(
            UsageBreakdown("claude-opus-4-6", "", 1, t, "claude", fast = false),
            UsageBreakdown("claude-opus-4-6", "", 1, t, "claude", fast = true)
        )
        assertEquals(39.8125, estimateTotalCost(models), 1e-9)
    }

    @Test
    fun `dollars round like the desktop`() {
        assertEquals("$0", formatUsd(0.0))
        assertEquals("$1.23", formatUsd(1.234))
        assertEquals("$184", formatUsd(184.4))
        assertEquals("$1,235", formatUsd(1_234.5))
    }

    companion object {
        private val saved = Locale.getDefault()

        @JvmStatic
        @BeforeClass
        fun english() = Locale.setDefault(Locale.US)

        @JvmStatic
        @AfterClass
        fun restore() = Locale.setDefault(saved)
    }
}
