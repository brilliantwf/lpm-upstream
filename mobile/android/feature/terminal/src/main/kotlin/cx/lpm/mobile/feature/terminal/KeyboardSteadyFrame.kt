package cx.lpm.mobile.feature.terminal

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import cx.lpm.mobile.core.web.TextExtent

/**
 * Keeps its content at the height it has with the keyboard closed and clips it, so
 * the keyboard slides over the content instead of resizing it every animation
 * frame (each resize would reflow xterm and send the Mac a PTY resize). Text that
 * reaches the bottom stays pinned there, its top sliding out of sight; text that
 * ends above the keyboard stays pinned to the top. The content is told how much of
 * its top and bottom sits out of sight, which grows only once the keyboard has
 * settled and drops as soon as it starts closing — one reflow each way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyboardSteadyFrame(
    extent: TextExtent,
    modifier: Modifier = Modifier,
    content: @Composable (hidden: PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val frame = remember { SteadyFrame() }
    val ime = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    val bars = WindowInsets.navigationBars
    LaunchedEffect(frame, density) {
        snapshotFlow {
            val inset = bars.getBottom(density)
            Keyboard(
                height = (target.getBottom(density) - inset).coerceAtLeast(0),
                current = (ime.getBottom(density) - inset).coerceAtLeast(0)
            )
        }.collect(frame::keyboardChanged)
    }
    Layout(
        content = { content(frame.hiddenInsets(density)) },
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { frame.resized(it.height, extent, density) }
    ) { measurables, constraints ->
        val height = constraints.maxHeight
        val contentHeight = maxOf(frame.restHeight, height)
        val placeable = measurables.first().measure(
            Constraints.fixed(constraints.maxWidth, contentHeight)
        )
        layout(constraints.maxWidth, height) {
            placeable.place(0, if (frame.pinTop) 0 else height - contentHeight)
        }
    }
}

/** How far the keyboard covers the screen above the navigation bar: where it's headed, and now. */
private data class Keyboard(val height: Int, val current: Int) {
    /** False only once it has finished closing. */
    val shown: Boolean get() = height > 0 || current > 0
}

private class SteadyFrame {
    private var height = 0
    var restHeight by mutableIntStateOf(0)
        private set
    private var hidden by mutableIntStateOf(0)
    var pinTop by mutableStateOf(false)
        private set
    private var keyboard = Keyboard(0, 0)

    // The keyboard's height once it has finished opening; on closing it drops at once.
    private var settledHeight = 0

    fun hiddenInsets(density: Density): PaddingValues = with(density) {
        val out = hidden.toDp()
        if (pinTop) PaddingValues(bottom = out) else PaddingValues(top = out)
    }

    fun keyboardChanged(next: Keyboard) {
        keyboard = next
        if (next.height == next.current) settledHeight = next.height
        if (next.height < settledHeight) settledHeight = next.height
        settle()
    }

    fun resized(newHeight: Int, extent: TextExtent, density: Density) {
        if (newHeight < height) {
            pinTop = with(density) { extent.bottom.toPx() } <= newHeight
        } else if (newHeight > height) {
            pinTop = !extent.fillsOnGrow
        }
        height = newHeight
        restHeight = maxOf(restHeight, newHeight)
        hidden = minOf(hidden, restHeight - newHeight)
        settle()
    }

    private fun settle() {
        if (!keyboard.shown) restHeight = height
        if (keyboard.height == settledHeight) hidden = restHeight - height
    }
}
