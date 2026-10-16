package cx.lpm.mobile.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.SheetScaffold
import cx.lpm.mobile.core.navigation.SheetStack
import cx.lpm.mobile.core.navigation.rememberSheetNavigator

/** The rows of Settings that open a page of their own inside the sheet. */
enum class SettingsDestination { Root, Tailscale, ReadAloud, Notifications }

/**
 * The phone's settings, as iOS presents them: a sheet with "Done", whose rows push
 * their pages inside it.
 */
@Composable
fun SettingsSheet(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    slots: SettingsSlots = SettingsSlots()
) {
    val navigator = rememberSheetNavigator(SettingsDestination.Root)
    SheetStack(navigator, modifier.fillMaxSize()) { page ->
        when (page) {
            SettingsDestination.Root -> SettingsRoute(onDone, navigator::push, slots)
            SettingsDestination.Tailscale -> slots.tailscalePage(navigator::back)
            SettingsDestination.ReadAloud -> slots.readAloudPage(navigator::back)
            SettingsDestination.Notifications -> NotificationSettingsPage(navigator::back)
        }
    }
}

@Composable
private fun SettingsRoute(
    onDone: () -> Unit,
    onOpen: (SettingsDestination) -> Unit,
    slots: SettingsSlots,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SheetScaffold(
        title = stringResource(R.string.settings_title),
        onDone = onDone,
        doneLabel = stringResource(R.string.settings_done)
    ) {
        SettingsContent(
            state = state,
            onAppearanceChange = viewModel::setAppearance,
            onOpen = onOpen,
            terminalControls = slots.terminalControls
        )
    }
}
