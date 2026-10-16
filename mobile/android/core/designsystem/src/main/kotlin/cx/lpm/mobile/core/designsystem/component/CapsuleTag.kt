package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A small label in a gray capsule: a project scope ("storefront"), a project kind
 * ("Copy", "SSH"), or with a [dot] a service chip ("● web").
 */
@Composable
fun CapsuleTag(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = LpmTheme.colors.secondaryLabel,
    containerColor: Color = LpmTheme.colors.secondaryLabel.copy(alpha = 0.14f),
    textStyle: TextStyle = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
    dot: Color? = null
) {
    Row(
        modifier = modifier
            .background(containerColor, CircleShape)
            .padding(
                horizontal = if (dot !=
                    null
                ) {
                    9.dp
                } else {
                    6.dp
                },
                vertical = if (dot != null) 5.dp else 2.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) StatusDot(color = dot, size = 5.dp)
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
