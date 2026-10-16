package cx.lpm.mobile.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A gray rounded bar standing in for text that's still loading; null [width] fills the row. */
@Composable
fun SkeletonBar(width: Dp?, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .background(LpmTheme.colors.tertiarySystemFill, RoundedCornerShape(height / 2))
    )
}

/** The subtle bright band that sweeps across skeleton content, left to right. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase = transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            tween(SHIMMER_MS, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "phase"
    )
    Box(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val p = phase.value
                drawRect(
                    brush = Brush.horizontalGradient(
                        (p - 0.3f) to Color.Black.copy(alpha = 0.45f),
                        p to Color.Black,
                        (p + 0.3f) to Color.Black.copy(alpha = 0.45f),
                        startX = 0f,
                        endX = size.width
                    ),
                    topLeft = Offset.Zero,
                    blendMode = BlendMode.DstIn
                )
            }
    ) { content() }
}

private const val SHIMMER_MS = 1_200
