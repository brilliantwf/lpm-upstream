package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** SwiftUI's `Divider()` at an opacity: a hairline across, or [length] tall when [vertical]. */
@Composable
internal fun ComposerDivider(
    modifier: Modifier = Modifier,
    alpha: Float = 0.5f,
    vertical: Boolean = false,
    length: Dp = 18.dp
) {
    val color = LpmTheme.colors.separator.copy(alpha = LpmTheme.colors.separator.alpha * alpha)
    Box(
        if (vertical) {
            modifier.width(Hairline).height(length).background(color)
        } else {
            modifier.fillMaxWidth().height(Hairline).background(color)
        }
    )
}

private val Hairline = 0.5.dp
