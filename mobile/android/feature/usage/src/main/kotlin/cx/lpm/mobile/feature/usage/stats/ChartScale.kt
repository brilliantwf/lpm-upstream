package cx.lpm.mobile.feature.usage.stats

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** The token axis of the activity chart: its top and the values it marks. */
data class TokenScale(val top: Long, val ticks: List<Long>)

/**
 * Round ticks from zero to past [max], about [count] of them, at 1, 2 or 5 times a power
 * of ten, the axis topped at the first tick at or above [max] — the automatic axis Swift
 * Charts draws (0, 5.0M, 10M, 15M, 20M for a 16.5M peak).
 */
fun tokenScale(max: Long, count: Int = 5): TokenScale {
    if (max <= 0) return TokenScale(top = 1, ticks = listOf(0, 1))
    val raw = max.toDouble() / count
    val power = 10.0.pow(floor(log10(raw)))
    val error = raw / power
    val factor = when {
        error >= sqrt(50.0) -> 10.0
        error >= sqrt(10.0) -> 5.0
        error >= sqrt(2.0) -> 2.0
        else -> 1.0
    }
    val step = maxOf(1L, (factor * power).toLong())
    val top = ceil(max.toDouble() / step).toLong() * step
    return TokenScale(top = top, ticks = (0..top / step).map { it * step })
}

/** Every how many days the chart marks its date axis, by how many days it shows. */
fun dayStride(days: Int): Int = when {
    days < 8 -> 1
    days < 15 -> 2
    else -> 7
}
