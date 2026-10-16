package cx.lpm.mobile.core.web

import java.util.Base64

/** Values written into scripts handed to `evaluateJavascript`. */
internal object JsLiteral {
    /**
     * A double-quoted JavaScript string literal for [value]. Only JSON escapes are
     * used, so the result is also a JSON string. Line and paragraph separators and
     * every surrogate are escaped, so no character can end the literal or be
     * mangled on its way into the page.
     */
    fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.needsUnicodeEscape() -> append(unicodeEscape(c))
                else -> append(c)
            }
        }
        append('"')
    }

    private fun Char.needsUnicodeEscape() =
        this < ' ' || this == '\u2028' || this == '\u2029' || isSurrogate()

    private fun unicodeEscape(c: Char) = "\\u" + c.code.toString(16).padStart(4, '0')

    /** base64 of the UTF-8 bytes, as the pages' `atob` + TextDecoder expect. */
    fun base64(text: String): String = base64(text.toByteArray(Charsets.UTF_8))

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
