package cx.lpm.mobile.feature.git.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/**
 * A full-bleed band at a hunk boundary, a list row's height: "@@ <line>", the
 * header's context, and "⇥ N" when N columns of common indentation were trimmed.
 */
@Composable
internal fun HunkSeparator(block: DiffBlock, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ListDefaults.RowMinHeight)
            .background(colors.secondarySystemFill)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = block.hunkLabel,
            style = mono.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryLabel
        )
        // The context fills the band, ending at least 8 dp short of the indent hint.
        Text(
            text = block.hunkContext,
            style = mono,
            color = colors.tertiaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        if (block.indentHint > 0) {
            Text(
                text = stringResource(R.string.git_hunk_indent, block.indentHint),
                style = mono.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = colors.tertiaryLabel
            )
        }
    }
}
