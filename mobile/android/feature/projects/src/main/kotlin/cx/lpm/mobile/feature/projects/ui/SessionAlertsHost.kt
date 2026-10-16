package cx.lpm.mobile.feature.projects.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.data.session.SessionAlerts
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.feature.projects.R

/**
 * The alerts a session raises from any of its screens: a request the machine
 * refused ("Couldn't complete that") and a one-off notice ("Heads up").
 */
@Composable
fun SessionAlertsHost(
    alerts: SessionAlerts.Alerts,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val ok = AlertButton(stringResource(R.string.ok), AlertRole.Cancel)
    when {
        alerts.actionError != null -> LpmAlert(
            title = stringResource(R.string.session_error_title),
            message = alerts.actionError,
            buttons = listOf(ok),
            onDismissRequest = onDismissError
        )

        alerts.notice != null -> LpmAlert(
            title = stringResource(R.string.session_notice_title),
            message = alerts.notice,
            buttons = listOf(ok),
            onDismissRequest = onDismissNotice
        )
    }
}
