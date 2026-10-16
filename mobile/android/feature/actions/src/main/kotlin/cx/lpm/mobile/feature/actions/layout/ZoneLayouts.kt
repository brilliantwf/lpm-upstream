package cx.lpm.mobile.feature.actions.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A zone's buttons in [rows] rows, filled top to bottom and then across, each as wide
 * as the widest in its column. A column that no longer fits the width starts a new
 * band of rows below, so a zone never runs off the screen.
 */
@Composable
internal fun ZoneGrid(
    rows: Int,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val perColumn = rows.coerceAtLeast(1)
        val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val rowHeight = measurables.maxOfOrNull { it.maxIntrinsicHeight(Constraints.Infinity) } ?: 0
        val width = constraints.maxWidth
        val xs = IntArray(measurables.size)
        val ys = IntArray(measurables.size)
        val columnWidths = IntArray(measurables.size)
        var x = 0
        var y = 0
        var bandRows = 0
        var maxX = 0
        for (start in measurables.indices step perColumn) {
            val column = start until minOf(start + perColumn, measurables.size)
            val columnWidth = minOf(column.maxOf { widths[it] }, width)
            if (x > 0 && x + columnWidth > width) {
                x = 0
                y += bandRows * (rowHeight + gap)
                bandRows = 0
            }
            column.forEachIndexed { row, index ->
                xs[index] = x
                ys[index] = y + row * (rowHeight + gap)
                columnWidths[index] = columnWidth
            }
            bandRows = maxOf(bandRows, column.count())
            maxX = maxOf(maxX, x + columnWidth)
            x += columnWidth + gap
        }
        val height = y + bandRows * (rowHeight + gap) - if (bandRows > 0) gap else 0
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(columnWidths[i], rowHeight))
        }
        layout(maxX, height.coerceAtLeast(0)) {
            placeables.forEachIndexed { i, p -> p.place(xs[i], ys[i]) }
        }
    }
}

/**
 * Every layer of a zone in one spot as big as the biggest, the open one in view and
 * the others lined up beside it, so opening another slides them sideways. [open] is
 * the (animated) index in view; an [empty] page takes whatever room the others leave.
 */
@Composable
internal fun ZonePager(
    open: () -> Float,
    empty: List<Boolean>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val filled = measurables.mapIndexedNotNull { i, m ->
            if (empty.getOrElse(i) { false }) null else i to m.measure(loose)
        }.toMap()
        val width = filled.values.maxOfOrNull { it.width } ?: 0
        val height = filled.values.maxOfOrNull { it.height } ?: 0
        val placeables = measurables.mapIndexed { i, m ->
            filled[i] ?: m.measure(Constraints.fixed(width, height))
        }
        val stride = width + PageGap.roundToPx()
        layout(width, height) {
            val at = open()
            placeables.forEachIndexed { i, p -> p.place(((i - at) * stride).roundToInt(), 0) }
        }
    }
}

private val PageGap = 16.dp
