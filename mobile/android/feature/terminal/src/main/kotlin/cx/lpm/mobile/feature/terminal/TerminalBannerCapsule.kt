package cx.lpm.mobile.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** The floating dark capsule the terminal's banners sit in. */
@Composable
internal fun TerminalBannerCapsule(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun BannerIcon(painter: Painter, modifier: Modifier = Modifier) {
    Icon(painter, contentDescription = null, tint = Color.White, modifier = modifier.size(17.dp))
}

@Composable
internal fun BannerText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LpmTheme.typography.footnote,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
