package cx.lpm.mobile.feature.memory.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.memory.R

/** "Changed on your Mac by an agent — reloaded the latest version." with "Dismiss". */
@Composable
internal fun ConflictBanner(notice: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val style = LpmTheme.typography.caption1
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.secondarySystemBackground, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_sync),
            contentDescription = null,
            tint = colors.label,
            modifier = Modifier.size(16.dp)
        )
        Text(text = notice, style = style, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.memory_session_dismiss),
            style = style.copy(fontWeight = FontWeight.SemiBold),
            color = colors.tint,
            modifier = Modifier.clickableNoIndication(onClick = onDismiss)
        )
    }
}
