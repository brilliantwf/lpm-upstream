package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.LinkState
import cx.lpm.mobile.core.data.link.PairedSummary
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which of the connection sheets are up. */
data class ConnectionSheetsUiState(
    val details: Boolean = false,
    val identityCheck: Boolean = false,
    val machines: Boolean = false,
    val tailscaleSettings: Boolean = false,
    val tailscalePage: String? = null,
    val summary: PairedSummary? = null
)

/** The sheets the connection UI opens from anywhere in the app, raised by the link's flags. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ConnectionSheetsViewModel.Factory::class)
class ConnectionSheetsViewModel @AssistedInject constructor(
    @Assisted private val key: SessionKey,
    private val sessions: MacSessions
) : ViewModel() {
    val uiState: StateFlow<ConnectionSheetsUiState> = sessions.session(key)
        .flatMapLatest { session ->
            session?.link?.state?.map { it.sheets() } ?: flowOf(ConnectionSheetsUiState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionSheetsUiState())

    /** A sheet went away: clear its flag, then run what was waiting for it to go. */
    fun closed(sheet: (LinkState) -> LinkState) {
        val link = sessions[key]?.link ?: return
        link.update(sheet)
        link.runAfterSheet()
    }

    fun closeTailscaleSettings() = update { it.copy(tailscaleSettingsOpen = false) }

    fun closeSummary() = update { it.copy(summary = null) }

    fun tailscalePageShown() = update { it.copy(tailscalePage = null) }

    private fun update(change: (LinkState) -> LinkState) {
        sessions[key]?.link?.update(change)
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SessionKey): ConnectionSheetsViewModel
    }
}

private fun LinkState.sheets() = ConnectionSheetsUiState(
    details = sheetOpen,
    identityCheck = identityCheckOpen,
    machines = manageOpen,
    tailscaleSettings = tailscaleSettingsOpen,
    tailscalePage = tailscalePage,
    summary = summary
)
