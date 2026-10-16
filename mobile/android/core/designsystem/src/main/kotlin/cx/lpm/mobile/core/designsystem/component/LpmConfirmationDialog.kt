package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * SwiftUI's `.confirmationDialog` as iOS 26 shows it on an iPhone screen or sheet: a
 * 240 dp popover under the status bar, its arrow pointing down, with the title and
 * [message] over capsule buttons. The screen isn't dimmed and there is no Cancel
 * button — a tap outside or system back cancels.
 */
@Composable
fun LpmConfirmationDialog(
    title: String,
    buttons: List<AlertButton>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    val cancel = buttons.firstOrNull { it.role == AlertRole.Cancel }
    val dismiss = {
        cancel?.onClick?.invoke()
        onDismissRequest()
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(
            modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null, onClick = dismiss)
                .windowInsetsPadding(WindowInsets.statusBars),
            contentAlignment = Alignment.TopCenter
        ) {
            Popover(
                title,
                message,
                buttons.filter {
                    it.role != AlertRole.Cancel
                },
                onDismissRequest
            )
        }
    }
}

@Composable
private fun Popover(
    title: String,
    message: String?,
    buttons: List<AlertButton>,
    onDismissRequest: () -> Unit
) {
    val colors = LpmTheme.colors
    val shape = remember { PopoverShape(34.dp, ArrowWidth, ArrowHeight) }
    Column(
        modifier = Modifier
            .width(240.dp)
            .shadow(28.dp, shape, ambientColor = colors.scrim, spotColor = colors.scrim)
            .background(colors.menuBackground, shape)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp + ArrowHeight)
    ) {
        Text(
            text = title,
            style = LpmTheme.typography.headline,
            color = colors.label,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (message != null) {
            Text(
                text = message,
                style = LpmTheme.typography.subheadline,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            buttons.forEach { PopoverButton(it, onDismissRequest) }
        }
    }
}

@Composable
private fun PopoverButton(button: AlertButton, onDismissRequest: () -> Unit) {
    val colors = LpmTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.label.copy(alpha = 0.13f), CircleShape)
            .clickable(enabled = button.enabled, role = Role.Button) {
                button.onClick()
                onDismissRequest()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = button.label,
            style = LpmTheme.typography.body,
            color = if (button.role == AlertRole.Destructive) colors.red else colors.tint
        )
    }
}

/** A rounded card with an arrow under its middle, pointing down. */
private class PopoverShape(
    private val corner: Dp,
    private val arrowWidth: Dp,
    private val arrowHeight: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { corner.toPx() }
        val aw = with(density) { arrowWidth.toPx() }
        val ah = with(density) { arrowHeight.toPx() }
        val bodyBottom = size.height - ah
        val body = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, bodyBottom, CornerRadius(r)))
        }
        val mid = size.width / 2
        val arrow = Path().apply {
            moveTo(mid - aw / 2, bodyBottom - 1f)
            quadraticTo(mid - aw / 4, bodyBottom, mid - aw / 8, bodyBottom + ah * 0.7f)
            quadraticTo(mid, size.height + 1f, mid + aw / 8, bodyBottom + ah * 0.7f)
            quadraticTo(mid + aw / 4, bodyBottom, mid + aw / 2, bodyBottom - 1f)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, arrow))
    }
}

private val ArrowWidth = 30.dp
private val ArrowHeight = 11.dp
