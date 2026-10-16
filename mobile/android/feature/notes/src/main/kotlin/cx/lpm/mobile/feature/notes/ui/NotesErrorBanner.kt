package cx.lpm.mobile.feature.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R

/**
 * A failure pinned under the bar rather than an alert: the reply can land while the
 * prompt that started it is still dismissing, and an alert raised then is lost.
 */
@Composable
internal fun NotesErrorBanner(message: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val orange = LpmTheme.colors.orange
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(barMaterial())
            .padding(start = 14.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_error_fill),
            contentDescription = null,
            tint = orange,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            style = LpmTheme.typography.footnote,
            color = orange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 9.dp)
        )
        Box(
            Modifier
                .size(36.dp)
                .clickableNoIndication(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_close),
                contentDescription = stringResource(R.string.notes_close),
                tint = orange,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
