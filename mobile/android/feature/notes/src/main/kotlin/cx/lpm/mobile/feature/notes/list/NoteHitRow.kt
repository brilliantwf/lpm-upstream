package cx.lpm.mobile.feature.notes.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.PlainListDefaults
import cx.lpm.mobile.core.designsystem.component.plainListSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R

/**
 * One search result: the matching snippet over the chat it's in and when. The [first]
 * row carries the list's top separator.
 */
@Composable
internal fun NoteHitRow(
    row: NoteHitRowUi,
    first: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .plainListSeparator(colors.listSeparator, top = first)
            .fillMaxWidth()
            .clickable(interaction, indication = null, role = Role.Button, onClick = onOpen)
            .background(if (pressed) colors.gray4 else Color.Transparent)
            .padding(
                horizontal = PlainListDefaults.RowPadding,
                vertical = PlainListDefaults.RowInset + 4.dp
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = row.hit.snippet,
            style = LpmTheme.typography.subheadline,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = row.hit.chatTitle.ifEmpty { stringResource(R.string.notes_untitled) },
                style = LpmTheme.typography.caption1.copy(fontWeight = FontWeight.Medium),
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = row.time,
                style = LpmTheme.typography.caption1,
                color = colors.tertiaryLabel
            )
        }
    }
}
