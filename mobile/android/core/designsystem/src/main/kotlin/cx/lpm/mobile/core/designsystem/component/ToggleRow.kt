package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A title with a switch at the far edge (`Toggle`), like a service row: [detail]
 * follows the title in gray (":3000"). [onClick] makes the title side tappable on
 * its own, stopping short of the switch, as the service rows open their logs.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    leadingSlot: ListLeadingSlot = ListDefaults.IconSlot
) {
    ListRow(
        modifier = modifier,
        leading = leading,
        leadingSlot = leadingSlot,
        trailing = {
            LpmSwitch(
                modifier = Modifier.offset(x = 2.dp),
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                contentDescription = title
            )
        }
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = LpmTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}
