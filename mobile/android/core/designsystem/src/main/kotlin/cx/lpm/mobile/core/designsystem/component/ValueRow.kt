package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A read-only title and value (`LabeledContent`). */
@Composable
fun ValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    leadingSlot: ListLeadingSlot = ListDefaults.IconSlot
) {
    ListRow(
        modifier = modifier,
        leading = leading,
        leadingSlot = leadingSlot,
        trailing = {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                maxLines = 1
            )
        }
    ) {
        Text(
            text = title,
            style = LpmTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
