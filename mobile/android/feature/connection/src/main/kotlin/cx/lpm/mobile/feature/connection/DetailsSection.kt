package cx.lpm.mobile.feature.connection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** The `DisclosureGroup("Details")`: port, the address in use, each check, the last error. */
@Composable
internal fun DetailsSection(state: ConnectionSheetUiState) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "disclosure")
    val link = state.link?.link
    val mono = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace)
    ListSection {
        ListRow(onClick = { open = !open }, trailing = {
            Icon(
                painter = painterResource(DesignR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = LpmTheme.colors.tint,
                modifier = Modifier.size(22.dp).rotate(turn)
            )
        }) {
            Text(stringResource(R.string.details), style = LpmTheme.typography.body)
        }
        if (open) {
            DetailRow(stringResource(R.string.port), state.reached?.port?.toString().orEmpty())
            link?.host?.let {
                DetailRow(stringResource(R.string.connection_address_in_use), it, valueStyle = mono)
            }
            state.reached?.hosts.orEmpty().forEach { host ->
                link?.checks?.get(host)?.let { check ->
                    val small = LpmTheme.typography.footnote.copy(fontFamily = FontFamily.Monospace)
                    DetailRow(host, check.detail, titleStyle = small, valueStyle = small)
                }
            }
            state.lastError?.let { DetailRow(stringResource(R.string.connection_last_error), it) }
        }
    }
}

@Composable
private fun DetailRow(
    title: String,
    value: String,
    titleStyle: TextStyle = LpmTheme.typography.body,
    valueStyle: TextStyle = LpmTheme.typography.body
) {
    ListRow(trailing = {
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = valueStyle,
            color = LpmTheme.colors.secondaryLabel,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }) {
        Text(title, style = titleStyle)
    }
}
