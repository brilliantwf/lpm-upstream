package cx.lpm.mobile.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A page pushed inside the Settings sheet: its inline title, the back button, and a
 * scrolling `Form` of sections.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemGroupedBackground)
    ) {
        LpmTopBar(
            title = title,
            windowInsets = WindowInsets(0),
            modifier = Modifier.padding(top = 16.dp),
            leading = {
                GlassIconButton(
                    icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                    contentDescription = stringResource(R.string.settings_back),
                    onClick = onBack
                )
            }
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = ListDefaults.ContentTopPadding, bottom = 24.dp),
            content = content
        )
    }
}
