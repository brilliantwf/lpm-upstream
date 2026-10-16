package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A slim colored strip across the top of a screen with an optional action at the
 * end, like "Demo — sample projects … Exit" (`DemoBanner`, system purple).
 */
@Composable
fun HeaderBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    containerColor: Color = LpmTheme.colors.purple,
    contentColor: Color = Color.White
) {
    val style = LpmTheme.typography.footnote
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Glyph(
                painter = icon,
                contentDescription = null,
                size = 18.dp,
                bounds = DpSize(15.dp, 15.dp),
                tint = contentColor
            )
        }
        Text(
            text = text,
            style = style.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            LinkButton(
                text = actionLabel,
                onClick = onAction,
                color = contentColor,
                textStyle = style.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}
