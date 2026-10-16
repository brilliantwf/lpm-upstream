package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A titled group's small gray title in a [ContextMenu] (`Section("…")` in a `Menu`). */
@Composable
fun ContextMenuSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = LpmTheme.colors.secondaryLabel,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(start = 28.dp, end = 27.dp, top = 1.dp, bottom = 10.dp)
    )
}
