package cx.lpm.mobile.core.designsystem.code

import java.text.BreakIterator

/**
 * The text as Swift `Character`s (grapheme clusters), so a port that walks a line
 * character by character steps over the same units iOS does and never splits a
 * surrogate pair or a combining sequence.
 */
fun graphemes(text: String): List<String> {
    if (text.all { it.code < ASCII_END }) return text.map(Char::toString)
    val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
    val out = ArrayList<String>(text.length)
    var start = breaks.first()
    var end = breaks.next()
    while (end != BreakIterator.DONE) {
        out += text.substring(start, end)
        start = end
        end = breaks.next()
    }
    return out
}

/** Swift's `Character.isLetter`, judged by the first scalar. */
fun String.isLetter(): Boolean = isNotEmpty() && Character.isLetter(codePointAt(0))

/** Swift's `Character.isNumber`: any numeric scalar, not only decimal digits. */
fun String.isNumber(): Boolean {
    if (isEmpty()) return false
    return when (Character.getType(codePointAt(0)).toByte()) {
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }
}

/** Swift's `Character.isHexDigit`: ASCII hex digits and their fullwidth forms. */
internal fun String.isHexDigit(): Boolean {
    if (length != 1) return false
    val c = this[0]
    return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' ||
        c in '０'..'９' || c in 'Ａ'..'Ｆ' || c in 'ａ'..'ｆ'
}

/** The UTF-16 length of graphemes `[from, to)`, an `NSRange` offset or length. */
fun List<String>.utf16Length(from: Int, to: Int): Int {
    var length = 0
    for (i in from until to) length += this[i].length
    return length
}

private const val ASCII_END = 0x80
