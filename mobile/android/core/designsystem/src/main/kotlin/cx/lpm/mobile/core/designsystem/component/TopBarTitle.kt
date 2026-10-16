package cx.lpm.mobile.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** iOS stacks a navigation subtitle tighter than the two lines' own heights. */
private val SubtitleOverlap = 5.dp

/** Roboto's capitals sit higher in the line than SF's; this re-centers them in the bar. */
private val CapsOffset = 1.2.dp

/** A navigation title with an optional gray subtitle under it ("storefront" / "Running"). */
@Composable
fun TopBarTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    TitleStack(title = title, modifier = modifier) {
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = LpmTheme.typography.footnote,
                color = LpmTheme.colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A navigation title over a subtitle of its own, such as colored change counts. */
@Composable
fun TopBarTitle(
    title: String,
    subtitleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    TitleStack(title = title, modifier = modifier, subtitle = subtitleContent)
}

@Composable
private fun TitleStack(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = title,
                style = LpmTheme.typography.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle()
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lines = measurables.map { it.measure(loose) }
        val overlap = if (lines.size > 1) SubtitleOverlap.roundToPx() else 0
        val width = lines.maxOf { it.width }
        val height = lines.sumOf { it.height } - overlap
        layout(width, height) {
            var y = CapsOffset.roundToPx()
            lines.forEach {
                it.placeRelative((width - it.width) / 2, y)
                y += it.height - overlap
            }
        }
    }
}
