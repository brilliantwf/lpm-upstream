package cx.lpm.mobile.core.demo

// The composer's "transform" engine: canned rewrites shaped by the instruction.

internal fun demoRewrites(text: String, instruction: String, count: Int): List<String> {
    val base = polishPrompt(text)
    val lc = instruction.lowercase()
    val variants = when {
        "short" in lc || "concise" in lc -> listOf(
            firstSentence(dropFiller(base)),
            dropFiller(base),
            truncateWords(base, 10),
            firstSentence(base),
            truncateWords(dropFiller(base), 8)
        )

        "grammar" in lc || "spell" in lc ->
            listOf(base, expandContractions(base), base, expandContractions(base), base)

        "detail" in lc || "longer" in lc || "expand" in lc -> listOf(
            "$base Include unit tests for the new behavior.",
            "$base Outline the approach first, then implement it step by step.",
            "$base List any edge cases you find along the way.",
            "$base Update related documentation to match.",
            "$base Note every file you touch in a short summary at the end."
        )

        else -> listOf(
            base,
            "$base Keep the changes minimal and focused.",
            "$base Explain your reasoning as you go.",
            "$base Break it into small, reviewable steps.",
            "$base Add tests where behavior changes."
        )
    }
    return (0 until count).map { variants[it % variants.size] }
}

private val leadIns = listOf(
    "can you ",
    "could you ",
    "please ",
    "i want you to ",
    "i'd like you to ",
    "hey "
)

private fun polishPrompt(text: String): String {
    var s = text.replace("\n", " ")
    while ("  " in s) s = s.replace("  ", " ")
    s = s.trimmingWhitespacesAndNewlines()
    var stripped = true
    while (stripped) {
        stripped = false
        for (prefix in leadIns) {
            if (s.lowercase().startsWith(prefix)) {
                s = s.drop(prefix.length)
                stripped = true
            }
        }
    }
    if (s.isEmpty()) return "Rewrite this."
    s = s.graphemePrefix(1).uppercase() + s.dropFirstGrapheme()
    s = s.replace(" i ", " I ")
    val last = s.lastGrapheme()
    if (last != null && last !in listOf(".", "!", "?")) s += "."
    return s
}

private val filler =
    setOf("just", "really", "very", "basically", "actually", "maybe", "perhaps", "simply")

private fun dropFiller(s: String): String =
    s.swiftSplit(' ').filter { it.lowercase() !in filler }.joinToString(" ")

private fun firstSentence(s: String): String {
    val r = s.indexOf(". ")
    return if (r >= 0) s.substring(0, r) + "." else s
}

private fun truncateWords(s: String, n: Int): String {
    val words = s.swiftSplit(' ')
    if (words.size <= n) return s
    return words.take(n).joinToString(" ") + "…"
}

private val contractions = listOf(
    "don't" to "do not",
    "Don't" to "Do not",
    "can't" to "cannot",
    "won't" to "will not",
    "it's" to "it is",
    "It's" to "It is",
    "doesn't" to "does not",
    "isn't" to "is not",
    "let's" to "let us",
    "I'm" to "I am",
    "we're" to "we are"
)

private fun expandContractions(s: String): String {
    var out = s
    for ((a, b) in contractions) out = out.replace(a, b)
    return out
}
