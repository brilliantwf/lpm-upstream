package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.core.navigation.SheetStack
import cx.lpm.mobile.core.navigation.rememberSheetNavigator
import java.time.Instant

internal sealed interface MachinesPage {
    data object List : MachinesPage

    data class Machine(val macId: String) : MachinesPage

    data class Addresses(val macId: String) : MachinesPage
}

/** "Manage machines…": the saved machines, each opening its name, addresses and removal. */
@Composable
internal fun ManageMacsSheet(
    viewModel: ManageMacsViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val sheet = rememberModalSheetState()
    ModalSheet(onDismissRequest = onDismiss, state = sheet) {
        PresentationScope {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val navigator = rememberSheetNavigator<MachinesPage>(MachinesPage.List)
            SheetStack(navigator) { page ->
                when (page) {
                    MachinesPage.List -> MachineList(
                        state = state,
                        onDone = { sheet.dismiss(onDismiss) },
                        onOpen = { navigator.push(MachinesPage.Machine(it)) },
                        onAdd = {
                            viewModel.addMachineAfterClose()
                            sheet.dismiss(onDismiss)
                        }
                    )

                    is MachinesPage.Machine -> MacDetailPage(
                        macId = page.macId,
                        state = state,
                        onBack = navigator::back,
                        onAddresses = { navigator.push(MachinesPage.Addresses(page.macId)) },
                        onRename = viewModel::rename,
                        onSwitch = viewModel::switchTo,
                        onRemove = {
                            navigator.back()
                            viewModel.remove(page.macId)
                        }
                    )

                    is MachinesPage.Addresses -> EditEndpointPage(
                        macId = page.macId,
                        session = SessionKey.Live,
                        onBack = navigator::back
                    )
                }
            }
        }
    }
}

@Composable
private fun MachineList(
    state: ManageMacsUiState,
    onDone: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit
) {
    val words = reachWords()
    val inUse = stringResource(R.string.machines_in_use, state.statusLine?.text.orEmpty())
    SheetPage(title = stringResource(R.string.machines_title), trailing = { DoneButton(onDone) }) {
        SheetForm {
            ListSection(footer = stringResource(R.string.machines_footer)) {
                state.macs.forEach { mac ->
                    val status = if (mac.localId == state.activeMacId) {
                        inUse
                    } else {
                        reachText(state.reach[mac.localId], mac.lastConnected, Instant.now(), words)
                    }
                    MachineRow(mac, status) { onOpen(mac.localId) }
                }
            }
            ListSection {
                ActionRow(stringResource(R.string.add_machine), DesignR.drawable.ic_add, onAdd)
            }
        }
    }
}

@Composable
private fun MachineRow(mac: MacRecord, status: String, onClick: () -> Unit) {
    val colors = LpmTheme.colors
    ListRow(
        onClick = onClick,
        leadingSlot = ListLeadingSlot(width = 30.dp, spacing = 12.dp),
        verticalPadding = 9.dp,
        leading = {
            Box(
                Modifier
                    .size(30.dp)
                    .background(colors.gray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(machineIcon(mac)),
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailing = { DisclosureChevron() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                mac.displayName,
                style = LpmTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(status, style = LpmTheme.typography.footnote, color = colors.secondaryLabel)
        }
    }
}

@Composable
internal fun reachWords(): ReachWords {
    val online = stringResource(R.string.reach_online)
    val checking = stringResource(R.string.reach_checking)
    val notAnswering = stringResource(R.string.reach_not_answering)
    val last = stringResource(R.string.reach_last_connected)
    return ReachWords(online, checking, notAnswering) { last.format(it) }
}
