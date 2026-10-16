package cx.lpm.mobile.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One button a row's swipe reveals (`swipeActions`): its symbol on [color]. */
class SwipeAction(
    val label: String,
    @param:DrawableRes val icon: Int,
    val color: Color,
    val onClick: () -> Unit
)

/**
 * A list row with iOS swipe actions: dragging it sideways uncovers [leading] (from
 * the left edge) or [trailing] (from the right) buttons, and it settles open or
 * shut. Picking one closes the row first. TalkBack gets the same actions.
 * [containerColor] is the row's own background, which covers the buttons while shut:
 * a card's, or the screen's in a plain list.
 */
@Composable
fun SwipeActionsBox(
    modifier: Modifier = Modifier,
    leading: List<SwipeAction> = emptyList(),
    trailing: List<SwipeAction> = emptyList(),
    containerColor: Color = LpmTheme.colors.secondarySystemGroupedBackground,
    content: @Composable () -> Unit
) {
    val width = with(LocalDensity.current) { ActionWidth.toPx() }
    val openStart = leading.size * width
    val openEnd = -trailing.size * width
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val drag = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceIn(openEnd, openStart)) }
    }
    fun settle(velocity: Float) = scope.launch {
        val value = offset.value
        val target = when {
            value < 0 && (value < openEnd / 2 || velocity < -FLING_VELOCITY) -> openEnd
            value > 0 && (value > openStart / 2 || velocity > FLING_VELOCITY) -> openStart
            else -> 0f
        }
        offset.animateTo(target)
    }
    fun pick(action: SwipeAction) {
        scope.launch { offset.animateTo(0f) }
        action.onClick()
    }
    Box(
        modifier
            .clipToBounds()
            .semantics {
                customActions = (leading + trailing).map {
                    CustomAccessibilityAction(it.label) {
                        it.onClick()
                        true
                    }
                }
            }
    ) {
        Row(Modifier.matchParentSize()) {
            leading.forEach { ActionButton(it, ::pick) }
            Box(Modifier.weight(1f))
            trailing.forEach { ActionButton(it, ::pick) }
        }
        Layout(
            content = content,
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(containerColor)
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    enabled = leading.isNotEmpty() || trailing.isNotEmpty(),
                    onDragStopped = { velocity -> settle(velocity) }
                )
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) { placeables.forEach { it.place(0, 0) } }
        }
    }
}

@Composable
private fun ActionButton(
    action: SwipeAction,
    onPick: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(ActionWidth)
            .fillMaxHeight()
            .background(action.color)
            .clickable(role = Role.Button, onClickLabel = action.label) { onPick(action) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = action.label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

private val ActionWidth = 74.dp
private const val FLING_VELOCITY = 600f
