package cx.lpm.mobile.feature.files.preview

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * iOS's `ByteCountFormatter` with the `.file` count style: decimal units, whole
 * kilobytes, one decimal for megabytes and two above ("262 KB", "1.5 MB", "1.07 GB").
 */
internal object ByteCount {
    private val UNITS = listOf("KB", "MB", "GB", "TB", "PB")
    private val THOUSAND = BigDecimal(1000)

    fun format(bytes: Long): String {
        if (bytes == 0L) return "Zero KB"
        if (bytes == 1L) return "1 byte"
        if (bytes < 1000) return "$bytes bytes"
        var value = BigDecimal(bytes).divide(THOUSAND)
        var unit = 0
        while (true) {
            val rounded = value.setScale(minOf(unit, 2), RoundingMode.HALF_EVEN)
            if (rounded < THOUSAND || unit == UNITS.lastIndex) {
                return rounded.stripTrailingZeros().toPlainString() + " " + UNITS[unit]
            }
            value = value.divide(THOUSAND)
            unit++
        }
    }
}
