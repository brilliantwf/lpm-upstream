package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** The gray note under a section's card ("Tap a service for its logs."). */
@Composable
fun ListSectionFooter(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LpmTheme.colors.secondaryLabel
) {
    Text(
        text = text,
        style = LpmTheme.typography.footnote.copy(lineHeight = 16.sp),
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ListDefaults.RowPadding,
                end = ListDefaults.RowPadding,
                top = 8.8.dp,
                bottom = 12.5.dp
            )
    )
}
