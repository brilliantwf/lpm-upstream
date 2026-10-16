package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The iOS − / + stepper: a 93 × 32 gray capsule split by a hairline. A side whose
 * step would leave [range] dims and stops responding. [label] names the buttons
 * ("Decrement Font size"), as VoiceOver does.
 */
@Composable
fun LpmStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Row(
        modifier = modifier
            .size(93.dp, 32.dp)
            .background(LpmTheme.colors.tertiarySystemFill, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperHalf(
            painter = painterResource(R.drawable.ic_remove),
            glyph = DpSize(12.8.dp, 2.dp),
            contentDescription = listOfNotNull("Decrement", label).joinToString(" "),
            enabled = value > range.first,
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .width(1.dp)
                .height(24.dp)
                .background(LpmTheme.colors.separator)
        )
        StepperHalf(
            painter = painterResource(R.drawable.ic_add),
            glyph = DpSize(12.8.dp, 12.8.dp),
            contentDescription = listOfNotNull("Increment", label).joinToString(" "),
            enabled = value < range.last,
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StepperHalf(
    painter: Painter,
    glyph: DpSize,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = remember { TouchTarget() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = LpmTheme.colors
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickableTarget(target, interaction, enabled, Role.Button, onClick)
            .background(if (pressed) colors.tertiarySystemFill else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Glyph(
            painter = painter,
            contentDescription = contentDescription,
            size = 22.dp,
            bounds = glyph,
            tint = if (enabled) colors.label else colors.tertiaryLabel
        )
    }
}
