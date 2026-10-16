package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.session.RepairReason
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class IdentityCheckUiState(val macLabel: String = "your Mac", val code: String? = null)

/** The active Mac presents a different identity: compare a short code before trusting it. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = IdentityCheckViewModel.Factory::class)
class IdentityCheckViewModel @AssistedInject constructor(
    @Assisted private val key: SessionKey,
    private val sessions: MacSessions
) : ViewModel() {
    val uiState: StateFlow<IdentityCheckUiState> = sessions.session(key)
        .flatMapLatest { session ->
            session ?: return@flatMapLatest flowOf(IdentityCheckUiState())
            combine(session.link.views, session.link.state) { view, link ->
                IdentityCheckUiState(macLabel = view.macLabel, code = link.identityCode)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdentityCheckUiState())

    fun trust() {
        sessions[key]?.trustNewIdentity()
    }

    fun reject() {
        sessions[key]?.rejectNewIdentity()
    }

    /** Pair again, once this sheet has gone. */
    fun pairAgainAfterClose() {
        val session = sessions[key] ?: return
        session.link.afterSheetCloses { session.repairActiveMac(RepairReason.Identity) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SessionKey): IdentityCheckViewModel
    }
}
