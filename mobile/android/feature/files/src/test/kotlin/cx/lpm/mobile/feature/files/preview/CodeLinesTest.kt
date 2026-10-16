package cx.lpm.mobile.feature.files.preview

import cx.lpm.mobile.core.designsystem.code.SyntaxColors
import cx.lpm.mobile.core.designsystem.theme.LightLpmColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeLinesTest {
    @Test
    fun `a text splits at each newline, empty lines kept`() {
        val lines = CodeLines("a\n\nb\n")
        assertEquals(4, lines.count)
        assertEquals(listOf("a", "", "b", ""), List(lines.count) { lines[it] })
    }

    @Test
    fun `a CRLF line ends before its carriage return`() {
        val lines = CodeLines("one\r\ntwo\r\n\r\n")
        assertEquals(listOf("one", "two", "", ""), List(lines.count) { lines[it] })
    }

    @Test
    fun `the highlighted lines carry the same text`() {
        val text = "const a = 1 // one\r\n/* two\nlines */\nlet b = \"x\""
        val lines = CodeLines(text)
        val styled = lines.highlighted("ts", SyntaxColors(LightLpmColors))
        assertEquals(List(lines.count) { lines[it] }, styled.map { it.text })
        assertEquals(true, styled[0].spanStyles.isNotEmpty())
    }
}
