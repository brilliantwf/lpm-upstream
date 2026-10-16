package cx.lpm.mobile.core.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwiftTextTest {
    @Test
    fun charactersAreGraphemeClusters() {
        assertEquals(listOf("a", "\r\n", "b"), "a\r\nb".graphemes())
        assertEquals(
            listOf("\uD83D\uDC4D\uD83C\uDFFD", "e\u0301"),
            "\uD83D\uDC4D\uD83C\uDFFDe\u0301".graphemes()
        )
        assertEquals("caf", "café".dropLastGrapheme())
        assertEquals("ab", "ab👍🏽".graphemePrefix(2))
        assertEquals(3, "ab👍🏽".graphemeCount())
    }

    @Test
    fun splitDropsEmptyPiecesAndHonorsMaxSplits() {
        assertEquals(listOf("a", "b"), "  a  b ".swiftSplit(' '))
        assertEquals(listOf("4h", "-8h"), "4h--8h".swiftSplit('-', maxSplits = 1))
        assertEquals(listOf("4h"), "-4h".swiftSplit('-', maxSplits = 1))
        assertEquals(listOf("# t\r\nx", ""), "# t\r\nx\n".swiftLines())
    }

    @Test
    fun trimmingFollowsFoundationCharacterSets() {
        assertEquals("a\n", " \ta\n".trimmingWhitespaces())
        assertEquals("a", "\u2028 a\r\n\u0085".trimmingWhitespacesAndNewlines())
    }

    @Test
    fun doublesParseLikeSwift() {
        listOf("1", "-2.5", ".5", "5.", "1e3", "0x1p3", "inf", "NaN", "+1").forEach {
            assertTrue(it, it.isSwiftDouble())
        }
        listOf("", " 1", "1d", "1f", "abc", "1.2.3").forEach { assertFalse(it, it.isSwiftDouble()) }
    }

    @Test
    fun slugsMatchTheMac() {
        assertEquals("feature-x", slug(" Feature X "))
        assertEquals("hello-world", slug("Hello World!"))
        assertEquals("café-2", slug("Café #2"))
    }
}
