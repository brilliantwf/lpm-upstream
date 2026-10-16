package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A status dot (`RunningDot`): green running, gray stopped, red error. [hollow]
 * draws the ring that marks a last-known state rather than a live one.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    hollow: Boolean = false
) {
    Canvas(modifier.size(size)) {
        if (hollow) {
            val stroke = 1.5.dp.toPx()
            drawCircle(
                color,
                radius = (this.size.minDimension - stroke) / 2,
                style = Stroke(stroke)
            )
        } else {
            drawCircle(color)
        }
    }
}
