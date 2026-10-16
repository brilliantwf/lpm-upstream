package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The rounded card of an inset-grouped section. Stacks its rows and draws the iOS 26
 * separator under every row but the last, starting where that row's text starts
 * ([listSeparator]) and stopping [ListDefaults.RowPadding] short of the far edge.
 */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val color = LpmTheme.colors.listSeparator
    val lines = remember { SeparatorLines() }
    Layout(
        content = content,
        modifier = modifier
            .clip(ListDefaults.CardShape)
            .background(LpmTheme.colors.secondarySystemGroupedBackground)
            .drawWithContent {
                drawContent()
                val thickness = ListDefaults.SeparatorThickness.toPx()
                val end = ListDefaults.RowPadding.toPx()
                for (i in 0 until lines.count) {
                    val start = lines.starts[i]
                    val x = if (layoutDirection == LayoutDirection.Rtl) end else start
                    drawRect(
                        color = color,
                        topLeft = Offset(x, lines.bottoms[i] - thickness),
                        size = Size(size.width - start - end, thickness)
                    )
                }
            }
    ) { measurables, constraints ->
        val rowConstraints = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(rowConstraints) }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeables.maxOfOrNull { it.width } ?: 0
        }
        val height = placeables.sumOf {
            it.height
        }.coerceIn(constraints.minHeight, constraints.maxHeight)
        lines.reset(placeables.size)
        var y = 0
        placeables.forEachIndexed { index, placeable ->
            y += placeable.height
            val separator = measurables[index].parentData as? ListSeparatorData
            if (index < placeables.lastIndex && separator?.visible != false) {
                lines.add(
                    bottom = y.toFloat(),
                    start = (
                        separator?.startInset
                            ?: ListDefaults.RowPadding
                        ).toPx()
                )
            }
        }
        layout(width, height) {
            var top = 0
            placeables.forEach {
                it.placeRelative(0, top)
                top += it.height
            }
        }
    }
}

/**
 * Where the separator under this row starts, measured from the card's leading edge,
 * or no separator at all. Rows apply it themselves; a custom row passes the inset of
 * its first text.
 */
fun Modifier.listSeparator(
    startInset: Dp = ListDefaults.RowPadding,
    visible: Boolean = true
): Modifier = this then ListSeparatorElement(startInset, visible)

private class SeparatorLines {
    var count = 0
        private set
    var bottoms = FloatArray(0)
        private set
    var starts = FloatArray(0)
        private set

    fun reset(capacity: Int) {
        count = 0
        if (bottoms.size < capacity) {
            bottoms = FloatArray(capacity)
            starts = FloatArray(capacity)
        }
    }

    fun add(bottom: Float, start: Float) {
        bottoms[count] = bottom
        starts[count] = start
        count++
    }
}

private data class ListSeparatorData(val startInset: Dp, val visible: Boolean)

private data class ListSeparatorElement(val startInset: Dp, val visible: Boolean) :
    ModifierNodeElement<ListSeparatorNode>() {
    override fun create() = ListSeparatorNode(startInset, visible)

    override fun update(node: ListSeparatorNode) {
        node.startInset = startInset
        node.visible = visible
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "listSeparator"
        properties["startInset"] = startInset
        properties["visible"] = visible
    }
}

private class ListSeparatorNode(var startInset: Dp, var visible: Boolean) :
    Modifier.Node(),
    ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any =
        ListSeparatorData(startInset, visible)
}
