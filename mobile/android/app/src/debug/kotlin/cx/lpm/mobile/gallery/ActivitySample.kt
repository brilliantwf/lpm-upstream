package cx.lpm.mobile.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.CapsuleTag
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.SearchField
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

@Composable
internal fun ActivitySample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        LpmTopBar(
            title = "Activity",
            leading = { BackButton(onBack) },
            trailing = {
                GlassIconButton(
                    icon = painterResource(R.drawable.ic_stacks),
                    contentDescription = "Group by project",
                    onClick = {},
                    iconSize = 27.dp
                )
            }
        )
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search projects and agents",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 9.dp)
        )
        SegmentedControl(
            options = listOf("All", "Agents", "Services", "Automations"),
            selectedIndex = kind,
            onSelect = { kind = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)
        )
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 34.dp, bottom = 8.dp)) {
            val style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold)
            Text(
                "SERVICES",
                style = style,
                color = colors.secondaryLabel,
                modifier = Modifier.weight(1f)
            )
            Text("2 of 3 running", style = style, color = colors.secondaryLabel)
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(20.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(colors.tertiarySystemGroupedBackground, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_dns),
                    null,
                    tint = colors.green,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "storefront",
                    style = LpmTheme.typography.body.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val chip = LpmTheme.typography.caption1.copy(fontWeight = FontWeight.Medium)
                    CapsuleTag(
                        "web",
                        contentColor = colors.green,
                        containerColor = colors.tertiarySystemFill,
                        textStyle = chip,
                        dot = colors.green
                    )
                    CapsuleTag(
                        "api",
                        contentColor = colors.green,
                        containerColor = colors.tertiarySystemFill,
                        textStyle = chip,
                        dot = colors.green
                    )
                    CapsuleTag(
                        "worker",
                        containerColor = colors.tertiarySystemFill,
                        textStyle = chip,
                        dot = colors.gray3
                    )
                }
                Text(
                    "web :3000  api :4000",
                    style = LpmTheme.typography.caption1,
                    color = colors.tertiaryLabel
                )
            }
            Box(
                Modifier
                    .size(32.dp)
                    .background(colors.tertiarySystemFill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_stop_fill),
                    "Stop everything in storefront",
                    tint = colors.red,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "4 other projects have nothing running.",
                "Activity includes supported agents started from projects opened during this session. Terminal sessions aren't tracked."
            ).forEach {
                Text(
                    it,
                    style = LpmTheme.typography.caption1,
                    color = colors.tertiaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
