package cx.lpm.mobile.feature.terminal

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls `onTouch` whenever a finger lands anywhere inside, without taking part in
 * the touch, so the terminal, the composer and the bar all still get it — a
 * deliberate touch is the proof someone is looking (the desktop's attention.ts).
 */
internal fun Modifier.observeTouches(onTouch: () -> Unit): Modifier = pointerInput(onTouch) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) onTouch()
        }
    }
}
