package cx.lpm.mobile.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** "Opening terminal…" while its list loads; "Terminal unavailable" once the list lacks it. */
@Composable
internal fun TerminalPlaceholder(
    loading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    Column(modifier.fillMaxSize().background(colors.systemBackground)) {
        LpmTopBar(
            title = stringResource(R.string.terminal_fallback_title),
            leading = {
                GlassIconButton(
                    icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                    contentDescription = stringResource(R.string.terminal_back),
                    onClick = onBack
                )
            }
        )
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (loading) 8.dp else 12.dp)
            ) {
                if (loading) {
                    ActivityIndicator(
                        size = ActivityIndicatorSize.Regular,
                        color = colors.secondaryLabel
                    )
                    Text(
                        stringResource(R.string.terminal_opening),
                        style = LpmTheme.typography.subheadline,
                        color = colors.secondaryLabel
                    )
                } else {
                    Icon(
                        painterResource(DesignR.drawable.ic_terminal),
                        contentDescription = null,
                        tint = colors.secondaryLabel,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        stringResource(R.string.terminal_unavailable),
                        style = LpmTheme.typography.title2.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
