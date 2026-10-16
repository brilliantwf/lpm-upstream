package cx.lpm.mobile.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.CapsuleTag
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

@Composable
internal fun AutomationsSample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Column(modifier.fillMaxSize()) {
        LpmTopBar(
            title = "Automations",
            leading = {
                BackButton(onBack)
                GlassCapsuleButton(text = "Mark all read", onClick = {
                }, textStyle = LpmTheme.typography.subheadline)
            },
            trailing = {
                GlassIconButton(
                    icon = painterResource(R.drawable.ic_add),
                    contentDescription = "New automation",
                    onClick = {},
                    iconSize = 30.dp
                )
            }
        )
        Column(Modifier.padding(top = ListDefaults.ContentTopPadding)) {
            ListSection(header = "New", headerStyle = ListHeaderStyle.Title) {
                AutomationRow(
                    emoji = "🌙",
                    name = "Nightly tests",
                    scope = "storefront",
                    status = "1 new message · Every day at 03:00 · in 6h",
                    statusColor = colors.green,
                    unread = true
                )
            }
            ListSection(header = "Earlier", headerStyle = ListHeaderStyle.Title) {
                AutomationRow(
                    emoji = "📦",
                    name = "Weekly dependency check",
                    scope = "blog",
                    status = "Off · Mon at 06:00",
                    statusColor = colors.gray,
                    unread = false,
                    modifier = Modifier.alpha(0.6f)
                )
            }
        }
    }
}

@Composable
private fun AutomationRow(
    emoji: String,
    name: String,
    scope: String,
    status: String,
    statusColor: Color,
    unread: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    ListRow(modifier = modifier, onClick = {}, trailing = { DisclosureChevron() }) {
        StatusDot(if (unread) colors.tint else Color.Transparent)
        Spacer(Modifier.width(10.dp))
        Text(
            emoji,
            style = LpmTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).padding(vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    name,
                    style = LpmTheme.typography.body.copy(
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                CapsuleTag(scope)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                StatusDot(statusColor, size = 6.dp)
                Text(
                    status,
                    style = LpmTheme.typography.caption1,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
