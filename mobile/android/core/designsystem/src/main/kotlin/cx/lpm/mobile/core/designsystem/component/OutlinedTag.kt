package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A hairline-outlined label beside a name, like a Claude account's "pool". */
@Composable
fun OutlinedTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LpmTheme.typography.caption1,
        color = LpmTheme.colors.secondaryLabel,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, LpmTheme.colors.label.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}
