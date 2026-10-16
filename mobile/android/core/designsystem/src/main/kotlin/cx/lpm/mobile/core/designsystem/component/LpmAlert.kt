package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** How an alert button reads: `.cancel` and `.destructive` roles, or a plain action. */
enum class AlertRole { Default, Cancel, Destructive }

data class AlertButton(
    val label: String,
    val role: AlertRole = AlertRole.Default,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {}
)

/**
 * SwiftUI's `.alert` as iOS 27 draws it: a 320 dp card with 34 dp corners, the
 * semibold title over a gray [message], both leading-aligned, an optional
 * [textField], and capsule buttons —
 * side by side when there are two, stacked otherwise with Cancel last. Any button
 * runs its action and then [onDismissRequest]; system back counts as Cancel.
 */
@Composable
fun LpmAlert(
    title: String,
    buttons: List<AlertButton>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    textField: (@Composable () -> Unit)? = null
) {
    val cancel = buttons.firstOrNull { it.role == AlertRole.Cancel }
    Dialog(
        onDismissRequest = {
            cancel?.onClick?.invoke()
            onDismissRequest()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(DIM)
        val colors = LpmTheme.colors
        Column(
            modifier = modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .background(colors.menuBackground, RoundedCornerShape(34.dp))
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp)
        ) {
            Text(
                text = title,
                style = LpmTheme.typography.headline,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            if (message != null) {
                Text(
                    text = message,
                    style = LpmTheme.typography.subheadline,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 7.dp)
                )
            }
            if (textField != null) {
                Spacer(Modifier.height(14.dp))
                textField()
            }
            Spacer(Modifier.height(21.dp))
            AlertButtons(buttons, onDismissRequest)
        }
    }
}

@Composable
private fun AlertButtons(buttons: List<AlertButton>, onDismissRequest: () -> Unit) {
    val ordered = buttons.filter { it.role != AlertRole.Cancel } +
        buttons.filter { it.role == AlertRole.Cancel }
    if (ordered.size == 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Side by side, Cancel leads.
            ordered.reversed().forEach {
                AlertButtonView(it, onDismissRequest, Modifier.weight(1f))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ordered.forEach { AlertButtonView(it, onDismissRequest, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun AlertButtonView(
    button: AlertButton,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (pressed) 0.6f else 1f)
            .background(colors.systemFill, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = button.enabled,
                role = Role.Button
            ) {
                button.onClick()
                onDismissRequest()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = button.label,
            style = LpmTheme.typography.body,
            color = when {
                !button.enabled -> colors.tertiaryLabel
                button.role == AlertRole.Destructive -> colors.red
                else -> colors.label
            },
            textAlign = TextAlign.Center
        )
    }
}

private const val DIM = 0.2f
