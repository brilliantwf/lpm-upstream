package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * Neighboring toolbar buttons sharing one glass capsule, as iOS 26 groups them
 * (the project's ⚡ and "…"). Fill it with [GlassGroupButton]s.
 */
@Composable
fun GlassButtonGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .glass(CircleShape, LpmTheme.colors)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
