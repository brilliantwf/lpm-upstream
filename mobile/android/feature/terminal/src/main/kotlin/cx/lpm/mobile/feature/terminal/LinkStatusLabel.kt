package cx.lpm.mobile.feature.terminal

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.LinkStatusLine
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** How the phone is connected, or why it isn't: a tinted dot (pulsing while it tries) and the line. */
@Composable
internal fun LinkStatusLabel(line: LinkStatusLine, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val tint = when (line.tone) {
        LinkStatusLine.Tone.Live -> colors.green
        LinkStatusLine.Tone.Waiting -> colors.orange
        LinkStatusLine.Tone.Problem -> colors.red
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "dot"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(if (line.pulse) pulse else 1f)
                .background(tint, CircleShape)
        )
        Text(
            text = line.text,
            style = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
            color = colors.secondaryLabel,
            maxLines = 1
        )
    }
}

private const val PULSE_MS = 800
