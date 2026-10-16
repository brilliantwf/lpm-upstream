package cx.lpm.mobile.feature.terminal.composer

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private const val SWEEP_MS = 1_400
private const val FULL_TURN = 360f
private val StrokeWidth = 2.dp

/** A gradient border sweeping around the field while a rewrite runs. */
@Composable
internal fun ShimmerBorder(cornerRadius: Dp, modifier: Modifier = Modifier) {
    val tint = LpmTheme.colors.tint
    val angle by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)),
        label = "angle"
    )
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val width = StrokeWidth.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val shader = SweepGradient(
                center.x,
                center.y,
                intArrayOf(
                    tint.copy(alpha = 0.1f).toArgb(),
                    tint.toArgb(),
                    tint.copy(alpha = 0.1f).toArgb()
                ),
                null
            )
            val matrix = Matrix()
            val radius = cornerRadius.toPx() - width / 2
            onDrawBehind {
                matrix.setRotate(angle, center.x, center.y)
                shader.setLocalMatrix(matrix)
                drawRoundRect(
                    brush = ShaderBrush(shader),
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width)
                )
            }
        }
    )
}
