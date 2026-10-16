package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A text button in a 44 dp glass capsule, as iOS 26 draws toolbar text buttons:
 * "Done" (pass `LpmTheme.typography.headline`), "Mark all read" (subheadline). A
 * destructive one ("Stop") passes `LpmTheme.colors.red` as [color].
 */
@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LpmTheme.typography.body,
    color: Color = LpmTheme.colors.label
) {
    val colors = LpmTheme.colors
    val target = remember { TouchTarget() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickableTarget(target, interaction, enabled, Role.Button, onClick)
            .height(44.dp)
            .glass(CircleShape, colors, pressed)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (enabled) color else colors.tertiaryLabel,
            maxLines = 1
        )
    }
}
