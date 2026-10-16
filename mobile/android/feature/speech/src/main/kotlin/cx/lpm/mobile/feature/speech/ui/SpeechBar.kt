package cx.lpm.mobile.feature.speech.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.R
import cx.lpm.mobile.feature.speech.data.SpeechState
import kotlin.math.roundToInt

/**
 * The floating transport for read-aloud (iOS `SpeechBar`): a timeline over a centred
 * transport, laid out like every podcast player. A clip from the Mac has a real
 * timeline, so it scrubs and skips by seconds; the phone's voice has no position, so
 * its track shows how far through the text it is and the skips move by sentence. The
 * layout is identical either way, so nothing moves under the thumb.
 */
@Composable
fun SpeechBar(
    state: SpeechState,
    onTogglePause: () -> Unit,
    onSkip: (Int) -> Unit,
    onSeek: (Double) -> Unit,
    onRateChange: (Double) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    Column(
        modifier
            .dropShadow(BarShape, BarShadow)
            .background(colors.menuBackground, BarShape)
            .border(1.dp, colors.label.copy(alpha = 0.08f), BarShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Timeline(state, onSeek)
        Transport(state, onTogglePause, onSkip, onRateChange, onStop)
    }
}

/**
 * While the Mac renders there is no position to show, so the row says what is
 * happening instead of animating a dead track.
 */
@Composable
private fun Timeline(state: SpeechState, onSeek: (Double) -> Unit) {
    val style = LpmTheme.typography.caption2.copy(fontFeatureSettings = "tnum")
    val color = LpmTheme.colors.secondaryLabel
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.isLoading) {
            Text(stringResource(R.string.speech_preparing), style = style, color = color)
            return@Row
        }
        Text(
            text = clock(state.elapsed),
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.width(34.dp)
        )
        SpeechScrubber(
            progress = state.progress,
            seekable = state.canSeek,
            onSeek = onSeek,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = trailingText(state),
            style = style.copy(textAlign = TextAlign.End),
            color = color,
            maxLines = 1,
            modifier = Modifier.width(40.dp)
        )
    }
}

/**
 * Speed and stop sit on the edges in equal-width slots so the three controls you
 * actually reach for stay centred under the thumb.
 */
@Composable
private fun Transport(
    state: SpeechState,
    onTogglePause: () -> Unit,
    onSkip: (Int) -> Unit,
    onRateChange: (Double) -> Unit,
    onStop: () -> Unit
) {
    val colors = LpmTheme.colors
    val seekable = state.canSeek
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            SpeedMenu(rate = state.rate, onRateChange = onRateChange)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportButton(
                label = stringResource(
                    if (seekable) R.string.speech_back_15 else R.string.speech_previous_sentence
                ),
                enabled = !state.isLoading,
                tint = colors.label,
                onClick = { onSkip(-1) }
            ) { tint -> SkipIcon(forward = false, seekable = seekable, tint = tint) }
            PlayPauseButton(state, onTogglePause)
            TransportButton(
                label = stringResource(
                    if (seekable) R.string.speech_forward_15 else R.string.speech_next_sentence
                ),
                enabled = !state.isLoading,
                tint = colors.label,
                onClick = { onSkip(1) }
            ) { tint -> SkipIcon(forward = true, seekable = seekable, tint = tint) }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            TransportButton(
                label = stringResource(R.string.speech_stop_reading),
                tint = colors.secondaryLabel,
                onClick = onStop
            ) { tint ->
                Icon(
                    painter = painterResource(DesignR.drawable.ic_close),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButton(state: SpeechState, onTogglePause: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val paused = state.mode == SpeechState.Mode.Paused
    val label = stringResource(if (paused) R.string.speech_play else R.string.speech_pause)
    Box(
        Modifier
            .size(46.dp)
            .background(LpmTheme.colors.tint, CircleShape)
            .clickableNoIndication(
                enabled = !state.isLoading,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onTogglePause()
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoading) {
            ActivityIndicator(size = ActivityIndicatorSize.Small, color = Color.White)
        } else {
            Icon(
                painter = painterResource(if (paused) PlayIcon else PauseIcon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(44.dp)
            .clickableNoIndication(
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        icon(if (enabled) tint else LpmTheme.colors.tertiaryLabel)
    }
}

/** A sentence skip, or SF Symbols' `gobackward.15` / `goforward.15` for a clip. */
@Composable
private fun SkipIcon(forward: Boolean, seekable: Boolean, tint: Color) {
    if (!seekable) {
        Icon(
            painter = painterResource(if (forward) NextIcon else PreviousIcon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
        return
    }
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_replay),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { if (forward) scaleX = -1f }
        )
        Text(
            text = stringResource(R.string.speech_skip_seconds),
            style = SkipDigits,
            color = tint,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private val PlayIcon = DesignR.drawable.ic_play_arrow_fill
private val PauseIcon = DesignR.drawable.ic_pause_fill
private val NextIcon = DesignR.drawable.ic_skip_next_fill
private val PreviousIcon = DesignR.drawable.ic_skip_previous_fill
private val BarShape = RoundedCornerShape(24.dp)
private val BarShadow =
    Shadow(12.dp, Color.Black.copy(alpha = 0.15f), offset = DpOffset(0.dp, 4.dp))
private val SkipDigits = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold)

private fun clock(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Time left when there is a clip to measure it against; otherwise how far through
 * the text the voice is, the only honest number the phone's voice can give.
 */
private fun trailingText(state: SpeechState): String = if (state.duration > 0) {
    "-" + clock(maxOf(0.0, state.duration - state.elapsed))
} else {
    "${(state.progress * 100).roundToInt()}%"
}
