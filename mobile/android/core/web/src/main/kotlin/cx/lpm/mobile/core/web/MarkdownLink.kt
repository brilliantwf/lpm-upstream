package cx.lpm.mobile.core.web

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Where a link or image in a Markdown file points, read the way the desktop's
 * Files preview reads it: out of the app, within the page, or at another file —
 * relative to the Markdown file's own folder, or to the project root when it
 * starts with "/".
 */
sealed interface MarkdownLink {
    data class External(val url: String) : MarkdownLink

    data object Anchor : MarkdownLink

    /** Readings of the path, most likely first, to try in order. */
    data class File(val paths: List<String>) : MarkdownLink

    companion object {
        fun resolve(href: String, filePath: String): MarkdownLink {
            if (href.startsWith("//")) return External("https:$href")
            if (hasScheme(href)) return External(href)
            val raw = href.takeWhile { it != '#' && it != '?' }
            val target = percentDecoded(raw) ?: raw
            if (target.isEmpty()) return Anchor
            // GitHub reads a leading "/" as the repository root; on a Mac it can just
            // as well be a real absolute path, so that reading comes second.
            if (target.startsWith(
                    "/"
                )
            ) {
                return File(listOf(normalize(target.drop(1)), normalize(target)))
            }
            val folder = parentPath(filePath)
            return File(listOf(normalize(if (folder.isEmpty()) target else "$folder/$target")))
        }

        private fun hasScheme(href: String): Boolean {
            val colon = href.indexOf(':')
            val first = href.firstOrNull() ?: return false
            if (colon < 0 || !first.isAsciiLetter()) return false
            return href.take(colon).all { it.isAsciiLetter() || it in '0'..'9' || it in "+.-" }
        }

        private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

        private fun normalize(path: String): String {
            val absolute = path.startsWith("/")
            val out = ArrayList<String>()
            for (segment in path.split('/')) {
                when (segment) {
                    "", "." -> continue

                    ".." -> {
                        val last = out.lastOrNull()
                        if (last != null && last != ".." && last != "~") {
                            out.removeAt(out.lastIndex)
                        } else if (!absolute) {
                            out += segment
                        }
                    }

                    else -> out += segment
                }
            }
            return (if (absolute) "/" else "") + out.joinToString("/")
        }

        /** NSString's `deletingLastPathComponent`. */
        private fun parentPath(path: String): String {
            if (path.isEmpty()) return ""
            val trimmed = path.trimEnd('/')
            if (trimmed.isEmpty()) return "/"
            val slash = trimmed.lastIndexOf('/')
            if (slash < 0) return ""
            return trimmed.take(slash).trimEnd('/').ifEmpty { "/" }
        }

        /** Swift's `removingPercentEncoding`: null on a malformed escape or invalid UTF-8. */
        private fun percentDecoded(text: String): String? {
            if ('%' !in text) return text
            val bytes = ByteArrayOutputStream(text.length)
            var i = 0
            while (i < text.length) {
                if (text[i] == '%') {
                    if (i + 2 > text.lastIndex) return null
                    val hi = Character.digit(text[i + 1], 16)
                    val lo = Character.digit(text[i + 2], 16)
                    if (hi < 0 || lo < 0) return null
                    bytes.write(hi * 16 + lo)
                    i += 3
                } else {
                    val end = i + Character.charCount(text.codePointAt(i))
                    bytes.write(text.substring(i, end).toByteArray(Charsets.UTF_8))
                    i = end
                }
            }
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }
    }
}
