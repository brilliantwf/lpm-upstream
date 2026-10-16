package cx.lpm.mobile.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.NavigationRow
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.SheetScaffold
import cx.lpm.mobile.core.designsystem.component.StepperRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private enum class Appearance(val label: String, val icon: Int) {
    System("System", R.drawable.ic_smartphone),
    Light("Light", R.drawable.ic_light_mode),
    Dark("Dark", R.drawable.ic_dark_mode)
}

private val Schemes = listOf(
    "Default",
    "One Dark",
    "Monokai",
    "Dracula",
    "Nord",
    "Solarized Dark",
    "GitHub Dark",
    "Claude Dark"
)

@Composable
internal fun SettingsSheet(onDone: () -> Unit, modifier: Modifier = Modifier) {
    ModalSheet(onDismissRequest = onDone, modifier = modifier) {
        SheetScaffold(title = "Settings", onDone = onDone) {
            SettingsContent()
        }
    }
}

@Composable
private fun SettingsContent(modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    var appearance by remember { mutableStateOf(Appearance.System) }
    var fontSize by remember { mutableIntStateOf(12) }
    var scheme by remember { mutableStateOf("Claude Dark") }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 19.dp)
    ) {
        ListSection(
            header = "Away from home",
            headerStyle = ListHeaderStyle.Title,
            footer = "Reach your Mac over cellular or any network, no Tailscale app needed."
        ) {
            NavigationRow(
                title = "Built-in Tailscale",
                onClick = {},
                value = "Off",
                leading = { SettingsIcon(R.drawable.ic_language) }
            )
        }
        ListSection(header = "Appearance", headerStyle = ListHeaderStyle.Title) {
            PickerRow(
                title = "Theme",
                options = Appearance.entries,
                selected = appearance,
                onSelect = { appearance = it },
                optionLabel = { it.label },
                optionIcon = { it.icon }
            )
        }
        ListSection(
            header = "Terminal",
            headerStyle = ListHeaderStyle.Title,
            footer = "Font size and color scheme for the built-in terminal."
        ) {
            StepperRow(
                title = "Font size",
                value = fontSize,
                onValueChange = {
                    fontSize = it
                },
                range =
                    8..24
            )
            PickerRow(
                title = "Color scheme",
                options = Schemes,
                selected = scheme,
                onSelect = { scheme = it },
                optionLabel = { it }
            )
            ListRow {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Color(0xFFFF5E59), Color(0xFFFFBD2E), Color(0xFF3DC94A)).forEach {
                            Box(Modifier.size(10.dp).background(it, CircleShape))
                        }
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.green)) { append("$ ") }
                            withStyle(SpanStyle(color = Color(0xFFC8C8C8))) { append("lpm run") }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp
                    )
                }
            }
        }
        ListSection {
            NavigationRow(
                title = "Read aloud",
                onClick = {},
                leading = { SettingsIcon(R.drawable.ic_volume_up) }
            )
            NavigationRow(
                title = "Notifications",
                onClick = {},
                leading = { SettingsIcon(R.drawable.ic_notifications_unread) }
            )
        }
    }
}

@Composable
private fun SettingsIcon(icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = LpmTheme.colors.tint,
        modifier = modifier.size(26.dp)
    )
}
