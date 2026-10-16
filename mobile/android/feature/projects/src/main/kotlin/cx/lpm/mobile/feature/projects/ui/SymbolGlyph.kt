package cx.lpm.mobile.feature.projects.ui

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
 * A Material Symbol laid out like an SF Symbol: its layout box is the drawn glyph
 * ([bounds]) rather than the symbol's padded canvas ([size]), so spacing measured on
 * iOS carries over.
 */
@Composable
fun SymbolGlyph(
    painter: Painter,
    size: Dp,
    bounds: DpSize,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
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
