package cx.lpm.mobile.feature.usage.usage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.usage.rows.UsageLane
import cx.lpm.mobile.feature.usage.ui.usagePalette

/**
 * A window meter's 6 dp bar: the use so far, red from the pace tick on — the use that
 * came faster than even pace — and the tick itself where even use would be by now.
 */
@Composable
internal fun MeterBar(
    fraction: Double,
    tick: Double?,
    stale: Boolean,
    modifier: Modifier = Modifier
) {
    val track = LpmTheme.colors.tertiarySystemFill
    val tickColor = LpmTheme.colors.secondaryLabel.copy(alpha = 0.7f)
    val palette = usagePalette
    val animated by animateFloatAsState(fraction.toFloat(), tween(700), label = "meter")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val radius = size.height / 2
        drawRoundRect(track, cornerRadius = CornerRadius(radius))
        val alpha = if (stale) 0.4f else 1f
        drawFill(
            width = animated * size.width,
            aheadFrom = tick?.let { it.toFloat() * size.width },
            height = size.height,
            corner = 3.dp.toPx(),
            fill = palette.fill,
            ahead = palette.ahead,
            alpha = alpha
        )
        if (tick != null) {
            val w = 1.5.dp.toPx()
            val x = (tick.toFloat() * size.width - w / 2).coerceIn(0f, size.width - w)
            drawRect(tickColor, Offset(x, 0f), Size(w, size.height))
        }
    }
}

/**
 * Both limit windows on one track, as the desktop sidebar draws them: the row's own
 * window in the bolder upper lane, the other in a hairline lane under it. Only the
 * upper lane draws a pace tick, pointing up out of the track.
 */
@Composable
internal fun UsageLanes(
    upper: UsageLane,
    upperFill: Color,
    pace: Double?,
    lower: UsageLane,
    dim: Boolean,
    modifier: Modifier = Modifier
) {
    val track = LpmTheme.colors.tertiarySystemFill
    val tickColor = LpmTheme.colors.label
    val palette = usagePalette
    val upperAnimated by animateFloatAsState(upper.fraction.toFloat(), tween(500), label = "upper")
    val lowerAnimated by animateFloatAsState(lower.fraction.toFloat(), tween(500), label = "lower")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(UpperHeight + LaneGap + LowerHeight)
    ) {
        val upperH = UpperHeight.toPx()
        val lowerTop = upperH + LaneGap.toPx()
        val alpha = if (dim) 0.4f else 1f
        val capsule = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2)))
        }
        clipPath(capsule) {
            drawRect(track, Offset.Zero, Size(size.width, upperH))
            drawFill(
                width = laneWidth(upperAnimated, size.width),
                aheadFrom = pace?.let { it.toFloat() * size.width },
                height = upperH,
                corner = upperH / 2,
                fill = upperFill,
                ahead = palette.ahead,
                alpha = alpha
            )
            translate(top = lowerTop) {
                val lowerH = LowerHeight.toPx()
                drawRect(track, Offset.Zero, Size(size.width, lowerH))
                drawFill(
                    width = laneWidth(lowerAnimated, size.width),
                    aheadFrom = null,
                    height = lowerH,
                    corner = lowerH / 2,
                    fill = palette.fill,
                    ahead = palette.ahead,
                    alpha = alpha
                )
            }
        }
        if (pace !=
            null
        ) {
            drawPaceTick(
                pace,
                tickColor,
                top = -Reach.toPx(),
                height = upperH + Reach.toPx(),
                alpha
            )
        }
    }
}

/**
 * A usage row's thin bar with its pace tick — where even use would put the bar by now —
 * standing 3 dp proud of the bar, for a row that has no second window to show.
 */
@Composable
internal fun UsagePaceBar(
    fraction: Double,
    fill: Color,
    pace: Double?,
    dim: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val track = LpmTheme.colors.tertiarySystemFill
    val tickColor = LpmTheme.colors.label
    val animated by animateFloatAsState(fraction.toFloat(), tween(500), label = "pace")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = CornerRadius(size.height / 2)
        val alpha = if (dim) 0.4f else 1f
        drawRoundRect(track, cornerRadius = radius)
        val width = laneWidth(animated, size.width)
        if (width >
            0f
        ) {
            drawRoundRect(
                fill,
                size = Size(width, size.height),
                cornerRadius = radius,
                alpha = alpha
            )
        }
        if (pace !=
            null
        ) {
            drawPaceTick(
                pace,
                tickColor,
                top = -3.dp.toPx(),
                height =
                    size.height + 6.dp.toPx(),
                alpha
            )
        }
    }
}

/** Any use at all shows as a sliver, so 1% never reads as nothing. */
private fun laneWidth(fraction: Float, width: Float): Float =
    if (fraction <= 0f) 0f else width * fraction.coerceIn(MIN_SLIVER, 1f)

private fun DrawScope.drawFill(
    width: Float,
    aheadFrom: Float?,
    height: Float,
    corner: Float,
    fill: Color,
    ahead: Color,
    alpha: Float
) {
    if (width <= 0f) return
    val shape = Path().apply {
        addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(corner)))
    }
    clipPath(shape) {
        drawRect(fill, Offset.Zero, Size(width, height), alpha = alpha)
        if (aheadFrom != null && aheadFrom < width) {
            drawRect(ahead, Offset(aheadFrom, 0f), Size(width - aheadFrom, height), alpha = alpha)
        }
    }
}

private fun DrawScope.drawPaceTick(
    pace: Double,
    color: Color,
    top: Float,
    height: Float,
    alpha: Float
) {
    val w = 2.dp.toPx()
    val x = (pace.toFloat() * size.width - w / 2).coerceIn(0f, size.width - w)
    drawRoundRect(color, Offset(x, top), Size(w, height), CornerRadius(w / 2), alpha = alpha)
}

private val UpperHeight = 3.5.dp
private val LaneGap = 1.5.dp
private val LowerHeight = 1.5.dp
private val Reach = 3.dp
private const val MIN_SLIVER = 0.02f
