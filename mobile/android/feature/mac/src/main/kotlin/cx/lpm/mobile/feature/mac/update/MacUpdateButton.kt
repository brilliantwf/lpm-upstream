package cx.lpm.mobile.feature.mac.update

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.model.BusyAgentPlace
import cx.lpm.mobile.feature.mac.R

/**
 * Starts the update on the Mac, with a warning first when agents there are
 * mid-task: the restart the update ends in would cut them off. [button] draws the
 * control and calls the start it is handed.
 */
@Composable
internal fun MacUpdateButton(
    macLabel: String,
    busyPlaces: () -> List<BusyAgentPlace>,
    onInstall: () -> Unit,
    button: @Composable (start: () -> Unit) -> Unit
) {
    var busy by remember { mutableStateOf<List<BusyAgentPlace>>(emptyList()) }
    var confirming by remember { mutableStateOf(false) }
    button {
        busy = busyPlaces()
        if (busy.isEmpty()) onInstall() else confirming = true
    }
    if (confirming) {
        val total = busy.sumOf { it.count }
        val agents = pluralStringResource(R.plurals.mac_update_agents, total, total)
        val places = ListFormatter.getInstance().format(busy.map { it.label })
        LpmConfirmationDialog(
            title = stringResource(
                if (total == 1) R.string.mac_update_busy_one else R.string.mac_update_busy_many
            ),
            message = stringResource(R.string.mac_update_busy_message, macLabel, agents, places),
            buttons = listOf(
                AlertButton(stringResource(R.string.mac_update_anyway), AlertRole.Destructive) {
                    onInstall()
                },
                AlertButton(stringResource(R.string.mac_update_cancel), AlertRole.Cancel)
            ),
            onDismissRequest = { confirming = false }
        )
    }
}
