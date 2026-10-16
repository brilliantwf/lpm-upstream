package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.ButtonRow
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ListSectionFooter
import cx.lpm.mobile.core.designsystem.component.LpmConfirmationDialog
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.component.ValueRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * Built-in Tailscale: puts this phone on the user's tailnet without the Tailscale
 * app, so it reaches a Mac from cellular or any other network. [onBack] when pushed,
 * [onDone] when it's a sheet of its own.
 */
@Composable
fun TailnetSettingsPage(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    viewModel: TailnetSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.page) {
        state.page?.let {
            context.openInApp(it)
            viewModel.pageShown()
        }
    }
    SheetPage(
        title = stringResource(R.string.tailnet_title),
        modifier = modifier,
        onBack = onBack,
        trailing = onDone?.let { done -> { DoneButton(done) } }
    ) {
        TailnetSettingsContent(
            state = state,
            onEnabledChange = viewModel::setEnabled,
            onSignIn = viewModel::signIn,
            onSignOut = viewModel::signOut,
            onOpenAdmin = viewModel::openAdmin,
            onTryAgain = viewModel::tryAgain
        )
    }
}

@Composable
fun TailnetSettingsContent(
    state: TailnetSettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAdmin: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    SheetForm(modifier) {
        ListSection(footer = stringResource(R.string.tailnet_footer)) {
            ToggleRow(
                title = stringResource(R.string.tailnet_title),
                checked = state.enabled,
                onCheckedChange = onEnabledChange
            )
        }
        if (state.enabled) {
            StatusSection(state, onSignIn, onOpenAdmin, onTryAgain) { confirmSignOut = true }
        }
        ListSection(
            header = stringResource(R.string.tailnet_how_it_works),
            headerStyle = ListHeaderStyle.Title,
            footer = stringResource(R.string.tailnet_how_footer, state.deviceName)
        ) {
            Step(1, stringResource(R.string.tailnet_step_1))
            Step(2, stringResource(R.string.tailnet_step_2))
            Step(3, stringResource(R.string.tailnet_step_3))
        }
    }
    if (confirmSignOut) {
        LpmConfirmationDialog(
            title = stringResource(R.string.tailnet_sign_out_title),
            message = stringResource(R.string.tailnet_sign_out_message),
            buttons = listOf(
                AlertButton(
                    stringResource(R.string.tailnet_sign_out),
                    AlertRole.Destructive,
                    onClick = onSignOut
                ),
                AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
            ),
            onDismissRequest = { confirmSignOut = false }
        )
    }
}

@Composable
private fun StatusSection(
    state: TailnetSettingsUiState,
    onSignIn: () -> Unit,
    onOpenAdmin: () -> Unit,
    onTryAgain: () -> Unit,
    onSignOut: () -> Unit
) {
    val colors = LpmTheme.colors
    val status = state.status
    val error = state.error
    ListSection(
        footer = error ?: stringResource(R.string.tailnet_approval_footer, state.deviceName)
            .takeIf { status.state == "needsApproval" },
        footerColor = if (error != null) colors.red else colors.secondaryLabel
    ) {
        ListRow(trailing = {
            if (state.busy || status.state == "starting") ActivityIndicator()
        }) {
            StatusDot(color = statusColor(status.state), size = 8.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(statusLabel(status.state)), style = LpmTheme.typography.body)
        }
        when (status.state) {
            "needsLogin" -> ActionRow(
                text = stringResource(R.string.tailnet_sign_in),
                icon = DesignR.drawable.ic_person_check,
                onClick = onSignIn,
                enabled = !state.busy
            )

            "needsApproval" -> {
                status.account?.let { ValueRow(stringResource(R.string.tailnet_account), it) }
                ActionRow(
                    text = stringResource(R.string.tailnet_open_admin),
                    icon = DesignR.drawable.ic_verified_user,
                    onClick = onOpenAdmin
                )
                ButtonRow(
                    text = stringResource(R.string.tailnet_sign_out),
                    onClick = onSignOut,
                    enabled = !state.busy,
                    destructive = true
                )
            }

            "running" -> {
                status.account?.let { ValueRow(stringResource(R.string.tailnet_account), it) }
                status.ip?.let { AddressValueRow(it) }
                ButtonRow(
                    text = stringResource(R.string.tailnet_sign_out),
                    onClick = onSignOut,
                    enabled = !state.busy,
                    destructive = true
                )
            }

            "stopped" -> ActionRow(
                text = stringResource(R.string.tailnet_open_admin),
                icon = DesignR.drawable.ic_verified_user,
                onClick = onOpenAdmin
            )

            "error" -> ButtonRow(
                text = stringResource(R.string.try_again_title),
                onClick = onTryAgain
            )
        }
    }
}

@Composable
private fun AddressValueRow(ip: String) {
    ListRow(trailing = {
        Text(
            ip,
            style = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
            color = LpmTheme.colors.secondaryLabel
        )
    }) {
        Text(stringResource(R.string.tailnet_address), style = LpmTheme.typography.body)
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val colors = LpmTheme.colors
    ListRow {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(18.dp)
                    .background(colors.tertiarySystemFill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$number",
                    style = LpmTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryLabel
                )
            }
            Text(text, style = LpmTheme.typography.subheadline)
        }
    }
}

private fun statusLabel(state: String): Int = when (state) {
    "running" -> R.string.tailnet_status_running
    "needsLogin" -> R.string.tailnet_status_needs_login
    "needsApproval" -> R.string.tailnet_status_needs_approval
    "stopped" -> R.string.tailnet_status_stopped
    "error" -> R.string.tailnet_status_error
    else -> R.string.tailnet_status_connecting
}

@Composable
private fun statusColor(state: String): Color = when (state) {
    "running" -> LpmTheme.colors.green
    "error", "stopped" -> LpmTheme.colors.red
    else -> LpmTheme.colors.orange
}
