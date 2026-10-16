package cx.lpm.mobile.feature.terminal.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.StepperRow
import cx.lpm.mobile.core.web.TerminalPrefs
import cx.lpm.mobile.core.web.TerminalTheme
import cx.lpm.mobile.feature.terminal.R

/**
 * The terminal's font size and color scheme, read and written straight to the
 * phone's preferences — the rows of the app's Settings sheet and of the terminal's
 * own sheet, which stay in step. Place inside a list section.
 */
@Composable
fun TerminalSettingsControls(viewModel: TerminalSettingsViewModel = hiltViewModel()) {
    val look by viewModel.uiState.collectAsStateWithLifecycle()
    TerminalSettingsRows(
        look = look,
        onFontSizeChange = viewModel::setFontSize,
        onThemeChange = viewModel::setTheme
    )
}

@Composable
fun TerminalSettingsRows(
    look: TerminalLook,
    onFontSizeChange: (Int) -> Unit,
    onThemeChange: (TerminalTheme) -> Unit
) {
    StepperRow(
        title = stringResource(R.string.terminal_settings_font_size),
        value = look.fontSize,
        onValueChange = onFontSizeChange,
        range = TerminalPrefs.MIN_FONT_SIZE..TerminalPrefs.MAX_FONT_SIZE
    )
    PickerRow(
        title = stringResource(R.string.terminal_settings_color_scheme),
        options = TerminalTheme.entries,
        selected = look.theme,
        onSelect = onThemeChange,
        optionLabel = { it.label }
    )
    TerminalThemePreview(theme = look.theme, fontSize = look.fontSize)
}
