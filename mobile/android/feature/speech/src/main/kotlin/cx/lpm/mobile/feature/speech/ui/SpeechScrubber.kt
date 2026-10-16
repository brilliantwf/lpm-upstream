package cx.lpm.mobile.feature.speech.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.R
import kotlin.math.roundToInt

/**
 * The timeline. A clip can be scrubbed, so it carries a knob and a track that
 * thickens under the finger, following it live so a scrub can be aimed (a plain tap
 * seeks too); the phone's voice gets the same shape without them.
 */
@Composable
fun SpeechScrubber(
    progress: Double,
    seekable: Boolean,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val haptics = LocalHapticFeedback.current
    val seek by rememberUpdatedState(onSeek)
    var dragRatio by remember { mutableStateOf<Double?>(null) }
    val track by animateDpAsState(
        if (dragRatio ==
            null
        ) {
            5.dp
        } else {
            8.dp
        },
        tween(150),
        label = "track"
    )
    val position = stringResource(R.string.speech_position)
    BoxWithConstraints(
        modifier
            .height(22.dp)
            .clearAndSetSemantics {
                contentDescription = position
                stateDescription = "${(progress * 100).roundToInt()}%"
            }
            .then(
                if (seekable) {
                    Modifier.pointerInput(Unit) {
                        val width = size.width.coerceAtLeast(1).toFloat()
                        fun ratio(x: Float) = (x / width).toDouble().coerceIn(0.0, 1.0)
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            dragRatio = ratio(down.position.x)
                            var last = down.position.x
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                last = change.position.x
                                if (!change.pressed) break
                                change.consume()
                                dragRatio = ratio(last)
                            }
                            seek(ratio(last))
                            dragRatio = null
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val ratio = (dragRatio ?: progress).coerceIn(0.0, 1.0).toFloat()
        Box(
            Modifier
                .fillMaxWidth()
                .height(track)
                .background(colors.label.copy(alpha = 0.12f), CircleShape)
        )
        Box(
            Modifier
                .fillMaxWidth(ratio)
                .height(track)
                .background(colors.tint, CircleShape)
        )
        if (seekable) {
            val x = (maxWidth * ratio - Knob / 2).coerceIn(
                0.dp,
                (maxWidth - Knob).coerceAtLeast(0.dp)
            )
            Box(
                Modifier
                    .offset(x = x)
                    .size(Knob)
                    .dropShadow(
                        CircleShape,
                        Shadow(2.dp, Color.Black.copy(alpha = 0.25f), offset = DpOffset(0.dp, 1.dp))
                    )
                    .background(Color.White, CircleShape)
            )
        }
    }
}

private val Knob = 12.dp
