package cx.lpm.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.model.AppearanceMode
import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Built-in Tailscale row says beside its name. */
enum class TailscaleSummary { Off, Connected, SignIn, Waiting, Problem, Connecting }

fun tailscaleSummary(enabled: Boolean, state: String): TailscaleSummary = when {
    !enabled -> TailscaleSummary.Off
    state == "running" -> TailscaleSummary.Connected
    state == "needsLogin" -> TailscaleSummary.SignIn
    state == "needsApproval" -> TailscaleSummary.Waiting
    state == "error" || state == "stopped" -> TailscaleSummary.Problem
    else -> TailscaleSummary.Connecting
}

data class SettingsUiState(
    val appearance: AppearanceMode = AppearanceMode.System,
    val tailscale: TailscaleSummary = TailscaleSummary.Off
)

/** The phone's own settings (iOS `SettingsSheet`): appearance and the Tailscale summary. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    tailscale: BuiltInTailscale
) : ViewModel() {
    private val appearance = prefs.data
        .map { AppearanceMode.of(prefs.string(AppearanceMode.STORAGE_KEY)) }
        .distinctUntilChanged()

    val uiState: StateFlow<SettingsUiState> = combine(
        appearance,
        tailscale.enabled,
        tailscale.status
    ) { mode, enabled, status ->
        SettingsUiState(mode, tailscaleSummary(enabled, status.state))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            appearance = AppearanceMode.of(prefs.string(AppearanceMode.STORAGE_KEY)),
            tailscale = tailscaleSummary(tailscale.enabled.value, tailscale.status.value.state)
        )
    )

    fun setAppearance(mode: AppearanceMode) =
        prefs.setString(AppearanceMode.STORAGE_KEY, mode.rawValue)
}
