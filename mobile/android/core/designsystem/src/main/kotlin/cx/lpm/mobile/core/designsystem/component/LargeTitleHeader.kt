package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A screen-opening bold large title with a callout [subtitle] ("Connect to your Mac"). */
@Composable
fun LargeTitleHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = LpmTheme.typography.largeTitle.copy(fontWeight = FontWeight.Bold)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = LpmTheme.typography.callout,
                color = LpmTheme.colors.secondaryLabel
            )
        }
    }
}
