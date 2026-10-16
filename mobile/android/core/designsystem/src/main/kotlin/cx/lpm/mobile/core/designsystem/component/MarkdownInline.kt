package cx.lpm.mobile.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Inline markdown — emphasis, strong, strikethrough, code spans, links and backslash
 * escapes — as iOS's inline-only `AttributedString(markdown:)` reads it. Whitespace is
 * kept as written. [code] paints code spans, which carry no font of their own; [link]
 * styles links.
 */
fun markdownInline(text: String, code: SpanStyle, link: SpanStyle): AnnotatedString =
    buildAnnotatedString { InlineParser(this, code, link).parse(text) }

private class InlineParser(
    private val out: AnnotatedString.Builder,
    private val code: SpanStyle,
    private val link: SpanStyle
) {
    fun parse(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            i = when {
                c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE -> {
                    out.append(s[i + 1])
                    i + 2
                }

                c == '`' -> codeSpan(s, i)

                s.startsWith("**", i) || s.startsWith("__", i) -> wrapped(s, i, 2, STRONG)

                s.startsWith("~~", i) -> wrapped(s, i, 2, STRIKE)

                c == '*' || (c == '_' && s.getOrNull(i - 1)?.isLetterOrDigit() != true) ->
                    wrapped(s, i, 1, EMPHASIS)

                c == '[' -> linkAt(s, i)

                else -> {
                    out.append(c)
                    i + 1
                }
            }
        }
    }

    private fun codeSpan(s: String, start: Int): Int {
        val ticks = s.substring(start).takeWhile { it == '`' }.length
        val fence = "`".repeat(ticks)
        val close = s.indexOf(fence, start + ticks)
        if (close < 0) {
            out.append(fence)
            return start + ticks
        }
        var inner = s.substring(start + ticks, close)
        if (inner.length > 2 && inner.startsWith(' ') && inner.endsWith(' ')) {
            inner = inner.substring(1, inner.length - 1)
        }
        out.withStyle(code) { append(inner) }
        return close + ticks
    }

    /** `**strong**`, `*em*`, `~~struck~~`; a delimiter that doesn't close stays as text. */
    private fun wrapped(s: String, start: Int, width: Int, style: SpanStyle): Int {
        val delimiter = s.substring(start, start + width)
        val from = start + width
        val close = s.indexOf(delimiter, from)
        val opens = from < s.length && !s[from].isWhitespace()
        if (close <= from || !opens || s[close - 1].isWhitespace()) {
            out.append(delimiter)
            return from
        }
        val index = out.pushStyle(style)
        parse(s.substring(from, close))
        out.pop(index)
        return close + width
    }

    /** `[text](url)`, tappable; anything else starting with `[` is text. */
    private fun linkAt(s: String, start: Int): Int {
        val textEnd = s.indexOf(']', start + 1)
        val urlEnd = if (textEnd >= 0 && s.getOrNull(textEnd + 1) == '(') {
            s.indexOf(')', textEnd + 2)
        } else {
            -1
        }
        if (urlEnd < 0) {
            out.append('[')
            return start + 1
        }
        val url = s.substring(textEnd + 2, urlEnd).trim()
        val index = out.pushLink(LinkAnnotation.Url(url, TextLinkStyles(link)))
        parse(s.substring(start + 1, textEnd))
        out.pop(index)
        return urlEnd + 1
    }

    private companion object {
        const val ESCAPABLE = "\\`*_{}[]()#+-.!~|<>"
        val STRONG = SpanStyle(fontWeight = FontWeight.Bold)
        val EMPHASIS = SpanStyle(fontStyle = FontStyle.Italic)
        val STRIKE = SpanStyle(textDecoration = TextDecoration.LineThrough)
    }
}
