package cx.lpm.mobile.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private val TrackWidth = 63.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 37.dp
private val ThumbHeight = 24.dp
private val ThumbInset = 2.dp

/**
 * The iOS 26 switch: a 63 × 28 green / gray track with a pill thumb. Name it with
 * [contentDescription] when no visible title labels it.
 */
@Composable
fun LpmSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val colors = LpmTheme.colors
    val target = remember { TouchTarget() }
    val interaction = remember { MutableInteractionSource() }
    val track by animateColorAsState(
        if (checked) colors.green else colors.switchTrackOff,
        label = "track"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbWidth - ThumbInset * 2 else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "thumb"
    )
    val interactive = if (onCheckedChange != null) {
        Modifier.toggleableTarget(target, checked, interaction, enabled, onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(interactive)
            .then(
                if (contentDescription !=
                    null
                ) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else 0.5f)
            .size(TrackWidth, TrackHeight)
            .background(track, CircleShape)
            .padding(ThumbInset),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset { IntOffset(thumbX.roundToPx(), 0) }
                .size(ThumbWidth, ThumbHeight)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 4.dp,
                        color = Color.Black.copy(alpha = 0.12f),
                        offset = DpOffset(0.dp, 2.dp)
                    )
                )
                .background(Color.White, CircleShape)
        )
    }
}
