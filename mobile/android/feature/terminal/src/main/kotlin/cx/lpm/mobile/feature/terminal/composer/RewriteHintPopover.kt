package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private val Gap = 8.dp
private val Edge = 8.dp

/** A small bubble over its anchor, gone on the next tap anywhere. */
@Composable
internal fun RewriteHintPopover(text: String, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val position = remember(density) {
        AbovePosition(with(density) { Gap.roundToPx() }, with(density) { Edge.roundToPx() })
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss) {
        Text(
            text = text,
            style = LpmTheme.typography.footnote,
            color = LpmTheme.colors.label,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(LpmTheme.colors.menuBackground, RoundedCornerShape(14.dp))
                .padding(12.dp)
        )
    }
}

private class AbovePosition(private val gap: Int, private val edge: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.left).coerceIn(
            edge,
            (windowSize.width - edge - popupContentSize.width).coerceAtLeast(edge)
        )
        return IntOffset(x, anchorBounds.top - gap - popupContentSize.height)
    }
}
