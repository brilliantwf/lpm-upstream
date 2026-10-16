package cx.lpm.mobile.feature.automations.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.LpmSwitch
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.automations.ui.color

/** How far a disclosure group's rows sit in from its own row, as in an iOS list. */
val GroupIndent = 20.dp

/**
 * A `DisclosureGroup`'s own row: what the group holds, the one fact you'd have opened
 * it for, and the chevron that turns down while it's open.
 */
@Composable
fun DisclosureRow(
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val angle by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    ListRow(
        modifier = modifier.semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        },
        onClick = onToggle,
        trailing = {
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .size(width = 13.dp, height = 13.dp)
                    .rotate(angle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = LpmTheme.colors.label,
                    modifier = Modifier.requiredSize(27.dp)
                )
            }
        }
    ) {
        Text(text = title, style = LpmTheme.typography.body, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = LpmTheme.typography.body,
            color = LpmTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A group's title and value (`LabeledContent`). */
@Composable
fun GroupValueRow(title: String, value: String, modifier: Modifier = Modifier) {
    ListRow(modifier = modifier, indent = GroupIndent) {
        Text(text = title, style = LpmTheme.typography.body, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = LpmTheme.typography.body,
            color = LpmTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A group's switch (`Toggle`). */
@Composable
fun GroupToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ListRow(
        modifier = modifier,
        indent = GroupIndent,
        trailing = {
            LpmSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.offset(x = 2.dp),
                enabled = enabled,
                contentDescription = title
            )
        }
    ) {
        Text(text = title, style = LpmTheme.typography.body, modifier = Modifier.weight(1f))
    }
}

/** A moment on the right of a row: the clock time, and how long ago underneath. */
@Composable
fun TimeRow(title: String, time: TimeRowUi, modifier: Modifier = Modifier) {
    ListRow(modifier = modifier, indent = GroupIndent, verticalPadding = 13.dp) {
        Text(text = title, style = LpmTheme.typography.body, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = time.value,
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                textAlign = TextAlign.End
            )
            Text(
                text = time.detail,
                style = LpmTheme.typography.caption1,
                color = time.tone.color(),
                textAlign = TextAlign.End
            )
        }
    }
}
