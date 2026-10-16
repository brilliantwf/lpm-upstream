package cx.lpm.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.component.ListCard
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSectionHeader
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A `Form` section whose footer holds more than one line of text — a note and a
 * button under it. [dimmed] is a section turned off by a switch above it (iOS
 * `.disabled` + `.opacity(0.4)`); its rows take [enabled] themselves.
 */
@Composable
fun FormSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    dimmed: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .padding(horizontal = ListDefaults.Margin)
            .alpha(if (dimmed) DIMMED_ALPHA else 1f)
    ) {
        if (header != null) {
            ListSectionHeader(text = header, style = ListHeaderStyle.Title)
        } else {
            Spacer(Modifier.height(7.6.dp))
        }
        ListCard(content = content)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ListDefaults.RowPadding,
                    end = ListDefaults.RowPadding,
                    top = 8.8.dp,
                    bottom = 12.5.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = footer
        )
    }
}

/** A line of a [FormSection]'s footer. */
@Composable
fun FooterText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LpmTheme.typography.footnote.copy(lineHeight = 16.sp),
        color = LpmTheme.colors.secondaryLabel,
        modifier = modifier
    )
}

private const val DIMMED_ALPHA = 0.4f
