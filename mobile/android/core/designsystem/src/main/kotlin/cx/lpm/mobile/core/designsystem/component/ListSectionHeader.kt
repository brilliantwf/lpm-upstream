package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

enum class ListHeaderStyle {
    /** Footnote in capitals, what lpm's own `SectionTitle` draws ("TABS", "USAGE"). */
    Caps,

    /** iOS 26's default section header: a gray headline ("Away from home", "New"). */
    Title
}

/**
 * A section's header, inset to line up with the rows' text, with an optional
 * trailing [action] on the same baseline ("Stop all", "Details", "2 of 3 running").
 */
@Composable
fun ListSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    style: ListHeaderStyle = ListHeaderStyle.Caps,
    action: (@Composable () -> Unit)? = null
) {
    val textStyle = when (style) {
        ListHeaderStyle.Caps -> LpmTheme.typography.footnote
        ListHeaderStyle.Title -> LpmTheme.typography.headline
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ListDefaults.RowPadding,
                end = ListDefaults.RowPadding,
                top = if (style == ListHeaderStyle.Caps) 0.dp else 2.dp,
                bottom = if (style == ListHeaderStyle.Caps) 9.2.dp else 8.8.dp
            )
    ) {
        Text(
            text = if (style == ListHeaderStyle.Caps) text.uppercase() else text,
            style = textStyle,
            color = LpmTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
        )
        if (action != null) {
            Box(Modifier.alignByBaseline()) { action() }
        }
    }
}
