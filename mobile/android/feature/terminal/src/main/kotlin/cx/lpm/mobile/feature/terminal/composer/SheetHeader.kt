package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A sheet's inline title with glass text buttons: a leading cancellation and/or a
 * trailing confirmation, as a `NavigationStack` toolbar draws them.
 */
@Composable
internal fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: Pair<String, () -> Unit>? = null,
    trailing: Pair<String, () -> Unit>? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .height(44.dp)
    ) {
        leading?.let { (label, action) ->
            GlassCapsuleButton(
                text = label,
                onClick = action,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        Text(
            text = title,
            style = LpmTheme.typography.headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
        )
        trailing?.let { (label, action) ->
            GlassCapsuleButton(
                text = label,
                onClick = action,
                textStyle = LpmTheme.typography.headline,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
