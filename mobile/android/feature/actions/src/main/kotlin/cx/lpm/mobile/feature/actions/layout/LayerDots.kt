package cx.lpm.mobile.feature.actions.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.actions.R
import kotlin.math.roundToInt

/**
 * The open layer's name when any layer has one, then a dot per layer. The name steps
 * to the next layer, a dot opens its own. To accessibility it's an adjustable
 * "Layer" control whose value is the open layer, with that layer's name inside.
 */
@Composable
internal fun LayerDots(
    lists: List<ZoneList>,
    open: Int,
    onShow: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val name = stringResource(R.string.actions_zone_layer)
    val last = (lists.size - 1).coerceAtLeast(1)
    Row(
        modifier = modifier.semantics {
            contentDescription = name
            stateDescription = lists.getOrNull(open)?.title.orEmpty()
            progressBarRangeInfo =
                ProgressBarRangeInfo(open.toFloat(), 0f..last.toFloat(), steps = last - 1)
            setProgress { target ->
                onShow(target.roundToInt())
                true
            }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lists.any { it.layer?.label?.isNotEmpty() == true }) {
            val title = lists.getOrNull(open)?.title.orEmpty()
            Box(
                Modifier
                    .height(20.dp)
                    .plainClick { onShow((open + 1) % lists.size) }
                    .clearAndSetSemantics { contentDescription = title },
                contentAlignment = Alignment.Center
            ) {
                lists.forEachIndexed { index, list ->
                    Text(
                        text = list.title,
                        style = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
                        color = colors.secondaryLabel,
                        modifier = Modifier.alpha(if (index == open) 1f else 0f)
                    )
                }
            }
        }
        Row(Modifier.clearAndSetSemantics {}) {
            lists.indices.forEach { index ->
                val dot = if (index == open) colors.tint else colors.label.copy(alpha = 0.26f)
                Box(
                    Modifier
                        .size(width = 16.dp, height = 20.dp)
                        .plainClick { onShow(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(6.dp).background(dot, CircleShape))
                }
            }
        }
    }
}

private fun Modifier.plainClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)
