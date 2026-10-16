package cx.lpm.mobile.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ButtonRow
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuDivider
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.ContextMenuSubmenu
import cx.lpm.mobile.core.designsystem.component.GlassButtonGroup
import cx.lpm.mobile.core.designsystem.component.GlassGroupButton
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ListSectionSpacing
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.NavigationRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

@Composable
internal fun ProjectDetailSample(
    onBack: () -> Unit,
    menuOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    var menu by remember { mutableStateOf(menuOpen) }
    var services by remember { mutableStateOf(setOf("web", "api")) }
    Column(modifier.fillMaxSize()) {
        LpmTopBar(
            title = "storefront",
            subtitle = "Running",
            leading = { BackButton(onBack) },
            trailing = {
                Box {
                    GlassButtonGroup {
                        GlassGroupButton(
                            icon = painterResource(R.drawable.ic_bolt),
                            contentDescription = "Actions",
                            onClick = {},
                            iconSize = 28.dp
                        )
                        GlassGroupButton(
                            icon = painterResource(R.drawable.ic_more_horiz),
                            contentDescription = "More",
                            onClick = { menu = true },
                            tint = colors.green,
                            iconSize = 28.dp
                        )
                    }
                    ContextMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ProjectMenuItems()
                    }
                }
            }
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = ListDefaults.ContentTopPadding)
        ) {
            ListSection(header = "Tabs", spacing = ListSectionSpacing.Compact) {
                NavigationRow(title = "Terminal 1", onClick = {})
                NavigationRow(title = "✨ Claude", onClick = {})
                ButtonRow(text = "New tab", onClick = {}, icon = painterResource(R.drawable.ic_add))
            }
            ListSection(
                header = "Services · ${services.size} of 3 running",
                headerAction = {
                    LinkButton(text = "Stop all", onClick = { services = emptySet() })
                },
                footer = "Tap a service for its logs.",
                spacing = ListSectionSpacing.Compact
            ) {
                listOf(
                    "web" to ":3000",
                    "api" to ":4000",
                    "worker" to null
                ).forEach { (name, port) ->
                    ToggleRow(
                        title = name,
                        detail = port,
                        checked = name in services,
                        onCheckedChange = { on ->
                            services =
                                if (on) services + name else services - name
                        },
                        onClick = {}
                    )
                }
            }
            UsageSection()
        }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    GlassIconButton(
        icon = painterResource(R.drawable.ic_arrow_back_ios_new),
        contentDescription = "Back",
        onClick = onBack,
        modifier = modifier
    )
}

@Composable
private fun ColumnScope.ProjectMenuItems() {
    ContextMenuItem(title = "Stop", onClick = {}, icon = painterResource(R.drawable.ic_stop_fill))
    ContextMenuItem(
        title = "Review Changes",
        onClick = {},
        icon = painterResource(R.drawable.ic_checklist),
        subtitle = "4 changed files"
    )
    ContextMenuSubmenu(title = "Git", icon = painterResource(R.drawable.ic_call_split)) {
        ContextMenuItem(title = "Pull", onClick = {
        }, icon = painterResource(R.drawable.ic_arrow_downward))
        ContextMenuItem(title = "Push", onClick = {
        }, icon = painterResource(R.drawable.ic_arrow_upward))
        ContextMenuItem(title = "Fetch", onClick = {}, icon = painterResource(R.drawable.ic_sync))
        ContextMenuDivider()
        ContextMenuItem(title = "Discard Changes…", onClick = {}, destructive = true)
    }
    ContextMenuItem(title = "Memory", onClick = {
    }, icon = painterResource(R.drawable.ic_psychology))
    ContextMenuItem(title = "Notes", onClick = {}, icon = painterResource(R.drawable.ic_article))
    ContextMenuItem(
        title = "Automations",
        onClick = {},
        icon = painterResource(R.drawable.ic_history),
        subtitle = "1 with new runs"
    )
    ContextMenuSubmenu(
        title = "Claude Account",
        icon = painterResource(R.drawable.ic_account_circle)
    ) {
        ContextMenuItem(title = "Work", onClick = {})
        ContextMenuItem(title = "Personal", onClick = {})
    }
    ContextMenuSubmenu(
        title = "Status",
        icon = painterResource(R.drawable.ic_sentiment_satisfied)
    ) {
        ContextMenuItem(title = "In progress", onClick = {})
        ContextMenuItem(title = "Blocked", onClick = {})
    }
    ContextMenuDivider()
    ContextMenuItem(title = "Rename…", onClick = {}, icon = painterResource(R.drawable.ic_edit))
    ContextMenuItem(title = "Configure…", onClick = {}, icon = painterResource(R.drawable.ic_tune))
    ContextMenuItem(title = "Terminal Settings", onClick = {
    }, icon = painterResource(R.drawable.ic_format_size))
}
