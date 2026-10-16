package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmColors

/**
 * iOS 26 Liquid Glass as far as Compose can draw it without a backdrop blur: a light
 * wash over what sits behind, a rim lit at the top and bottom and shaded at the
 * sides, and a soft shadow. [pressed] swells it the way interactive glass does, and
 * [tint] washes it in a color (`.glassEffect(.regular.tint(…))`).
 */
fun Modifier.glass(
    shape: Shape,
    colors: LpmColors,
    pressed: Boolean = false,
    tint: Color = Color.Unspecified
): Modifier = this
    .graphicsLayer {
        val scale = if (pressed) 1.06f else 1f
        scaleX = scale
        scaleY = scale
    }
    .outerShadow(shape, colors.glassShadow, blur = 8.dp, offsetY = 4.dp)
    .background(colors.glassFill, shape)
    .then(if (tint.isSpecified) Modifier.background(tint, shape) else Modifier)
    .border(
        width = 0.75.dp,
        brush = Brush.verticalGradient(
            0f to colors.glassRimLight,
            0.5f to colors.glassRimShade,
            1f to colors.glassRimLight
        ),
        shape = shape
    )
