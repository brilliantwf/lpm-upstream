package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The full-width filled button (`.borderedProminent`, 12 dp corners) in the tint
 * color with a white headline label, like "Set up Built-in Tailscale".
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    contentColor: Color = Color.White,
    textStyle: TextStyle = LpmTheme.typography.headline
) {
    FilledButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        container = LpmTheme.colors.tint,
        content = contentColor,
        textStyle = textStyle,
        modifier = modifier
    )
}
