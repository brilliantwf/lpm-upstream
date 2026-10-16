package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A button-role click with no ripple: iOS controls dim or highlight instead. */
fun Modifier.clickableNoIndication(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null
): Modifier = clickable(
    interactionSource = null,
    indication = null,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick
)
