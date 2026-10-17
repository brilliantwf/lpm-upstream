package cx.lpm.mobile.feature.mac.machines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.machines.MachineImportStatus
import cx.lpm.mobile.core.data.machines.MachineSection
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenuDivider
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.feature.mac.R

/** The projects "…" menu's "Pair Directly with …" rows, wired to the live Mac's machines. */
@Composable
fun PairDirectlyRoute(viewModel: MachinesMenuViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PairDirectlyMenuItems(state.pairDirectly, onPair = viewModel::add)
}

/**
 * Rows that pair this phone with a machine it otherwise opens through the live Mac:
 * one removed from this phone, one whose automatic pairing failed, or one that
 * removed this phone, which only pairing on the machine itself can bring back.
 */
@Composable
internal fun PairDirectlyMenuItems(sections: List<MachineSection>, onPair: (slug: String) -> Unit) {
    if (sections.isEmpty()) return
    ContextMenuDivider()
    sections.forEach { section ->
        val pending = section.pending
        ContextMenuItem(
            title = stringResource(R.string.mac_machines_pair_directly, section.machine.name),
            subtitle = when (pending) {
                is MachineImportStatus.Failed -> pending.message
                MachineImportStatus.Revoked -> stringResource(R.string.mac_machines_revoked_detail)
                else -> stringResource(R.string.mac_machines_pair_directly_detail)
            },
            icon = painterResource(DesignR.drawable.ic_link),
            onClick = { onPair(section.machine.slug) }
        )
    }
}
