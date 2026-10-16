package cx.lpm.mobile.feature.files.preview

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/** How far a pinch-zoomed preview is magnified and moved, kept inside its frame. */
@Stable
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val zoomed: Boolean get() = scale > 1.01f

    /** Zooms by [zoom] about [centroid] and moves by [pan], in a frame of [size]. */
    fun transform(zoom: Float, pan: Offset, centroid: Offset, size: IntSize) {
        val next = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val center = size.toSize().center
        val moved = centroid - center - (centroid - center - offset) * (next / scale) + pan
        scale = next
        offset = clamp(moved, size)
    }

    /** A double tap zooms in on the spot tapped, or back out. */
    fun toggle(at: Offset, size: IntSize) {
        if (zoomed) {
            scale = 1f
            offset = Offset.Zero
        } else {
            transform(DOUBLE_TAP_SCALE, Offset.Zero, at, size)
        }
    }

    private fun clamp(offset: Offset, size: IntSize): Offset {
        val maxX = (scale - 1) * size.width / 2
        val maxY = (scale - 1) * size.height / 2
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    private companion object {
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

@Composable
internal fun rememberZoomState(): ZoomState = remember { ZoomState() }

/**
 * Pinch to zoom, double tap to zoom in or out. Two fingers always zoom and pan; one
 * finger pans only once zoomed and only when [panWithOneFinger] — a scrolling list
 * inside keeps one-finger drags.
 */
internal fun Modifier.zoomable(state: ZoomState, panWithOneFinger: Boolean): Modifier = this
    .pointerInput(state) { detectTapGestures(onDoubleTap = { state.toggle(it, size) }) }
    .pointerInput(state, panWithOneFinger) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val fingers = event.changes.count { it.pressed }
                if (fingers >= 2 || (fingers == 1 && panWithOneFinger && state.zoomed)) {
                    state.transform(
                        zoom = event.calculateZoom(),
                        pan = event.calculatePan(),
                        centroid = event.calculateCentroid(useCurrent = true),
                        size = size
                    )
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }
