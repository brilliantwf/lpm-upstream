package cx.lpm.mobile.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private val Inset = 2.dp

/** The iOS segmented control (`.pickerStyle(.segmented)`): equal segments, a sliding pill. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(colors.tertiarySystemFill, CircleShape)
            .padding(Inset)
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val thumbX by animateDpAsState(
            targetValue = segment * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "thumb"
        )
        if (selectedIndex in options.indices) {
            Box(
                Modifier
                    .offset { IntOffset(thumbX.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .dropShadow(
                        shape = CircleShape,
                        shadow = Shadow(
                            radius = 8.dp,
                            color = Color.Black.copy(alpha = 0.10f),
                            offset = DpOffset(0.dp, 3.dp)
                        )
                    )
                    .background(if (colors.isDark) colors.gray2 else Color.White, CircleShape)
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, option ->
                Segment(
                    text = option,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Segment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = remember { TouchTarget() }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectableTarget(target, selected, interaction, onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = LpmTheme.typography.footnote.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
