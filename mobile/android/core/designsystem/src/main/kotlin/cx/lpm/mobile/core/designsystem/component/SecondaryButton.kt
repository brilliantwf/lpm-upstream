package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** The gray-filled sibling of [PrimaryButton] (`.bordered`) with a tint-colored label. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    contentColor: Color = LpmTheme.colors.tint,
    textStyle: TextStyle = LpmTheme.typography.headline
) {
    FilledButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        container = LpmTheme.colors.secondarySystemFill,
        content = contentColor,
        textStyle = textStyle,
        modifier = modifier
    )
}
