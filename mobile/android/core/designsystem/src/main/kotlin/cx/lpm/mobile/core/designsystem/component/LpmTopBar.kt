package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

private val BarHeight = 44.dp
private val ItemSpacing = 11.dp
private val TitleGap = 12.dp

/**
 * The iOS 26 navigation bar: glass buttons at either end ([leading], [trailing]) and
 * the title centered on the screen, moved over only when the buttons leave it no
 * room. [containerColor] fills behind the status bar too (the purple demo header).
 */
@Composable
fun LpmTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    windowInsets: WindowInsets = WindowInsets.statusBars
) {
    LpmTopBar(
        modifier = modifier,
        leading = leading,
        trailing = trailing,
        containerColor = containerColor,
        windowInsets = windowInsets
    ) {
        TopBarTitle(title = title, subtitle = subtitle)
    }
}

/** [LpmTopBar] with a custom [title], such as a Mac switcher menu. */
@Composable
fun LpmTopBar(
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    title: @Composable () -> Unit
) {
    Layout(
        contents = listOf(
            { BarItems(leading) },
            title,
            { BarItems(trailing) }
        ),
        modifier = modifier
            .background(containerColor)
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .height(BarHeight)
            .padding(horizontal = 16.dp)
    ) { (leadingMeasurables, titleMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val start = leadingMeasurables.first().measure(loose)
        val end = trailingMeasurables.first().measure(loose)
        val gap = TitleGap.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val minX = if (start.width > 0) start.width + gap else 0
        val maxRight = if (end.width > 0) width - end.width - gap else width
        val titlePlaceable = titleMeasurables.first().measure(
            loose.copy(maxWidth = (maxRight - minX).coerceAtLeast(0))
        )
        val centered = (width - titlePlaceable.width) / 2
        val titleX = centered.coerceIn(minX, (maxRight - titlePlaceable.width).coerceAtLeast(minX))
        layout(width, height) {
            start.placeRelative(0, (height - start.height) / 2)
            titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
            end.placeRelative(width - end.width, (height - end.height) / 2)
        }
    }
}

@Composable
private fun BarItems(content: (@Composable RowScope.() -> Unit)?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content?.invoke(this)
    }
}
