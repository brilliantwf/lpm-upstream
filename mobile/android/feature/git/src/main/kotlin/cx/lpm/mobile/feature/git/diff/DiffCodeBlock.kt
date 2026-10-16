package cx.lpm.mobile.feature.git.diff

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The diff's monospaced code: iOS's `monospacedSystemFont(ofSize: 13)`, whose lines
 * are 15.4 pt apart. Compose rounds a line's height up to whole pixels.
 */
internal val DiffCodeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 15.3.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

/**
 * One block of code beside its gutter (iOS `DiffCodeBlock`): the line numbers stay
 * put on their deeper tint while the code scrolls sideways over its row bands, at
 * least as wide as the space left beside the gutter so a band reaches the edge.
 */
@Composable
internal fun DiffCodeBlock(block: DiffBlock, colors: DiffColors, modifier: Modifier = Modifier) {
    val code = remember(block, colors) { codeText(block, colors) }
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = block.gutter,
                style = DiffCodeStyle,
                color = colors.gutterText,
                softWrap = false,
                modifier = Modifier.drawBehind { drawRows(block.rows) { colors.gutter(it) } }
            )
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = code,
                    style = DiffCodeStyle,
                    color = colors.label,
                    softWrap = false,
                    modifier = Modifier
                        .drawBehind { drawRows(block.rows, colors::row) }
                        .padding(end = 12.dp)
                )
            }
        }
    ) { (gutter, scroller), constraints ->
        val g = gutter.measure(Constraints())
        val rest = (constraints.maxWidth - g.width).coerceAtLeast(0)
        val s = scroller.measure(Constraints(minWidth = rest, maxWidth = rest))
        val height = maxOf(g.height, s.height)
        layout(constraints.maxWidth, height) {
            g.placeRelative(0, 0)
            s.placeRelative(g.width, 0)
        }
    }
}

/** Each row an equal slice of the block: every line has the same monospaced height. */
private fun DrawScope.drawRows(rows: List<DiffLineKind>, tint: (DiffLineKind) -> Color?) {
    if (rows.isEmpty()) return
    val height = size.height / rows.size
    rows.forEachIndexed { index, kind ->
        val color = tint(kind) ?: return@forEachIndexed
        drawRect(color, Offset(0f, index * height), Size(size.width, height))
    }
}

private fun codeText(block: DiffBlock, colors: DiffColors): AnnotatedString = buildAnnotatedString {
    append(block.code)
    block.tokens.forEach { addStyle(SpanStyle(color = colors.token(it.token)), it.start, it.end) }
    block.emphasis.forEach {
        val tint = if (it.added) colors.addWord else colors.delWord
        addStyle(SpanStyle(background = tint), it.start, it.end)
    }
}
