package cx.lpm.mobile.feature.notes.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A day's capsule over its first note: "Today", "Yesterday", "Oct 5, 2026". */
@Composable
internal fun DaySeparator(label: String, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryLabel,
            modifier = Modifier
                .background(colors.secondarySystemBackground, CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
