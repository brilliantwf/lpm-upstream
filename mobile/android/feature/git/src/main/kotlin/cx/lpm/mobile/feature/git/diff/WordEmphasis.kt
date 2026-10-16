package cx.lpm.mobile.feature.git.diff

import cx.lpm.mobile.core.designsystem.code.graphemes
import cx.lpm.mobile.core.designsystem.code.isLetter
import cx.lpm.mobile.core.designsystem.code.isNumber
import cx.lpm.mobile.core.designsystem.code.utf16Length
import java.text.Normalizer

/** A word-level change inside a line: UTF-16 offset and length into its text. */
internal data class WordEmphasis(val location: Int, val length: Int)

/**
 * Pairs each run of consecutive removed lines with the equal-length run of added
 * lines that follows, and marks the differing middle of each pair (a character diff
 * snapped out to word boundaries) for a deeper tint. Keyed by line index.
 */
internal fun computeEmphasis(lines: List<DiffLine>): Map<Int, List<WordEmphasis>> {
    val result = mutableMapOf<Int, MutableList<WordEmphasis>>()
    var i = 0
    val n = lines.size
    while (i < n) {
        if (lines[i].kind != DiffLineKind.Removed) {
            i += 1
            continue
        }
        var r = i
        while (r < n && lines[r].kind == DiffLineKind.Removed) r += 1
        var a = r
        while (a < n && lines[a].kind == DiffLineKind.Added) a += 1
        val removed = r - i
        if (removed == a - r) {
            for (k in 0 until removed) {
                val (old, new) = wordDiff(lines[i + k].text, lines[r + k].text)
                old?.let { result.getOrPut(i + k) { mutableListOf() } += it }
                new?.let { result.getOrPut(r + k) { mutableListOf() } += it }
            }
        }
        i = maxOf(a, i + 1)
    }
    return result
}

private fun wordDiff(old: String, new: String): Pair<WordEmphasis?, WordEmphasis?> {
    val o = graphemes(old)
    val w = graphemes(new)
    if (o.isEmpty() || w.isEmpty()) return null to null
    // Swift compares characters by canonical equivalence: "e\u0301" is "é".
    val ok = o.map(::canonical)
    val wk = w.map(::canonical)
    var p = 0
    while (p < o.size && p < w.size && ok[p] == wk[p]) p += 1
    var s = 0
    while (s < o.size - p && s < w.size - p && ok[o.size - 1 - s] == wk[w.size - 1 - s]) s += 1
    if (p == o.size && p == w.size) return null to null
    while (p > 0 && isWordChar(o[p - 1])) p -= 1
    while (s > 0 && isWordChar(o[o.size - s])) s -= 1
    val oMid = (o.size - s) - p
    val wMid = (w.size - s) - p
    if (oMid <= 0 && wMid <= 0) return null to null
    val oWhole = oMid >= WHOLE_LINE * o.size
    val wWhole = wMid >= WHOLE_LINE * w.size
    if (oWhole && wWhole) return null to null
    val oldRange = if (oMid > 0) range(o, p, o.size - s) else null
    val newRange = if (wMid > 0) range(w, p, w.size - s) else null
    return oldRange to newRange
}

private fun canonical(c: String): String =
    if (c.length == 1 && c[0].code < ASCII_END) c else Normalizer.normalize(c, Normalizer.Form.NFC)

private fun isWordChar(c: String) = c.isLetter() || c.isNumber() || c == "_"

private fun range(chars: List<String>, from: Int, to: Int) =
    WordEmphasis(chars.utf16Length(0, from), chars.utf16Length(from, to))

private const val ASCII_END = 0x80

// A change covering nearly all of both lines isn't worth a deeper tint.
private const val WHOLE_LINE = 0.9
