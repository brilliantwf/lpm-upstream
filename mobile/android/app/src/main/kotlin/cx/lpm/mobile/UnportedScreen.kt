package cx.lpm.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** Stands in for a destination whose feature isn't ported yet, so navigating there can't crash. */
@Composable
fun UnportedScreen(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(LpmTheme.colors.systemGroupedBackground)) {
        LpmTopBar(
            title = title,
            leading = {
                GlassIconButton(
                    icon = painterResource(R.drawable.ic_arrow_back_ios_new),
                    contentDescription = "Back",
                    onClick = onBack
                )
            }
        )
    }
}
