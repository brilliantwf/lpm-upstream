package cx.lpm.mobile.feature.automations.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.feature.automations.R

/** "Couldn't update automation": a request the Mac refused. */
@Composable
fun UpdateFailedAlert(message: String, onDismiss: () -> Unit) {
    LpmAlert(
        title = stringResource(R.string.update_failed),
        message = message,
        buttons = listOf(AlertButton(stringResource(R.string.ok), AlertRole.Cancel)),
        onDismissRequest = onDismiss
    )
}
