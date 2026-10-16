package cx.lpm.mobile.feature.git.diff

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.designsystem.code.SyntaxHighlighter
import cx.lpm.mobile.core.designsystem.code.SyntaxToken
import cx.lpm.mobile.core.designsystem.code.TokenRun

enum class DiffBlockKind { Hunk, Code }

/** A run of a block's code in a syntax color: `[start, end)` in UTF-16. */
data class TokenSpan(val start: Int, val end: Int, val token: SyntaxToken)

/** A word-level change in a block's code, tinted deeper on an [added] or removed row. */
data class EmphasisSpan(val start: Int, val end: Int, val added: Boolean)

/**
 * One render unit: a hunk separator, or a run of at most 48 code lines merged into
 * one gutter text (line numbers) and one code text (a +/- marker, the code padded
 * to its run's longest line), with each row's kind for the tint behind it.
 */
@Immutable
data class DiffBlock(
    val id: Int,
    val kind: DiffBlockKind,
    val hunkLabel: String = "",
    val hunkContext: String = "",
    val indentHint: Int = 0,
    val gutter: String = "",
    val code: String = "",
    val tokens: List<TokenSpan> = emptyList(),
    val emphasis: List<EmphasisSpan> = emptyList(),
    val rows: List<DiffLineKind> = emptyList()
) {
    val lineCount: Int get() = rows.size
}

/**
 * A diff parsed for the review screen (iOS `ParsedDiff`): per-hunk blocks for
 * rendering, plus the flat lines for counts. Built off the main thread, once per diff.
 */
@Immutable
class ParsedDiff(
    val lines: List<DiffLine>,
    val blocks: List<DiffBlock>,
    val addedCount: Int,
    val removedCount: Int
) {
    companion object {
        /** The largest number of code lines in one block, so no text lays out thousands at once. */
        const val CHUNK_LIMIT = 48

        fun parse(diff: String, ext: String): ParsedDiff {
            val base = parseDiffLines(diff)
            val emphasis = computeEmphasis(base)
            val highlighter = SyntaxHighlighter(ext)
            val code = base.map {
                if (it.kind ==
                    DiffLineKind.Hunk
                ) {
                    null
                } else {
                    highlighter.highlight(it.text)
                }
            }
            val maxNumber = base.maxOfOrNull { maxOf(it.oldNumber ?: 0, it.newNumber ?: 0) } ?: 0
            val digits = maxOf(2, maxNumber.toString().length)
            return ParsedDiff(
                lines = base,
                blocks = BlockBuilder(base, code, emphasis, digits).build(),
                addedCount = base.count { it.kind == DiffLineKind.Added },
                removedCount = base.count { it.kind == DiffLineKind.Removed }
            )
        }
    }
}

private class BlockBuilder(
    private val base: List<DiffLine>,
    private val code: List<List<TokenRun>?>,
    private val emphasis: Map<Int, List<WordEmphasis>>,
    private val digits: Int
) {
    fun build(): List<DiffBlock> {
        val blocks = mutableListOf<DiffBlock>()
        var i = 0
        while (i < base.size) {
            val line = base[i]
            if (line.kind == DiffLineKind.Hunk) {
                blocks += DiffBlock(
                    id = blocks.size,
                    kind = DiffBlockKind.Hunk,
                    hunkLabel = line.hunkLabel,
                    hunkContext = line.text,
                    indentHint = line.indentHint
                )
                i += 1
                continue
            }
            var j = i
            while (j < base.size && base[j].kind != DiffLineKind.Hunk) j += 1
            // Every chunk of a run scrolls the same width: its longest line's.
            val width = (i until j).maxOf { 1 + codeLength(it) }
            var c = i
            while (c < j) {
                val end = minOf(c + ParsedDiff.CHUNK_LIMIT, j)
                blocks += codeBlock(blocks.size, c until end, width)
                c = end
            }
            i = j
        }
        return blocks
    }

    private fun codeLength(index: Int): Int =
        code[index]?.sumOf { it.text.length } ?: base[index].text.length

    private fun codeBlock(id: Int, range: IntRange, width: Int): DiffBlock {
        val text = StringBuilder()
        val tokens = mutableListOf<TokenSpan>()
        val marks = mutableListOf<EmphasisSpan>()
        for (index in range) {
            val line = base[index]
            val start = text.length
            text.append(marker(line.kind))
            for (run in code[index] ?: listOf(TokenRun(line.text, SyntaxToken.Plain))) {
                val from = text.length
                text.append(run.text)
                if (run.token !=
                    SyntaxToken.Plain
                ) {
                    tokens += TokenSpan(from, text.length, run.token)
                }
            }
            repeat(width - (text.length - start)) { text.append(' ') }
            val length = text.length - start
            for (r in emphasis[index].orEmpty()) {
                val from = r.location + 1
                if (from + r.length <= length) {
                    marks += EmphasisSpan(
                        start + from,
                        start + from + r.length,
                        line.kind == DiffLineKind.Added
                    )
                }
            }
            if (index != range.last) text.append('\n')
        }
        return DiffBlock(
            id = id,
            kind = DiffBlockKind.Code,
            gutter = range.joinToString("\n") { gutterLine(base[it]) },
            code = text.toString(),
            tokens = tokens,
            emphasis = marks,
            rows = range.map { base[it].kind }
        )
    }

    private fun gutterLine(line: DiffLine): String {
        val number = if (line.kind == DiffLineKind.Removed) line.oldNumber else line.newNumber
        return number?.toString().orEmpty().padStart(digits) + "  "
    }

    private fun marker(kind: DiffLineKind) = when (kind) {
        DiffLineKind.Added -> "+"
        DiffLineKind.Removed -> "-"
        else -> " "
    }
}
