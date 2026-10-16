package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** An action row in the tint color, like "+ New tab"; [destructive] draws it red. */
@Composable
fun ButtonRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val color: Color = when {
        !enabled -> LpmTheme.colors.tertiaryLabel
        destructive -> LpmTheme.colors.red
        else -> LpmTheme.colors.tint
    }
    ListRow(modifier = modifier, onClick = onClick, enabled = enabled) {
        if (icon != null) {
            Glyph(
                painter = icon,
                contentDescription = null,
                size = 24.dp,
                bounds = DpSize(14.dp, 14.dp),
                tint = color
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, style = LpmTheme.typography.body, color = color)
    }
}
