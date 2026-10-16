package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A small capsule button (`.controlSize(.small)`, `.buttonBorderShape(.capsule)`):
 * [prominent] fills it with [color] under a white label (`.borderedProminent`),
 * otherwise a gray fill under a [color] label (`.bordered`) — "Try again", "Set up",
 * "Allow", "Settings". [leading] goes before the label, like a spinner.
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    enabled: Boolean = true,
    color: Color = LpmTheme.colors.tint,
    textStyle: TextStyle = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
    leading: (@Composable () -> Unit)? = null
) {
    val colors = LpmTheme.colors
    val target = remember { TouchTarget() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = when {
        !enabled -> colors.tertiarySystemFill
        prominent -> color
        else -> colors.secondarySystemFill
    }
    val content = when {
        !enabled -> colors.tertiaryLabel
        prominent -> Color.White
        else -> color
    }
    Row(
        modifier = modifier
            .clickableTarget(target, interaction, enabled, Role.Button, onClick)
            .alpha(if (pressed) 0.7f else 1f)
            .background(container, CircleShape)
            .heightIn(min = 28.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(text = text, style = textStyle, color = content, maxLines = 1)
    }
}
