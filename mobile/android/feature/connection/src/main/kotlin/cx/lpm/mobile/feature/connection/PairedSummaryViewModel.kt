package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.AddressKind
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import cx.lpm.mobile.core.tailnet.TailnetStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Whether the Mac just paired will also work away from home, and what's missing if not. */
enum class AwayState { Ready, App, TurnOnHere, FinishHere, MacNotSetUp }

data class PairedSummaryUiState(
    val record: MacRecord? = null,
    val away: AwayState = AwayState.MacNotSetUp,
    /** How this pairing reached the Mac. */
    val overTailscale: Boolean = false
)

/**
 * The check right after pairing, while both devices are at hand: which Mac this is,
 * whether it will work away from home (and the fix if not), and notifications.
 */
@HiltViewModel(assistedFactory = PairedSummaryViewModel.Factory::class)
class PairedSummaryViewModel @AssistedInject constructor(
    @Assisted private val macId: String,
    session: MacSession,
    tailscale: BuiltInTailscale
) : ViewModel() {
    val uiState: StateFlow<PairedSummaryUiState> = combine(
        session.state,
        session.link.state,
        tailscale.enabled,
        tailscale.status,
        session.preferences.data
    ) { s, link, enabled, status, _ ->
        val record = s.macs.firstOrNull { it.localId == macId }
        val usesApp = session.preferences.bool(PreferenceKeys.USES_TAILSCALE_APP) ?: false
        PairedSummaryUiState(
            record = record,
            away = awayState(record, enabled, status, usesApp),
            overTailscale = link.host?.let { AddressKind.of(it) == AddressKind.Tailscale } ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairedSummaryUiState())

    @AssistedFactory
    interface Factory {
        fun create(macId: String): PairedSummaryViewModel
    }
}

internal fun awayState(
    record: MacRecord?,
    enabled: Boolean,
    status: TailnetStatus,
    usesApp: Boolean
): AwayState {
    if (record == null ||
        record.hosts.none { AddressKind.of(it).worksAway }
    ) {
        return AwayState.MacNotSetUp
    }
    if (!enabled) return if (usesApp) AwayState.App else AwayState.TurnOnHere
    return if (status.state == "running") AwayState.Ready else AwayState.FinishHere
}
