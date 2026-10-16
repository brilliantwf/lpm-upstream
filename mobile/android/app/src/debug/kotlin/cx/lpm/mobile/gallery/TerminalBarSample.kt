package cx.lpm.mobile.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.GlassButtonGroup
import cx.lpm.mobile.core.designsystem.component.GlassGroupButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** The terminal screen's bar: always dark, over the terminal theme's background. */
@Composable
internal fun TerminalBarSample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LpmTheme(darkTheme = true) {
        val colors = LpmTheme.colors
        Column(
            modifier
                .fillMaxSize()
                .background(Color(0xFF2B2B2B))
        ) {
            LpmTopBar(
                title = "Terminal 1",
                leading = { BackButton(onBack) },
                trailing = {
                    Box {
                        GlassButtonGroup {
                            GlassGroupButton(painterResource(R.drawable.ic_bolt), "Actions", {
                            }, iconSize = 28.dp)
                            GlassGroupButton(painterResource(R.drawable.ic_more_horiz), "More", {
                            }, tint = colors.green, iconSize = 28.dp)
                        }
                    }
                }
            )
            Text(
                text = "~/dev/storefront $ ls",
                color = Color(0xFF60FDFF),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}
