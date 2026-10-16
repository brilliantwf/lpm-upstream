package cx.lpm.mobile.feature.git.diff

enum class DiffLineKind { Added, Removed, Hunk, Context }

/**
 * One line of a parsed diff: the code with its +/- prefix stripped and the hunk's
 * common indent trimmed (for a hunk line, the header's trailing context), and the
 * gutter numbers — the old file's for removed and context lines, the new file's
 * for added and context lines. A hunk line carries its compact "@@ <new-start>"
 * label and how many leading whitespace columns its hunk had trimmed.
 */
data class DiffLine(
    val id: Int,
    val text: String,
    val kind: DiffLineKind,
    val hunkLabel: String = "",
    val indentHint: Int = 0,
    val oldNumber: Int? = null,
    val newNumber: Int? = null
)

/**
 * Splits a unified diff into lines hunk by hunk (iOS `ParsedDiff.parse`): file
 * headers and "\ No newline" markers dropped, each hunk's common indentation trimmed,
 * old and new line numbers counted from the hunk header.
 */
internal fun parseDiffLines(diff: String): List<DiffLine> {
    val raw = diff.replace("\r\n", "\n").split("\n").toMutableList()
    // The newline ending the diff leaves an empty piece behind, which would render
    // as a phantom numbered context line.
    if (raw.last() == "") raw.removeAt(raw.lastIndex)
    val out = mutableListOf<DiffLine>()
    var i = 0
    while (i < raw.size) {
        val line = raw[i]
        if (!line.startsWith("@@")) {
            i += 1
            continue
        }
        val header = parseHunkHeader(line)
        val codes = mutableListOf<Pair<DiffLineKind, String>>()
        var j = i + 1
        while (j < raw.size) {
            val l = raw[j]
            if (l.startsWith("@@")) break
            if (!isFileHeader(l) && !l.startsWith("\\")) codes += classify(l)
            j += 1
        }
        val indent = commonIndent(codes.map { it.second }.filterNot(::isBlank))
        out += DiffLine(
            id = out.size,
            text = header.context,
            kind = DiffLineKind.Hunk,
            hunkLabel = if (header.newStart > 0) "@@ ${header.newStart}" else "@@",
            indentHint = indent.length
        )
        var oldNo = header.oldStart
        var newNo = header.newStart
        for ((kind, content) in codes) {
            val old = if (kind != DiffLineKind.Added) oldNo++ else null
            val new = if (kind != DiffLineKind.Removed) newNo++ else null
            out += DiffLine(
                id = out.size,
                text = stripIndent(content, indent),
                kind = kind,
                oldNumber = old,
                newNumber = new
            )
        }
        i = j
    }
    return out
}

/**
 * The diff reduced to hunk headers and their +/- lines (file-header metadata
 * dropped), for pasting into an agent prompt as a ```diff block.
 */
fun promptDiff(diff: String): String = diff.replace("\r\n", "\n").split("\n").filterNot {
    isFileHeader(it) || it.startsWith("\\")
}.joinToString("\n")

private fun classify(line: String): Pair<DiffLineKind, String> = when {
    line.startsWith("+") -> DiffLineKind.Added to line.substring(1)
    line.startsWith("-") -> DiffLineKind.Removed to line.substring(1)
    line.startsWith(" ") -> DiffLineKind.Context to line.substring(1)
    else -> DiffLineKind.Context to line
}

private fun isBlank(s: String) = s.all { it == ' ' || it == '\t' }

private fun leadingWhitespace(s: String) = s.takeWhile { it == ' ' || it == '\t' }

private fun commonIndent(lines: List<String>): String {
    var prefix = lines.firstOrNull()?.let(::leadingWhitespace) ?: return ""
    for (line in lines.drop(1)) {
        prefix = prefix.commonPrefixWith(leadingWhitespace(line))
        if (prefix.isEmpty()) break
    }
    return prefix
}

private fun stripIndent(content: String, indent: String): String = when {
    indent.isEmpty() -> content
    content.startsWith(indent) -> content.substring(indent.length)
    isBlank(content) -> ""
    else -> content
}

private val fileHeaderPrefixes = listOf(
    "diff --git", "index ", "--- ", "+++ ", "new file", "deleted file", "rename ", "copy ",
    "similarity ", "dissimilarity ", "old mode", "new mode"
)

private fun isFileHeader(line: String) = fileHeaderPrefixes.any(line::startsWith)

private class HunkHeader(val oldStart: Int, val newStart: Int, val context: String)

private fun parseHunkHeader(line: String): HunkHeader {
    val open = line.indexOf("@@")
    val close = if (open < 0) -1 else line.indexOf("@@", open + 2)
    if (close < 0) return HunkHeader(0, 0, "")
    val middle = line.substring(open + 2, close)
    val context = line.substring(close + 2).trim { it == '\t' || Character.isSpaceChar(it) }
    fun number(after: Char): Int {
        val at = middle.indexOf(after)
        if (at < 0) return 0
        return middle.substring(at + 1).takeWhile { it in '0'..'9' }.toIntOrNull() ?: 0
    }
    return HunkHeader(number('-'), number('+'), context)
}
