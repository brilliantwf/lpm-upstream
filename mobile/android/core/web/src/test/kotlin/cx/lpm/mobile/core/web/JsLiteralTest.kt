package cx.lpm.mobile.core.web

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JsLiteralTest {
    @Test
    fun escapesQuotesBackslashesAndLineBreaks() {
        assertEquals("\"a\\\"b\\\\c\\nd\\re\\tf\"", JsLiteral.string("a\"b\\c\nd\re\tf"))
    }

    @Test
    fun escapesControlCharactersAndSeparators() {
        assertEquals(
            "\"\\u001b[0m\\u0000\\u2028\\u2029\"",
            JsLiteral.string("\u001B[0m\u0000\u2028\u2029")
        )
    }

    @Test
    fun escapesSurrogatesSoEmojiAndLoneHalvesSurvive() {
        assertEquals("\"\\ud83d\\ude00\"", JsLiteral.string("😀"))
        assertEquals("\"\\ud800x\"", JsLiteral.string("\uD800x"))
    }

    @Test
    fun leavesOtherTextAlone() {
        assertEquals("\"café ⏺ </script> 'x'\"", JsLiteral.string("café ⏺ </script> 'x'"))
    }

    @Test
    fun isAJsonStringThatRoundTrips() {
        val text = "# Title\n\n```ts\nconst s = \"\\n\";\n```\r\n\u001B\u2028 😀 é \u0007"
        val literal = JsLiteral.string(text)
        assertEquals(text, Json.parseToJsonElement(literal).jsonPrimitive.content)
        assertFalse(literal.drop(1).dropLast(1).contains(Regex("(?<!\\\\)\"")))
    }

    @Test
    fun base64EncodesUtf8Bytes() {
        val text = "\u001B[32m✓\u001B[0m ⏺ 😀\r\n"
        val encoded = JsLiteral.base64(text)
        assertEquals(text, String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
        assertEquals("", JsLiteral.base64(""))
        assertEquals("4pyT", JsLiteral.base64("✓"))
    }
}
