package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A full-width 58 dp button with a headline label (or [textStyle]): 44 dp of label
 * plus iOS's 7 dp padding.
 */
@Composable
internal fun FilledButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    shape: Shape,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LpmTheme.typography.headline
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = LpmTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .alpha(if (pressed) 0.75f else 1f)
            .background(if (enabled) container else colors.tertiarySystemFill, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (enabled) content else colors.tertiaryLabel,
            textAlign = TextAlign.Center
        )
    }
}
