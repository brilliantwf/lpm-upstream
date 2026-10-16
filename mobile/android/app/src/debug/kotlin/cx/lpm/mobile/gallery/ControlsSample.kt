package cx.lpm.mobile.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.CapsuleTag
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.HeaderBanner
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmStepper
import cx.lpm.mobile.core.designsystem.component.LpmSwitch
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.OutlinedTag
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.component.SecondaryButton
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.component.ValueRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

@Composable
internal fun ControlsSample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    var on by remember { mutableStateOf(true) }
    var segment by remember { mutableIntStateOf(2) }
    var step by remember { mutableIntStateOf(12) }
    Column(modifier.fillMaxSize()) {
        LpmTopBar(
            title = "Controls",
            subtitle = "Every shared component",
            leading = { BackButton(onBack) },
            trailing = { GlassCapsuleButton("Done", {}, textStyle = LpmTheme.typography.headline) }
        )
        HeaderBanner(
            text = "Demo — sample projects",
            icon = painterResource(R.drawable.ic_play_circle_fill),
            actionLabel = "Exit",
            onAction = {}
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 19.dp)
        ) {
            ListSection(header = "Switches and stepper", headerStyle = ListHeaderStyle.Title) {
                ValueRow(title = "Port", value = "8765")
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LpmSwitch(checked = on, onCheckedChange = {
                        on = it
                    }, contentDescription = "Sample")
                    LpmSwitch(checked = !on, onCheckedChange = {
                        on = !it
                    }, contentDescription = "Inverse")
                    LpmSwitch(checked = true, onCheckedChange = {
                    }, enabled = false, contentDescription = "Disabled")
                    LpmStepper(value = step, onValueChange = {
                        step = it
                    }, range = 8..24, label = "Font size")
                }
            }
            SegmentedControl(
                options = listOf("Today", "7 days", "30 days", "All time"),
                selectedIndex = segment,
                onSelect = { segment = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(colors.green)
                StatusDot(colors.secondaryLabel)
                StatusDot(colors.red)
                StatusDot(colors.green, hollow = true)
                CapsuleTag("storefront")
                CapsuleTag("SONNET 4.6")
                OutlinedTag("pool")
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(painterResource(R.drawable.ic_arrow_back_ios_new), "Back", {})
                GlassIconButton(painterResource(R.drawable.ic_add), "Add", {}, iconSize = 30.dp)
                GlassCapsuleButton("Mark all read", {}, textStyle = LpmTheme.typography.subheadline)
                LinkButton("Details", {}, textStyle = LpmTheme.typography.body)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Set up Built-in Tailscale", {})
                SecondaryButton("Set up Built-in Tailscale", {})
                PrimaryButton("Pair", {}, enabled = false)
            }
        }
    }
}
