package cx.lpm.mobile.feature.terminal.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.web.TerminalTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TerminalSettingsViewModel @Inject constructor(private val prefs: AppPreferences) :
    ViewModel() {
    val uiState: StateFlow<TerminalLook> = prefs.terminalLooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), prefs.terminalLook)

    fun setFontSize(size: Int) = prefs.setTerminalFontSize(size)

    fun setTheme(theme: TerminalTheme) = prefs.setTerminalTheme(theme)
}
