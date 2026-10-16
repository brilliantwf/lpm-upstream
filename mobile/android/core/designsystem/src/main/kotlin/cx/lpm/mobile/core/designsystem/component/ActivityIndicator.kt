package cx.lpm.mobile.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** SwiftUI `ProgressView`'s control sizes. */
enum class ActivityIndicatorSize(val diameter: Dp) {
    Mini(12.dp),
    Small(16.dp),
    Regular(20.dp),
    Large(34.dp)
}

private const val SPOKES = 8

/** iOS's spinner (an indeterminate `ProgressView`): eight spokes fading round in steps. */
@Composable
fun ActivityIndicator(
    modifier: Modifier = Modifier,
    size: ActivityIndicatorSize = ActivityIndicatorSize.Regular,
    color: Color = LpmTheme.colors.gray
) {
    val step by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = SPOKES.toFloat(),
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "step"
    )
    Canvas(
        modifier
            .size(size.diameter)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
    ) {
        val radius = this.size.minDimension / 2
        val stroke = radius * 0.2f
        val head = step.toInt()
        for (i in 0 until SPOKES) {
            val age = (head - i + SPOKES) % SPOKES
            rotate(i * 360f / SPOKES) {
                drawLine(
                    color = color.copy(alpha = color.alpha * (1f - age * 0.11f)),
                    start = Offset(center.x, center.y - radius * 0.5f),
                    end = Offset(center.x, center.y - radius + stroke / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
