package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private val Corner = 24.dp
private const val EXPAND_MS = 150

// The compact side gutters (44 each: edge inset + a 36 dp control + gap) and the
// field's own text insets.
private val CompactChrome = 88.dp + 16.dp
private val ExpandedChrome = 12.dp + 16.dp

/**
 * The message pill, ChatGPT-style: while the prompt fits one line the controls sit
 * inline (+ · field · send); once it wraps or holds a newline the field spans the
 * pill and the controls drop to a row below it. The field is the same node in both
 * layouts, so the keyboard stays up across the change; only the paddings move.
 */
@Composable
internal fun ComposerPill(
    input: TextFieldState,
    state: ComposerUiState,
    look: ComposerLook,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
    onHideKeyboard: () -> Unit,
    plusMenu: @Composable () -> Unit,
    sendButton: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LpmTheme.typography.body
    var width by remember { mutableIntStateOf(0) }
    val text = input.text.toString()

    // The wrap decision is made at the compact layout's fixed text width, so
    // expanding (which widens the field) can't flip it back at the boundary.
    fun lines(chrome: androidx.compose.ui.unit.Dp): Int {
        val available = width - with(density) { chrome.roundToPx() }
        if (width <= 0 || available <= 0) return 1
        return measurer.measure(
            text.ifEmpty {
                " "
            },
            style,
            constraints = Constraints(maxWidth = available)
        ).lineCount
    }
    val expanded = '\n' in text || lines(CompactChrome) > 1
    // At its five-line cap the field scrolls its own text: a downward swipe there
    // scrolls back rather than closing the keyboard.
    val scrollsText = expanded && lines(ExpandedChrome) >= MAX_FIELD_LINES
    val horizontal by animateDpAsState(if (expanded) 6.dp else 44.dp, tween(EXPAND_MS), label = "h")
    val top by animateDpAsState(if (expanded) 4.dp else 0.dp, tween(EXPAND_MS), label = "top")
    val bottom by animateDpAsState(
        if (expanded) 40.dp else 0.dp,
        tween(EXPAND_MS),
        label = "bottom"
    )
    val controlsBottom = if (expanded) 2.dp else 1.dp
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .swipeDownDismissesKeyboard(enabled = !scrollsText, onDismiss = onHideKeyboard)
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .clip(RoundedCornerShape(Corner))
            .background(look.field)
    ) {
        ComposerField(
            input = input,
            placeholder = look.placeholder,
            argumentHint = state.slashArgumentHint,
            enabled = look.enabled && !state.composer.transforming,
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            onHardwareReturn = onSend,
            modifier = Modifier.padding(
                start = horizontal,
                end = horizontal,
                top = top,
                bottom = bottom
            )
        )
        Box(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = controlsBottom)) {
            plusMenu()
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = controlsBottom)) {
            sendButton()
        }
        if (state.composer.transforming) ShimmerBorder(Corner, Modifier.matchParentSize())
    }
}

private const val MAX_FIELD_LINES = 5
