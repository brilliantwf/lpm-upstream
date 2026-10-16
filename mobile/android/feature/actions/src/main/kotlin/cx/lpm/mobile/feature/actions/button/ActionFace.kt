package cx.lpm.mobile.feature.actions.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.glass
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** How a face draws its label and chevron: the action's color, smaller in the footer. */
@Immutable
internal class FaceStyle(val color: Color, val text: TextStyle)

/**
 * An action button's face as iOS 26 draws it: an interactive glass capsule washed in
 * the action's color, its label in that color. Smaller type in the footer.
 */
@Composable
internal fun ActionFace(
    tint: ActionTint?,
    compact: Boolean,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    content: @Composable RowScope.(FaceStyle) -> Unit
) {
    val colors = LpmTheme.colors
    val base = LpmTheme.typography.subheadline
    val style = FaceStyle(
        color = tint?.text(colors.isDark) ?: colors.label,
        text = base.copy(
            fontSize = if (compact) COMPACT_SIZE.sp else base.fontSize,
            fontWeight = FontWeight.Medium
        )
    )
    Row(
        modifier = modifier
            .height(FaceHeight)
            .glass(
                CircleShape,
                colors,
                pressed,
                tint = tint?.glass(colors.isDark) ?: Color.Unspecified
            )
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content(style)
    }
}

/** A label inside an [ActionFace], one line in the face's color. */
@Composable
internal fun FaceLabel(text: String, style: FaceStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = style.color,
        style = style.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** `chevron.down` at 11 pt bold, in the face's color. */
@Composable
internal fun Chevron(style: FaceStyle, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_keyboard_arrow_down),
        contentDescription = null,
        tint = style.color,
        modifier = modifier.size(18.dp)
    )
}

private val FaceHeight = 44.dp
private const val COMPACT_SIZE = 14
