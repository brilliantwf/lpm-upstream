package cx.lpm.mobile.feature.pairing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.link.AddressCheck
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.session.MacSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class EnterCodeUiState(
    /** This pairing is under way. */
    val pairing: Boolean = false,
    val issue: ConnectionIssue? = null,
    val checks: Map<String, AddressCheck> = emptyMap(),
    val showDetails: Boolean = false
)

/** Typed pairing: the code first, then the address, the port out of the way. */
@HiltViewModel(assistedFactory = EnterCodeViewModel.Factory::class)
class EnterCodeViewModel @AssistedInject constructor(
    @Assisted host: String,
    @Assisted port: Int,
    private val session: MacSession
) : ViewModel() {
    /** The form, as Compose state so its text fields edit it directly. */
    var form by mutableStateOf(EnterCodeForm(host = host, port = port.toString()))

    private val details = MutableStateFlow(false)

    val uiState: StateFlow<EnterCodeUiState> =
        combine(session.state, session.link.state, session.link.views, details) {
                s,
                link,
                view,
                showDetails
            ->
            EnterCodeUiState(
                pairing = link.pairing && s.connection == LpmClient.State.Connecting,
                issue = view.issue?.takeIf { link.pairing && !it.isConnecting },
                checks = link.checks,
                showDetails = showDetails
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnterCodeUiState())

    /** Pair with what's typed; false when the form isn't complete. */
    fun pair(): Boolean {
        val f = form
        if (!f.canPair) return false
        details.value = false
        val (host, port) = f.target
        session.pair(listOf(host), port, f.cleanCode)
        return true
    }

    /** The screen's answers to an issue's buttons; false leaves it to the card. */
    fun handle(action: IssueAction): Boolean {
        when (action) {
            IssueAction.Retry -> pair()
            IssueAction.EnterNewCode -> form = form.copy(code = "")
            IssueAction.Details -> details.update { !it }
            else -> return false
        }
        return true
    }

    @AssistedFactory
    interface Factory {
        fun create(host: String, port: Int): EnterCodeViewModel
    }
}
