package cx.lpm.mobile.feature.usage.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * One line of text that shrinks to fit, down to [minScale] of its size, as SwiftUI's
 * `.lineLimit(1).minimumScaleFactor(…)`.
 */
@Composable
internal fun ScalingText(
    text: String,
    style: TextStyle,
    color: Color,
    minScale: Float,
    modifier: Modifier = Modifier
) {
    val size = if (style.fontSize.isSpecified) style.fontSize else 17.sp
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = size * minScale,
            maxFontSize = size,
            stepSize = 0.5.sp
        ),
        modifier = modifier
    )
}
