package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * A Material Symbol laid out like an SF Symbol: the layout box is the drawn glyph
 * ([bounds]), not the symbol's padded 24 dp canvas, so spacing measured on iOS
 * carries over. [size] is the canvas the symbol is drawn at.
 */
@Composable
internal fun Glyph(
    painter: Painter,
    contentDescription: String?,
    size: Dp,
    bounds: DpSize,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(bounds), contentAlignment = Alignment.Center) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.requiredSize(size)
        )
    }
}
