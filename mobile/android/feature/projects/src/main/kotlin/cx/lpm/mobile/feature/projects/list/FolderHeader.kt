package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.projects.ui.SymbolGlyph

/** A sidebar folder: a chevron that opens or shuts it, its name and how many it holds. */
@Composable
fun FolderHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = LpmTheme.colors
    ListRow(
        modifier = modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = onLongPress,
                onClick = onToggle
            )
            .background(if (pressed) colors.gray4 else Color.Transparent),
        leading = {
            SymbolGlyph(
                painter = painterResource(
                    if (expanded) R.drawable.ic_keyboard_arrow_down else R.drawable.ic_chevron_right
                ),
                size = 18.dp,
                bounds = DpSize(12.dp, 12.dp),
                tint = colors.secondaryLabel
            )
        },
        leadingSlot = FolderSlot,
        trailing = {
            Text(
                text = count.toString(),
                style = LpmTheme.typography.caption1,
                color = colors.secondaryLabel
            )
        }
    ) {
        Text(
            text = name,
            style = LpmTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val FolderSlot = ListLeadingSlot(width = 12.dp, spacing = 8.dp)
