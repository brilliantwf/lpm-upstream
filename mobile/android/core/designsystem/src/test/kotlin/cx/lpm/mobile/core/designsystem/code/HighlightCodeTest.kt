package cx.lpm.mobile.core.designsystem.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import cx.lpm.mobile.core.designsystem.theme.DarkLpmColors
import cx.lpm.mobile.core.designsystem.theme.LightLpmColors
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightCodeTest {
    private val colors = SyntaxColors(LightLpmColors)

    /** Each run of one color in the highlighted text, as `text` to color. */
    private fun AnnotatedString.runs(): List<Pair<String, Color?>> = spanStyles
        .sortedBy { it.start }
        .map { text.substring(it.start, it.end) to it.item.color }
        .fold(mutableListOf()) { out, (piece, color) ->
            val last = out.lastOrNull()
            if (last != null && last.second == color) {
                out[out.lastIndex] = last.first + piece to color
            } else {
                out += piece to color
            }
            out
        }

    @Test
    fun theTextComesBackUnchangedWithItsEmptyLines() {
        val text = "const a = 1\n\n/* one\n\n two */\nlet b = 'x'\n"
        assertEquals(text, highlightCode(text, "ts", colors).text)
        assertEquals("", highlightCode("", "ts", colors).text)
    }

    @Test
    fun tokensTakeTheSyntaxColors() {
        val runs = highlightCode("const x = \"a\" + 42 // note", "ts", colors).runs()
        assertEquals(
            listOf(
                "const" to colors.keyword,
                " x = " to colors.plain,
                "\"a\"" to colors.text,
                " + " to colors.plain,
                "42" to colors.number,
                " " to colors.plain,
                "// note" to colors.comment
            ),
            runs
        )
    }

    @Test
    fun aBlockCommentRunsAcrossLines() {
        val runs = highlightCode("a /* b\nc */ d", "swift", colors).runs()
        assertEquals(
            listOf("a " to colors.plain, "/* b" to colors.comment) +
                listOf("\n" to colors.plain, "c */" to colors.comment, " d" to colors.plain),
            runs
        )
    }

    @Test
    fun yamlKeysAreColored() {
        val runs = highlightCode("name: web\n  - port: 3000", "yml", colors).runs()
        assertEquals(
            listOf(
                "name" to colors.key,
                ": web\n  - " to colors.plain,
                "port" to colors.key,
                ": " to colors.plain,
                "3000" to colors.number
            ),
            runs
        )
    }

    @Test
    fun anUnknownExtensionStaysPlain() {
        val text = "if true { 1 }\n\n// x"
        val result = highlightCode(text, "txt", colors)
        assertEquals(text, result.text)
        assertEquals(listOf(text to colors.plain), result.runs())
    }

    @Test
    fun darkAppearanceHasItsOwnShades() {
        val dark = SyntaxColors(DarkLpmColors)
        assertEquals(Color(0xFFFF7AB2), dark.keyword)
        assertEquals(Color(0xFF9B2393), colors.keyword)
        assertEquals(DarkLpmColors.label, dark.plain)
    }
}
