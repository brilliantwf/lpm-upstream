package cx.lpm.mobile.feature.speech.text

/**
 * Character references (`&amp;`, `&#8594;`), which Foundation's markdown parser
 * decodes and the inline renderer leaves as written.
 */
internal object HtmlEntities {
    fun decode(text: String): String {
        if ('&' !in text) return text
        return REFERENCE.replace(text) { match ->
            val name = match.groupValues[1]
            val cp = when {
                name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)
                name.startsWith("#") -> name.drop(1).toIntOrNull()
                else -> NAMED[name]
            }
            if (cp == null || !Character.isValidCodePoint(cp) || cp == 0) {
                match.value
            } else {
                String(Character.toChars(cp))
            }
        }
    }

    private val REFERENCE = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});")

    private val NAMED = mapOf(
        "amp" to 0x26, "lt" to 0x3C, "gt" to 0x3E, "quot" to 0x22, "apos" to 0x27,
        "nbsp" to 0xA0, "copy" to 0xA9, "reg" to 0xAE, "trade" to 0x2122, "hellip" to 0x2026,
        "mdash" to 0x2014, "ndash" to 0x2013, "lsquo" to 0x2018, "rsquo" to 0x2019,
        "ldquo" to 0x201C, "rdquo" to 0x201D, "laquo" to 0xAB, "raquo" to 0xBB,
        "bull" to 0x2022, "middot" to 0xB7, "deg" to 0xB0, "times" to 0xD7, "divide" to 0xF7,
        "plusmn" to 0xB1, "euro" to 0x20AC, "pound" to 0xA3, "yen" to 0xA5, "cent" to 0xA2,
        "sect" to 0xA7, "para" to 0xB6, "larr" to 0x2190, "uarr" to 0x2191, "rarr" to 0x2192,
        "darr" to 0x2193, "harr" to 0x2194, "check" to 0x2713, "cross" to 0x2717,
        "star" to 0x2606, "starf" to 0x2605, "hearts" to 0x2665, "iexcl" to 0xA1,
        "iquest" to 0xBF, "shy" to 0xAD, "micro" to 0xB5, "frac12" to 0xBD, "frac14" to 0xBC,
        "frac34" to 0xBE, "sup2" to 0xB2, "sup3" to 0xB3, "ensp" to 0x2002, "emsp" to 0x2003,
        "thinsp" to 0x2009, "zwj" to 0x200D, "zwnj" to 0x200C
    )
}
