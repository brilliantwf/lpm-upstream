package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.Shimmer
import cx.lpm.mobile.core.designsystem.component.SkeletonBar
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private val RowWidths = listOf(150, 104, 168, 120, 88, 136)

/** Ghost of the projects list, shown until the first projects push arrives. */
@Composable
fun ProjectListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(top = ListDefaults.ContentTopPadding)
            .clearAndSetSemantics {}
    ) {
        ListSection {
            RowWidths.forEachIndexed { i, width ->
                ListRow(
                    leading = { StatusDot(LpmTheme.colors.tertiarySystemFill) },
                    leadingSlot = ListDefaults.DotSlot,
                    trailing = if (i % 3 == 0) ({ SkeletonBar(44.dp, height = 18.dp) }) else null
                ) {
                    Shimmer { SkeletonBar(width.dp) }
                }
            }
        }
    }
}
