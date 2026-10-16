package cx.lpm.mobile.feature.files.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** iOS's linear `ProgressView`: a thin rounded track filling in the tint, 240 dp at most. */
@Composable
internal fun LinearProgress(fraction: Float, modifier: Modifier = Modifier) {
    val track = LpmTheme.colors.systemFill
    val fill = LpmTheme.colors.tint
    val value = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .widthIn(max = 240.dp)
            .fillMaxWidth()
            .height(4.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (value > 0f) {
            drawRoundRect(
                fill,
                size = Size(maxOf(size.width * value, size.height), size.height),
                cornerRadius = radius
            )
        }
    }
}
