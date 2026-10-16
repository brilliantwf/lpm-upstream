package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A row that opens another screen (`NavigationLink`): [title] with an optional
 * [subtitle], a gray trailing [value] ("Off") and the disclosure chevron.
 */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    leadingSlot: ListLeadingSlot = ListDefaults.IconSlot,
    indent: Dp = 0.dp
) {
    ListRow(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        leading = leading,
        leadingSlot = leadingSlot,
        indent = indent,
        trailing = {
            if (value != null) {
                Text(
                    text = value,
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel,
                    maxLines = 1
                )
                Spacer(Modifier.width(13.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            DisclosureChevron()
        }
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LpmTheme.typography.body,
                color = if (enabled) LpmTheme.colors.label else LpmTheme.colors.tertiaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LpmTheme.typography.subheadline,
                    color = LpmTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
