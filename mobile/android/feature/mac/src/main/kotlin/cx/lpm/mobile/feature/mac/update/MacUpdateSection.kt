package cx.lpm.mobile.feature.mac.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ValueRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.mac.R
import cx.lpm.mobile.feature.mac.data.MacUpdatePhase

/** The connection sheet's "lpm" section for `session`'s Mac, wired to its ViewModel. */
@Composable
fun MacUpdateSectionRoute(session: SessionKey = SessionKey.Live) {
    val viewModel = macUpdateViewModel(session)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MacUpdateSection(
        state = state,
        actions = MacUpdateBarActions(
            install = viewModel::install,
            cancel = viewModel::cancel,
            busyPlaces = viewModel::busyPlaces
        )
    )
}

/**
 * The lpm a Mac runs, in its connection details, and the update to a newer one —
 * still offered here after the bar over the projects list was closed (iOS
 * `MacUpdateSection`).
 */
@Composable
fun MacUpdateSection(
    state: MacUpdateUiState,
    actions: MacUpdateBarActions,
    modifier: Modifier = Modifier
) {
    val update = state.update
    val version = update.version ?: return
    ListSection(
        modifier = modifier,
        header = stringResource(R.string.mac_update_section),
        headerStyle = ListHeaderStyle.Title,
        footer = if (update.canUpdate) stringResource(R.string.mac_update_footer) else null
    ) {
        ValueRow(stringResource(R.string.mac_update_version), version)
        Status(state, actions)
        update.failure?.let { error ->
            ListRow {
                Text(
                    text = error,
                    style = LpmTheme.typography.footnote,
                    color = LpmTheme.colors.red
                )
            }
        }
    }
}

@Composable
private fun Status(state: MacUpdateUiState, actions: MacUpdateBarActions) {
    val update = state.update
    when (val phase = update.phase) {
        null -> {
            val newer = update.newer
            if (newer != null && update.canInstall) {
                MacUpdateButton(state.macLabel, actions.busyPlaces, actions.install) { start ->
                    UpdateRow(newer, enabled = state.canStartInstall, onClick = start)
                }
            } else if (update.canUpdate) {
                ValueRow(
                    stringResource(R.string.mac_update_status),
                    stringResource(R.string.mac_update_status_up_to_date)
                )
            }
        }

        is MacUpdatePhase.Updated -> ListRow(trailing = {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(DesignR.drawable.ic_check_circle_fill),
                contentDescription = null,
                tint = LpmTheme.colors.green,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.mac_update_updated_to, phase.release),
                style = LpmTheme.typography.body,
                color = LpmTheme.colors.green,
                maxLines = 1
            )
        }) {
            Text(stringResource(R.string.mac_update_status), style = LpmTheme.typography.body)
        }

        else -> ListRow {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityIndicator(size = ActivityIndicatorSize.Small)
                Text(
                    text = progressText(phase, update.cancelling),
                    style = LpmTheme.typography.body.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f)
                )
                if (phase.cancellable) {
                    LinkButton(
                        text = stringResource(R.string.mac_update_cancel),
                        onClick = actions.cancel,
                        enabled = !update.cancelling,
                        textStyle = LpmTheme.typography.body
                    )
                }
            }
        }
    }
}

/** "Update to 0.6.12" as a `Label` button: the icon in the icon column, both in the tint. */
@Composable
private fun UpdateRow(newer: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) LpmTheme.colors.tint else LpmTheme.colors.tertiaryLabel
    ListRow(
        onClick = onClick,
        enabled = enabled,
        leading = {
            Icon(
                painter = painterResource(DesignR.drawable.ic_arrow_circle_down),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        },
        leadingSlot = ListDefaults.IconSlot
    ) {
        Text(
            text = stringResource(R.string.mac_update_to, newer),
            style = LpmTheme.typography.body,
            color = color
        )
    }
}

@Composable
private fun progressText(phase: MacUpdatePhase, cancelling: Boolean): String {
    if (cancelling) return stringResource(R.string.mac_update_cancelling)
    return when (phase) {
        MacUpdatePhase.Checking -> stringResource(R.string.mac_update_checking)

        is MacUpdatePhase.Downloading -> if (phase.percent >= 0) {
            stringResource(R.string.mac_update_downloading_percent, phase.percent)
        } else {
            stringResource(R.string.mac_update_downloading)
        }

        MacUpdatePhase.Installing -> stringResource(R.string.mac_update_installing)

        MacUpdatePhase.Restarting -> stringResource(R.string.mac_update_restarting)

        is MacUpdatePhase.Updated -> ""
    }
}
