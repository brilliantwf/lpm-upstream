package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val SwipeDistance = 40.dp

/**
 * Calls `onDismiss` on a downward swipe over this view, as Messages closes its
 * keyboard. It only watches, so the view's own gestures still run; disabled, it
 * leaves them alone entirely (a field scrolling its text).
 */
internal fun Modifier.swipeDownDismissesKeyboard(
    enabled: Boolean = true,
    onDismiss: () -> Unit
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(onDismiss) {
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                        it.pressed
                    }
                        ?: continue
                val start = down.position
                var end = start
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    end = change.position
                    if (!change.pressed) break
                }
                val dy = end.y - start.y
                if (dy > SwipeDistance.toPx() && dy > abs(end.x - start.x) * 2) onDismiss()
            }
        }
    }
}
