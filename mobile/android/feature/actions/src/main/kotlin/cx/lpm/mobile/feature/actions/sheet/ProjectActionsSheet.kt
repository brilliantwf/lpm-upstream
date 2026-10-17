package cx.lpm.mobile.feature.actions.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.ui.ActionErrorsNotice
import cx.lpm.mobile.feature.actions.R as ActionsR
import cx.lpm.mobile.feature.actions.button.ActionCallbacks
import cx.lpm.mobile.feature.actions.button.DesktopActionButton
import cx.lpm.mobile.feature.actions.layout.ActionRowItem
import cx.lpm.mobile.feature.actions.layout.ActionZoneView

/**
 * Every project action as the desktop shows them (iOS `ProjectActionsSheet`): the
 * header row and the footer row, each with its zones framed among the buttons, and
 * the menu-only list, under a note naming any the Mac left out for a config problem.
 * "+" adds an action, and a long press edits one.
 */
@Composable
fun ProjectActionsSheet(
    state: ProjectActionsUiState,
    callbacks: ActionCallbacks,
    onShowLayer: (zone: String, layer: String) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        LpmTopBar(
            title = stringResource(ActionsR.string.actions_title),
            modifier = Modifier.padding(top = 16.dp),
            windowInsets = WindowInsets(0),
            trailing = {
                GlassIconButton(
                    icon = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(ActionsR.string.actions_new),
                    onClick = onNew,
                    iconSize = 30.dp
                )
            }
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // iOS's bar under the grabber ends 7 dp lower than ours; its
                // section titles' line boxes are 3 dp shorter.
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val rows = state.rows
            if (state.actionErrors.isNotEmpty()) ActionErrorsNotice(state.actionErrors)
            Section(
                stringResource(ActionsR.string.actions_header),
                rows.header,
                false,
                state,
                callbacks,
                onShowLayer
            )
            Section(
                stringResource(ActionsR.string.actions_footer),
                rows.footer,
                true,
                state,
                callbacks,
                onShowLayer
            )
            Section(
                stringResource(ActionsR.string.actions_menu),
                rows.menu.map { ActionRowItem.Button(it) },
                false,
                state,
                callbacks,
                onShowLayer
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(
    title: String,
    items: List<ActionRowItem>,
    compact: Boolean,
    state: ProjectActionsUiState,
    callbacks: ActionCallbacks,
    onShowLayer: (zone: String, layer: String) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = LpmTheme.colors.secondaryLabel
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                key(item.id) {
                    when (item) {
                        is ActionRowItem.Button ->
                            DesktopActionButton(item.action, callbacks, compact = compact)

                        is ActionRowItem.Frame -> ActionZoneView(
                            zone = item.zone,
                            lists = item.lists,
                            open = state.openIndex(item.zone.name, item.lists),
                            callbacks = callbacks,
                            onShow = { layer -> onShowLayer(item.zone.name, layer) },
                            compact = compact
                        )
                    }
                }
            }
        }
    }
}
