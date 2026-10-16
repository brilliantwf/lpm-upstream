package cx.lpm.mobile.feature.notes.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesFormatTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = NotesClock(now, ZoneOffset.UTC, Locale.US)

    private fun minutesAgo(minutes: Long) = now.minusSeconds(minutes * 60).toEpochMilli()

    @Test
    fun `relative times take the largest whole calendar unit, abbreviated`() {
        assertEquals("22m ago", clock.relative(minutesAgo(22)))
        assertEquals("1h ago", clock.relative(minutesAgo(119)))
        assertEquals("1d ago", clock.relative(minutesAgo(2_600)))
        assertEquals("5d ago", clock.relative(minutesAgo(8_400)))
        assertEquals("1w ago", clock.relative(minutesAgo(15_800)))
        assertEquals("2mo ago", clock.relative(minutesAgo(90_000)))
        assertEquals("1y ago", clock.relative(minutesAgo(600_000)))
        assertEquals("5s ago", clock.relative(now.toEpochMilli() - 5_000))
        assertEquals("in 3m", clock.relative(now.toEpochMilli() + 180_000))
        assertEquals("in 0s", clock.relative(now.toEpochMilli()))
        assertEquals("", clock.relative(0))
    }

    @Test
    fun `days read relative near today and as a medium date beyond`() {
        assertEquals("Today", clock.dayLabel(LocalDate.of(2026, 10, 15)))
        assertEquals("Yesterday", clock.dayLabel(LocalDate.of(2026, 10, 14)))
        assertEquals("Oct 5, 2026", clock.dayLabel(LocalDate.of(2026, 10, 5)))
        assertEquals("", clock.clockTime(0))
        assertEquals("12:00", clock.clockTime(now.toEpochMilli()).take(5))
    }

    @Test
    fun `sizes read like the file style byte counter`() {
        assertEquals("Zero KB", noteSizeText(0, Locale.US))
        assertEquals("1 byte", noteSizeText(1, Locale.US))
        assertEquals("512 bytes", noteSizeText(512, Locale.US))
        assertEquals("245 KB", noteSizeText(245_123, Locale.US))
        assertEquals("1.2 MB", noteSizeText(1_234_567, Locale.US))
        assertEquals("8 MB", noteSizeText(8_000_000, Locale.US))
        assertEquals("1.5 GB", noteSizeText(1_500_000_000, Locale.US))
    }
}
