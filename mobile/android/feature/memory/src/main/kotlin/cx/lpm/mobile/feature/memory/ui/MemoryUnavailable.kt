package cx.lpm.mobile.feature.memory.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** `ContentUnavailableView` with a description and, optionally, a `.borderedProminent` button. */
@Composable
internal fun MemoryUnavailable(
    title: String,
    icon: Painter,
    description: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    EmptyState(
        title = title,
        icon = icon,
        modifier = modifier,
        description = {
            Text(
                text = description,
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        },
        actions = action?.let {
            {
                CapsuleButton(
                    text = it,
                    onClick = onAction,
                    prominent = true,
                    textStyle = LpmTheme.typography.headline
                )
            }
        }
    )
}
