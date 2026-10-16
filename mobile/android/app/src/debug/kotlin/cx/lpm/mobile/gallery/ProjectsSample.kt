package cx.lpm.mobile.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.HeaderBanner
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.NavigationRow
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

@Composable
internal fun ProjectsSample(showSettings: Boolean, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    var settings by remember { mutableStateOf(showSettings) }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.background(colors.purple)) {
            LpmTopBar(
                trailing = {
                    Box {
                        GlassIconButton(
                            icon = painterResource(R.drawable.ic_more_horiz),
                            contentDescription = "More",
                            onClick = { settings = true },
                            iconSize = 28.dp
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 9.dp)
                                .size(8.dp)
                                .background(colors.blue, CircleShape)
                        )
                    }
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Demo Mac", style = LpmTheme.typography.headline)
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                            contentDescription = null,
                            tint = colors.secondaryLabel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusDot(color = colors.secondaryLabel, size = 6.dp)
                        Text(
                            "Demo · sample projects",
                            style = LpmTheme.typography.footnote,
                            color = colors.secondaryLabel
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HeaderBanner(
                text = "Demo — sample projects",
                icon = painterResource(R.drawable.ic_play_circle_fill),
                actionLabel = "Exit",
                onAction = {}
            )
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 36.dp)
        ) {
            ListSection {
                NavigationRow("storefront", {
                }, leading = { StatusDot(colors.green) }, leadingSlot = ListDefaults.DotSlot)
                NavigationRow("api-gateway", {
                }, leading = {
                    StatusDot(colors.secondaryLabel)
                }, leadingSlot = ListDefaults.DotSlot)
                NavigationRow("mobile-app", {
                }, leading = {
                    StatusDot(colors.secondaryLabel)
                }, leadingSlot = ListDefaults.DotSlot)
                ListRow(
                    onClick = {},
                    leading = {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                            contentDescription = null,
                            tint = colors.secondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    leadingSlot = ListLeadingSlot(width = 12.dp, spacing = 8.dp),
                    trailing = {
                        Text(
                            "2",
                            style = LpmTheme.typography.caption1,
                            color = colors.secondaryLabel
                        )
                    }
                ) {
                    Text(
                        "Client Work",
                        style = LpmTheme.typography.body.copy(fontWeight = FontWeight.Medium)
                    )
                }
                NavigationRow(
                    "blog",
                    {},
                    leading = { StatusDot(colors.secondaryLabel) },
                    leadingSlot = ListDefaults.DotSlot,
                    indent = 20.dp
                )
                NavigationRow(
                    "design-system",
                    {},
                    leading = { StatusDot(colors.secondaryLabel) },
                    leadingSlot = ListDefaults.DotSlot,
                    indent = 20.dp
                )
            }
            UsageSection()
        }
    }
    if (settings) {
        SettingsSheet(onDone = { settings = false })
    }
}

@Composable
internal fun UsageSection(modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    ListSection(
        modifier = modifier,
        header = "Usage",
        headerAction = {
            LinkButton(text = "Details", onClick = {}, textStyle = LpmTheme.typography.body)
        }
    ) {
        listOf("Work", "Personal", "Client", "Old laptop").forEach { name ->
            ListRow(
                onClick = {},
                leading = { StatusDot(Color(0xFFD97757)) },
                leadingSlot = ListLeadingSlot(width = 8.dp, spacing = 10.dp),
                trailing = {
                    Text(
                        "—",
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    DisclosureChevron()
                }
            ) {
                Text(name, style = LpmTheme.typography.body.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}
