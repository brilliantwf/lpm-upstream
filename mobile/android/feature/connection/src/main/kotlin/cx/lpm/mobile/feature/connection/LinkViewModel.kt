package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.mac.ThisDevice
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
import kotlinx.coroutines.flow.stateIn

/** The link of one session, for the status bar, issue cards and the terminal banner. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = LinkViewModel.Factory::class)
class LinkViewModel @AssistedInject constructor(
    @Assisted private val key: SessionKey,
    private val sessions: MacSessions,
    device: ThisDevice
) : ViewModel() {
    val uiState: StateFlow<LinkUiState?> = sessions.session(key)
        .flatMapLatest { it?.linkUi(device.noun) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun perform(action: IssueAction, issue: ConnectionIssue) {
        sessions[key]?.perform(action, issue)
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SessionKey): LinkViewModel
    }
}
