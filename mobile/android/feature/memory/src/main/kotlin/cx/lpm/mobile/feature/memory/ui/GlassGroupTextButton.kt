package cx.lpm.mobile.feature.memory.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A text button sharing a `GlassButtonGroup` with symbol buttons ("Edit" beside the trash). */
@Composable
internal fun GlassGroupTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = LpmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickable(
                interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .height(44.dp)
            .alpha(if (pressed) 0.4f else 1f)
            .padding(start = 14.dp, end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = LpmTheme.typography.body,
            color = if (enabled) colors.label else colors.tertiaryLabel,
            maxLines = 1
        )
    }
}
