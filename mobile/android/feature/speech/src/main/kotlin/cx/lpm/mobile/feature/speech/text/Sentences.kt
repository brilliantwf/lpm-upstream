package cx.lpm.mobile.feature.speech.text

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import java.util.Locale

/**
 * Sentences as iOS's `enumerateSubstrings(options: [.bySentences, .localized])` finds
 * them: ICU's breaker for the phone's language, which doesn't end a sentence at the
 * abbreviations CLDR lists ("Mr.", "Mrs."), plus the few more Apple's tokenizer keeps
 * together (measured with `ios-parity/generate.sh`'s cases).
 */
internal object Sentences {
    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val locale = ULocale.forLocale(Locale.getDefault()).setKeywordValue("ss", "standard")
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val out = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val piece = text.substring(start, end)
            if (end == text.length || !endsWithAbbreviation(piece)) {
                piece.trim().takeIf { it.isNotEmpty() }?.let { out += it }
                start = end
            }
            end = iterator.next()
        }
        return out.ifEmpty { listOf(text) }
    }

    private fun endsWithAbbreviation(piece: String): Boolean {
        val trimmed = piece.trimEnd()
        return APPLE_ABBREVIATIONS.any { abbreviation ->
            trimmed.endsWith(abbreviation) &&
                trimmed.getOrNull(trimmed.length - abbreviation.length - 1)?.isLetterOrDigit() !=
                true
        }
    }

    private val APPLE_ABBREVIATIONS = listOf(
        "Dr.", "Lt.", "Ph.D.", "M.D.", "B.A.", "U.S.", "U.K.", "E.U.", "e.g.", "i.e.", "A.M.",
        "P.M.", "a.m."
    )
}
