package cx.lpm.mobile.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.LargeTitleHeader
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

@Composable
internal fun PairingSample(modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LargeTitleHeader(
                title = "Connect to your Mac",
                subtitle = "lpm reaches your Mac from anywhere over Tailscale. " +
                    "Set it up first, or pair for this Wi-Fi only."
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                LinkButton(
                    text = "Watch how to connect",
                    onClick = {},
                    icon = painterResource(R.drawable.ic_smart_display_fill),
                    iconSize = 24.dp,
                    textStyle = LpmTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "  3:41",
                    style = LpmTheme.typography.callout,
                    color = colors.secondaryLabel
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.blue, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_language),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Use lpm anywhere", style = LpmTheme.typography.headline)
                    Text(
                        text = "Set up Tailscale first, so pairing also works on cellular. " +
                            "Without it, lpm only works on your Mac's Wi-Fi.",
                        style = LpmTheme.typography.subheadline,
                        color = colors.secondaryLabel
                    )
                }
            }
            PrimaryButton(text = "Set up Built-in Tailscale", onClick = {})
            LinkButton(
                text = "I use the Tailscale app",
                onClick = {},
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            LinkButton(
                text = "Pair for this Wi-Fi only",
                onClick = {},
                color = colors.secondaryLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        LinkButton(
            text = "No Mac nearby? Try the demo",
            onClick = {},
            icon = painterResource(R.drawable.ic_play_circle),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
