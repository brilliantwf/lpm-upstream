package cx.lpm.mobile.core.designsystem.component

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * A soft shadow drawn only outside [shape], so a translucent surface on top (glass,
 * a frosted menu) doesn't show it through its fill the way `dropShadow` would.
 */
internal fun Modifier.outerShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp): Modifier =
    drawWithCache {
        val path = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.BLACK
            setShadowLayer(blur.toPx(), 0f, offsetY.toPx(), color.toArgb())
        }
        onDrawBehind {
            clipPath(path, ClipOp.Difference) {
                drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
            }
        }
    }
