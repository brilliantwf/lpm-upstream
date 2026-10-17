package cx.lpm.mobile.feature.mac.machines

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.machines.MachineImportRow
import cx.lpm.mobile.core.data.machines.MachineImportStatus
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenuDivider
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.mac.R

/** The Mac switcher's rows for the live Mac's machines that aren't on this phone yet. */
@Composable
fun PendingMachinesRoute(viewModel: MachinesMenuViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PendingMachinesMenu(state.macName, state.pending, onAdd = viewModel::add)
}

/**
 * "Connected to <Mac>": each of its machines not on this phone, tappable only when
 * tapping can help — to retry a failure, re-add a removed one, or pair on a machine
 * that removed this phone.
 */
@Composable
internal fun PendingMachinesMenu(
    macName: String?,
    rows: List<MachineImportRow>,
    onAdd: (slug: String) -> Unit
) {
    if (rows.isEmpty()) return
    ContextMenuDivider()
    MenuSectionHeader(
        stringResource(
            R.string.mac_machines_connected_to,
            macName ?: stringResource(R.string.mac_machines_this_mac)
        )
    )
    rows.forEach { row ->
        val pending = pendingLook(row)
        ContextMenuItem(
            title = row.machine.name,
            subtitle = pending.detail,
            icon = painterResource(pending.icon),
            enabled = pending.tappable,
            onClick = { onAdd(row.machine.slug) }
        )
    }
}

private class PendingLook(val detail: String, val icon: Int, val tappable: Boolean)

@Composable
private fun pendingLook(row: MachineImportRow): PendingLook = when (val status = row.status) {
    MachineImportStatus.Adding ->
        PendingLook(stringResource(R.string.mac_machines_adding), DesignR.drawable.ic_sync, false)

    is MachineImportStatus.Failed -> PendingLook(
        stringResource(R.string.mac_machines_failed, status.message),
        DesignR.drawable.ic_warning,
        true
    )

    MachineImportStatus.Removed -> PendingLook(
        stringResource(R.string.mac_machines_tap_to_add),
        DesignR.drawable.ic_add_circle,
        true
    )

    MachineImportStatus.Revoked -> PendingLook(
        stringResource(R.string.mac_machines_revoked),
        DesignR.drawable.ic_mobile_off,
        true
    )

    MachineImportStatus.Offline -> PendingLook(
        stringResource(R.string.mac_machines_offline),
        if (row.machine.isLinuxHost) DesignR.drawable.ic_dns else DesignR.drawable.ic_desktop_mac,
        false
    )

    MachineImportStatus.NeedsUpdate -> PendingLook(
        stringResource(R.string.mac_machines_needs_update),
        DesignR.drawable.ic_arrow_circle_up,
        false
    )
}

/** A menu section's small gray title (a `Section("…")` in a `Menu`). */
@Composable
private fun MenuSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = LpmTheme.colors.secondaryLabel,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 27.dp, top = 4.dp, bottom = 4.dp)
    )
}
