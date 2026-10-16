package cx.lpm.mobile.feature.terminal.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.SheetScaffold
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.navigation.PresentationScope
import cx.lpm.mobile.feature.terminal.R

/**
 * The terminal's own font size and theme, opened from the terminal screen's menu.
 * It fits its content, keeping the terminal visible above it, so changes preview
 * live on the terminal behind the sheet.
 */
@Composable
fun TerminalSettingsSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sheet = rememberModalSheetState()
    ModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        fullHeight = false,
        state = sheet,
        floating = true
    ) {
        SheetScaffold(
            title = stringResource(R.string.terminal_settings_title),
            onDone = { sheet.dismiss(onDismiss) },
            doneLabel = stringResource(R.string.terminal_settings_done)
        ) {
            ListSection(
                modifier = Modifier.padding(top = ListDefaults.ContentTopPadding),
                footer = stringResource(R.string.terminal_settings_footer)
            ) {
                PresentationScope { TerminalSettingsControls() }
            }
        }
    }
}
