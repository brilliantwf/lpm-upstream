package cx.lpm.mobile.feature.connection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.LinkStatusLine
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A status dot and the status words, pulsing while connecting. */
@Composable
fun LinkStatusLabel(line: LinkStatusLine, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = LpmTheme.colors
    val tint = when (line.tone) {
        LinkStatusLine.Tone.Live -> colors.green
        LinkStatusLine.Tone.Waiting -> colors.orange
        LinkStatusLine.Tone.Problem -> colors.red
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            color = tint,
            size = 6.dp,
            modifier = Modifier.alpha(if (line.pulse) pulse else 1f)
        )
        Text(
            text = detail?.let { "${line.text} · $it" } ?: line.text,
            style = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
