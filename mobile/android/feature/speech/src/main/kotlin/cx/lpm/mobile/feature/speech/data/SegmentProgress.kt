package cx.lpm.mobile.feature.speech.data

import cx.lpm.mobile.feature.speech.text.SpeechSegment

/**
 * Where the voice is in the segments being read: the segment, how many of its
 * characters are spoken (for the progress bar, which counts characters because the
 * voice has no timeline) and the word to resume from after a pause. A run that
 * resumes starts its first utterance [offset] characters into the segment.
 */
internal class SegmentProgress {
    var segments: List<SpeechSegment> = emptyList()
        private set
    private var prefixChars = listOf(0)
    private var totalChars = 1

    var index = 0
        private set
    private var charsInCurrent = 0
    private var runStart = 0

    var offset = 0
        private set
    var wordStart = 0
        private set
    var elapsedAtSegmentStart = 0.0
        private set

    val isEmpty: Boolean get() = segments.isEmpty()
    val lastIndex: Int get() = segments.lastIndex

    /** The share of the text spoken so far. */
    val fraction: Double
        get() {
            val segment = segments.getOrNull(index) ?: return 0.0
            val spoken = prefixChars[index] + minOf(charsInCurrent, segment.text.length)
            return minOf(1.0, spoken.toDouble() / totalChars)
        }

    val block: Int? get() = segments.getOrNull(index)?.blockId

    fun reset(segments: List<SpeechSegment>) {
        this.segments = segments
        prefixChars = segments.runningFold(0) { acc, s -> acc + s.text.length }
        totalChars = maxOf(1, prefixChars.last())
        index = 0
        charsInCurrent = 0
        runStart = 0
        offset = 0
        wordStart = 0
        elapsedAtSegmentStart = 0.0
    }

    /** A run queued from segment [from], [offset] characters in (0 when that's past its end). */
    fun start(from: Int, offset: Int, elapsed: Double) {
        index = from
        runStart = from
        val length = segments.getOrNull(from)?.text?.length ?: 0
        this.offset = if (offset in 1 until length) offset else 0
        charsInCurrent = this.offset
        wordStart = this.offset
        if (this.offset == 0) elapsedAtSegmentStart = elapsed
    }

    /** Segment [i] began. */
    fun started(i: Int, elapsed: Double) {
        index = i
        val base = base(i)
        charsInCurrent = base
        wordStart = base
        if (base == 0) elapsedAtSegmentStart = elapsed
    }

    /** Segment [i]'s utterance is speaking its characters `[start, end)`. */
    fun spoke(i: Int, start: Int, end: Int) {
        if (i != index) return
        charsInCurrent = base(i) + end
        wordStart = base(i) + start
    }

    /**
     * Which block a fraction through the whole text lands in, by character offset.
     * Assumes a roughly uniform speaking rate — the only option with no timings.
     */
    fun blockAt(fraction: Double): Int? {
        if (segments.isEmpty()) return null
        val target = totalChars * fraction.coerceIn(0.0, 1.0)
        var seen = 0
        for (segment in segments) {
            seen += segment.text.length
            if (seen >= target) return segment.blockId
        }
        return segments.last().blockId
    }

    private fun base(i: Int): Int = if (i == runStart) offset else 0
}
