package cx.lpm.mobile.core.designsystem.code

/** What a run of code is, which picks its color. */
enum class SyntaxToken { Plain, Keyword, Text, Comment, Number, Key }

/** A run of a line's code and what it is. */
data class TokenRun(val text: String, val token: SyntaxToken)

/**
 * A best-effort, dependency-free per-line syntax highlighter keyed by file extension
 * (iOS `DiffHighlighter`): line and block comments, string literals, numbers and
 * keywords. Feed it a file's lines in order: block-comment state carries from one
 * line to the next. Unknown extensions stay plain. For a whole text, [highlightCode].
 */
class SyntaxHighlighter(ext: String) {
    private val spec = LanguageSpec.forExtension(ext)
    private var inBlockComment = false

    /** A diff row's runs: an empty line is a space, so its row keeps its height. */
    fun highlight(line: String): List<TokenRun> {
        if (spec.isPlain || (line.isEmpty() && !inBlockComment)) {
            return listOf(TokenRun(line.ifEmpty { " " }, SyntaxToken.Plain))
        }
        return tokenize(line)
    }

    /** A document line's runs, which join back into exactly [line]: an empty one has none. */
    fun documentRuns(line: String): List<TokenRun> = when {
        !spec.isPlain -> tokenize(line)
        line.isEmpty() -> emptyList()
        else -> listOf(TokenRun(line, SyntaxToken.Plain))
    }

    private fun tokenize(line: String): List<TokenRun> {
        val chars = graphemes(line)
        val n = chars.size
        val runs = mutableListOf<TokenRun>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) runs += TokenRun(plain.toString(), SyntaxToken.Plain)
            plain.clear()
        }

        var i = 0
        // YAML mapping keys: color `key:` at the head of a line, as the desktop editor does.
        if (spec.mapKeys && !inBlockComment) yamlKeyRuns(chars, runs)?.let { i = it }
        while (i < n) {
            if (inBlockComment) {
                val comment = StringBuilder()
                val close = spec.blockClose
                while (i < n) {
                    if (close != null && match(chars, i, close)) {
                        val length = graphemes(close).size
                        chars.subList(i, i + length).forEach(comment::append)
                        i += length
                        inBlockComment = false
                        break
                    }
                    comment.append(chars[i])
                    i += 1
                }
                flushPlain()
                runs += TokenRun(comment.toString(), SyntaxToken.Comment)
                continue
            }

            val c = chars[i]
            if (spec.lineComments.any { match(chars, i, it) }) {
                flushPlain()
                runs += TokenRun(chars.subList(i, n).joinToString(""), SyntaxToken.Comment)
                break
            }
            val open = spec.blockOpen
            if (open != null && match(chars, i, open)) {
                flushPlain()
                runs += TokenRun(open, SyntaxToken.Comment)
                i += graphemes(open).size
                inBlockComment = true
                continue
            }
            if (c in spec.strings) {
                val text = StringBuilder(c)
                i += 1
                while (i < n) {
                    val d = chars[i]
                    if (d == "\\" && i + 1 < n) {
                        text.append(d).append(chars[i + 1])
                        i += 2
                        continue
                    }
                    text.append(d)
                    i += 1
                    if (d == c) break
                }
                flushPlain()
                runs += TokenRun(text.toString(), SyntaxToken.Text)
                continue
            }
            if (c.isNumber() && (i == 0 || !isIdent(chars[i - 1])) && isNumberChar(c)) {
                val number = StringBuilder()
                while (i < n && isNumberChar(chars[i])) number.append(chars[i++])
                flushPlain()
                runs += TokenRun(number.toString(), SyntaxToken.Number)
                continue
            }
            if (isIdentStart(c)) {
                val word = StringBuilder()
                while (i < n && isIdent(chars[i])) word.append(chars[i++])
                val text = word.toString()
                flushPlain()
                runs +=
                    TokenRun(
                        text,
                        if (text in
                            spec.keywords
                        ) {
                            SyntaxToken.Keyword
                        } else {
                            SyntaxToken.Plain
                        }
                    )
                continue
            }
            plain.append(c)
            i += 1
        }
        flushPlain()
        return runs
    }

    private companion object {
        fun isIdentStart(c: String) = c.isLetter() || c == "_" || c == "$"

        fun isIdent(c: String) = c.isLetter() || c.isNumber() || c == "_" || c == "$"

        fun isNumberChar(c: String) = c.isHexDigit() || c == "." || c == "_" || c in "xXoObB"

        fun match(chars: List<String>, i: Int, token: String): Boolean {
            val t = graphemes(token)
            if (i + t.size > chars.size) return false
            return t.indices.all { chars[i + it] == t[it] }
        }

        /**
         * A YAML mapping key at the head of the line — `<indent>(- )*<key>:` with the
         * colon ending the line or followed by whitespace: adds the indent and list
         * markers as plain, the key, and the colon, and returns where the value
         * starts. Null for a bare scalar, a comment, a quoted key or `---`.
         */
        fun yamlKeyRuns(chars: List<String>, runs: MutableList<TokenRun>): Int? {
            val n = chars.size
            var i = 0
            val prefix = StringBuilder()
            while (i < n && chars[i] == " ") prefix.append(chars[i++])
            while (i + 1 < n && chars[i] == "-" && chars[i + 1] == " ") {
                prefix.append("- ")
                i += 2
                while (i < n && chars[i] == " ") prefix.append(chars[i++])
            }
            if (i >= n || chars[i] in setOf("#", "\"", "'", "-")) return null
            val key = StringBuilder()
            var j = i
            while (j < n && chars[j] != ":") key.append(chars[j++])
            if (j >= n) return null
            val afterColon = j + 1
            if (afterColon < n && chars[afterColon] != " " && chars[afterColon] != "\t") return null
            if (key.all { it == ' ' || it == '\t' || Character.isSpaceChar(it) }) return null
            if (prefix.isNotEmpty()) runs += TokenRun(prefix.toString(), SyntaxToken.Plain)
            runs += TokenRun(key.toString(), SyntaxToken.Key)
            runs += TokenRun(":", SyntaxToken.Plain)
            return afterColon
        }
    }
}
