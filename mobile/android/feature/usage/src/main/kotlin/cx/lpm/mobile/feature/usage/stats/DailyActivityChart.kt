package cx.lpm.mobile.feature.usage.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.UsageDaily
import cx.lpm.mobile.feature.usage.ui.providerMeta
import cx.lpm.mobile.feature.usage.ui.tint
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One day's bar: Claude's tokens stacked under Codex's. */
private data class DayBar(val index: Int, val claude: Long, val codex: Long)

/** The days on the chart's date axis, each bar placed by its date. */
private class DayAxis(val first: LocalDate?, val count: Int, val bars: List<DayBar>) {
    val peak: Long = bars.maxOfOrNull { it.claude + it.codex } ?: 0

    companion object {
        fun of(daily: List<UsageDaily>): DayAxis {
            val dated = daily.mapNotNull { day -> parseUsageDate(day.date)?.let { it to day } }
            val first = dated.minOfOrNull { it.first } ?: return DayAxis(null, 0, emptyList())
            val last = dated.maxOf { it.first }
            return DayAxis(
                first = first,
                count = ChronoUnit.DAYS.between(first, last).toInt() + 1,
                bars = dated.map { (date, day) ->
                    DayBar(
                        ChronoUnit.DAYS.between(first, date).toInt(),
                        day.claudeTokens,
                        day.codexTokens
                    )
                }
            )
        }
    }
}

/**
 * The daily token chart (Swift Charts' stacked `BarMark`s by provider): the legend
 * along the top, the token axis on the leading edge with solid grid lines, and the
 * date axis with dashed ones every [dayStride] days, labeled from each line.
 */
@Composable
internal fun DailyActivityChart(daily: List<UsageDaily>, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val claude = providerMeta("claude")
    val codex = providerMeta("codex")
    val claudeColor = claude.tint
    val codexColor = codex.tint
    val measurer = rememberTextMeasurer()
    val axis = remember(daily) { DayAxis.of(daily) }
    val scale = remember(axis) { tokenScale(axis.peak) }
    val labelStyle = LpmTheme.typography.caption2.copy(color = colors.secondaryLabel)
    val grid = colors.gray.copy(alpha = 0.5f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
    ) {
        drawLegend(
            measurer,
            labelStyle,
            listOf(claude.short to claudeColor, codex.short to codexColor)
        )
        val yLabels = scale.ticks.map { measurer.measure(formatTokenCount(it), labelStyle) }
        val plotLeft = (yLabels.maxOfOrNull { it.size.width } ?: 0) +
            (LabelInset + LabelGap).toPx()
        val plotTop = PlotTop.toPx()
        val baseline = Baseline.toPx()
        val plot = Size(size.width - plotLeft, baseline - plotTop)
        val y = { tokens: Long -> baseline - plot.height * tokens / scale.top }

        scale.ticks.forEachIndexed { i, tick ->
            val at = y(tick)
            drawLine(grid, Offset(plotLeft, at), Offset(size.width, at), GridWidth.toPx())
            val label = yLabels[i]
            drawText(
                label,
                topLeft = Offset(
                    plotLeft - LabelGap.toPx() - label.size.width,
                    at - label.size.height / 2f
                )
            )
        }

        val band = if (axis.count > 0) plot.width / axis.count else 0f
        val stride = dayStride(axis.count)
        val dash = PathEffect.dashPathEffect(floatArrayOf(Dash.toPx(), Dash.toPx()))
        for (i in 0..axis.count step stride) {
            val x = plotLeft + band * i
            drawLine(
                grid,
                Offset(x, plotTop),
                Offset(x, baseline),
                GridWidth.toPx(),
                pathEffect = dash
            )
            val day = axis.first?.plusDays(i.toLong()) ?: continue
            if (i < axis.count) {
                drawText(
                    measurer.measure(day.monthDayLabel(), labelStyle),
                    topLeft = Offset(x + DateLabelInset.toPx(), baseline + DateLabelGap.toPx())
                )
            }
        }

        val width = band * BAR_WIDTH_RATIO
        axis.bars.forEach { bar ->
            val left = plotLeft + band * bar.index + (band - width) / 2
            val claudeTop = y(bar.claude)
            val codexTop = y(bar.claude + bar.codex)
            val seam = if (bar.claude > 0 && bar.codex > 0) 1f else 0f
            drawSegment(
                claudeColor,
                left,
                width,
                claudeTop + seam,
                baseline,
                rounded =
                    bar.codex == 0L
            )
            if (bar.codex >
                0
            ) {
                drawSegment(codexColor, left, width, codexTop, claudeTop, rounded = true)
            }
        }
    }
}

private fun DrawScope.drawLegend(
    measurer: TextMeasurer,
    style: TextStyle,
    entries: List<Pair<String, Color>>
) {
    var x = 0f
    entries.forEach { (name, color) ->
        val text = measurer.measure(name, style)
        val radius = LegendDot.toPx() / 2
        val mid = text.size.height / 2f
        drawCircle(color, radius, Offset(x + radius, mid))
        x += radius * 2 + LegendDotGap.toPx()
        drawText(text, topLeft = Offset(x, 0f))
        x += text.size.width + LegendSpacing.toPx()
    }
}

private fun DrawScope.drawSegment(
    color: Color,
    left: Float,
    width: Float,
    top: Float,
    bottom: Float,
    rounded: Boolean
) {
    if (bottom - top <= 0f) return
    val radius = if (rounded) CornerRadius(BarCorner.toPx()) else CornerRadius.Zero
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = bottom,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius
            )
        )
    }
    drawPath(path, color)
}

private val ChartHeight = 200.dp
private val PlotTop = 19.3.dp
private val Baseline = 180.3.dp
private val LabelInset = 3.7.dp
private val LabelGap = 5.dp
private val DateLabelInset = 3.dp
private val DateLabelGap = 5.dp
private val GridWidth = 0.5.dp
private val Dash = 2.dp
private val BarCorner = 1.7.dp
private val LegendDot = 7.3.dp
private val LegendDotGap = 5.dp
private val LegendSpacing = 9.dp
private const val BAR_WIDTH_RATIO = 0.7f
