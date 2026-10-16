package cx.lpm.mobile.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownInlineTest {
    private val code = SpanStyle(color = Color.Blue)
    private val link = SpanStyle(color = Color.Red)

    private fun parse(text: String) = markdownInline(text, code, link)

    @Test
    fun markersBecomeStylesAndLeaveTheText() {
        val s = parse("A **bold**, *em* and ~~gone~~ `x * y` word")
        assertEquals("A bold, em and gone x * y word", s.text)
        val styles = s.spanStyles.associate { s.text.substring(it.start, it.end) to it.item }
        assertEquals(FontWeight.Bold, styles.getValue("bold").fontWeight)
        assertEquals(FontStyle.Italic, styles.getValue("em").fontStyle)
        assertEquals(TextDecoration.LineThrough, styles.getValue("gone").textDecoration)
        assertEquals(code, styles.getValue("x * y"))
    }

    @Test
    fun linksAreTappableAndNestStyles() {
        val s = parse("See [the **docs**](https://lpm.cx/docs).")
        assertEquals("See the docs.", s.text)
        val url = s.getLinkAnnotations(0, s.length).single()
        assertEquals("https://lpm.cx/docs", (url.item as LinkAnnotation.Url).url)
        assertEquals("the docs", s.text.substring(url.start, url.end))
    }

    @Test
    fun unclosedMarkersAndEscapesStayLiteral() {
        assertEquals("2 * 3 = 6", parse("2 * 3 = 6").text)
        assertEquals("snake_case_name", parse("snake_case_name").text)
        assertEquals("*not em*", parse("\\*not em\\*").text)
        assertEquals("[just brackets]", parse("[just brackets]").text)
        assertEquals("`open", parse("`open").text)
    }
}
