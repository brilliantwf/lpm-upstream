package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A borderless text button in the tint color ("Details", "Stop all", "I use the
 * Tailscale app"), optionally led by an [icon]. It dims while pressed, and its touch
 * area reaches 48 dp without moving anything around it.
 */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    color: Color = LpmTheme.colors.tint,
    textStyle: TextStyle = LpmTheme.typography.subheadline
) {
    val target = remember { TouchTarget() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (enabled) color else LpmTheme.colors.tertiaryLabel
    Row(
        modifier = modifier
            .clickableTarget(target, interaction, enabled, Role.Button, onClick)
            .alpha(if (pressed) 0.3f else 1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(text = text, style = textStyle, color = tint)
    }
}
