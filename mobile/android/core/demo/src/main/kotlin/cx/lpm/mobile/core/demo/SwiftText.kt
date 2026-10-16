package cx.lpm.mobile.core.demo

import java.text.BreakIterator
import java.text.Collator
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

// The few Swift String semantics the demo depends on, so text handling matches iOS:
// Characters are extended grapheme clusters ("\r\n" is one), `split` drops empty
// pieces, and Foundation's character sets decide what trimming removes.

/** The string's Swift `Character`s. */
internal fun String.graphemes(): List<String> {
    val out = ArrayList<String>(length)
    forEachGrapheme { start, end -> out.add(substring(start, end)) }
    return out
}

internal fun String.graphemeCount(): Int {
    var count = 0
    forEachGrapheme { _, _ -> count++ }
    return count
}

/** `String(prefix(n))`. */
internal fun String.graphemePrefix(n: Int): String = substring(0, graphemeOffset(0, n))

/** `String(suffix(n))`. */
internal fun String.graphemeSuffix(n: Int): String = substring(graphemeOffset(length, -n))

internal fun String.firstGrapheme(): String? = if (isEmpty()) null else graphemePrefix(1)

internal fun String.lastGrapheme(): String? = if (isEmpty()) null else graphemeSuffix(1)

internal fun String.dropFirstGrapheme(): String = substring(graphemeOffset(0, 1))

internal fun String.dropLastGrapheme(): String = substring(0, graphemeOffset(length, -1))

/** `index(_:offsetBy:limitedBy:)` over Characters, clamped to the string's ends. */
internal fun String.graphemeOffset(from: Int, by: Int): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var index = from
    repeat(kotlin.math.abs(by)) {
        val next = if (by > 0) iterator.following(index) else iterator.preceding(index)
        if (next == BreakIterator.DONE) return index
        index = next
    }
    return index
}

private inline fun String.forEachGrapheme(action: (Int, Int) -> Unit) {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        action(start, end)
        start = end
        end = iterator.next()
    }
}

/** `CharacterSet.whitespaces`: Unicode Zs and tab. */
private fun Char.isSwiftWhitespace() =
    this == '\t' || Character.getType(this) == Character.SPACE_SEPARATOR.toInt()

/** `CharacterSet.whitespacesAndNewlines`: Unicode Z*, U+000A–U+000D and U+0085. */
private fun Char.isSwiftWhitespaceOrNewline() =
    isSwiftWhitespace() || this in '\n'..'\r' || this == '\u0085' ||
        this == '\u2028' || this == '\u2029'

internal fun String.trimmingWhitespaces(): String = trim { it.isSwiftWhitespace() }

internal fun String.trimmingWhitespacesAndNewlines(): String =
    trim { it.isSwiftWhitespaceOrNewline() }

/** `split(separator:maxSplits:)` with Swift's default of omitting empty pieces. */
internal fun String.swiftSplit(separator: Char, maxSplits: Int = Int.MAX_VALUE): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    var index = 0
    while (index < length && out.size < maxSplits) {
        if (this[index] == separator) {
            if (index > start) out.add(substring(start, index))
            start = index + 1
        }
        index++
    }
    if (start < length) out.add(substring(start))
    return out
}

/** `split(separator: "\n", omittingEmptySubsequences: false)`, where "\r\n" is one Character. */
internal fun String.swiftLines(): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    for (i in indices) {
        if (this[i] == '\n' && (i == 0 || this[i - 1] != '\r')) {
            out.add(substring(start, i))
            start = i + 1
        }
    }
    out.add(substring(start))
    return out
}

/** `Character.isLetter || Character.isNumber`, judged by the first scalar like Swift. */
internal fun String.isSwiftLetterOrNumber(): Boolean {
    if (isEmpty()) return false
    val scalar = codePointAt(0)
    return Character.isAlphabetic(scalar) || Character.getNumericValue(scalar) != -1
}

/** Membership in `CharacterSet.alphanumerics`: Unicode L*, M* and N*. */
internal fun isSwiftAlphanumeric(scalar: Int): Boolean =
    Character.getType(scalar) in Character.UPPERCASE_LETTER..Character.OTHER_NUMBER

private val decimalFloat = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
private val hexFloat =
    Regex("[+-]?0[xX]([0-9a-fA-F]+\\.?[0-9a-fA-F]*|\\.[0-9a-fA-F]+)([pP][+-]?\\d+)?")
private val namedFloat = Regex("[+-]?(inf|infinity|nan)", RegexOption.IGNORE_CASE)

/** `Double(s) != nil`. */
internal fun String.isSwiftDouble(): Boolean =
    decimalFloat.matches(this) || hexFloat.matches(this) || namedFloat.matches(this)

/** `localizedCaseInsensitiveCompare`. */
internal val localizedCaseInsensitive: Comparator<String> =
    Collator.getInstance().apply { strength = Collator.SECONDARY }.let { collator ->
        Comparator { a, b -> collator.compare(a, b) }
    }

/** `ISO8601DateFormatter().string(from:)`: whole seconds, UTC. */
internal fun iso8601(instant: Instant): String = instant.truncatedTo(ChronoUnit.SECONDS).toString()

/** `Data(base64Encoded:)`, which wants padded input with no stray characters. */
internal fun decodeBase64(encoded: String): ByteArray? {
    if (encoded.length % 4 != 0) return null
    return runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
}
