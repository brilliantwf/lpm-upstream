package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import cx.lpm.mobile.core.tailnet.TailnetStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TailnetSettingsUiState(
    val enabled: Boolean = false,
    val status: TailnetStatus = TailnetStatus(),
    val deviceName: String = "",
    val busy: Boolean = false,
    val failure: String? = null,
    /** A sign-in or admin page to open over the screen. */
    val page: String? = null
) {
    /** What the footer says went wrong: this screen's last attempt, else the node's own error. */
    val error: String? get() = failure ?: status.error
}

@HiltViewModel
class TailnetSettingsViewModel @Inject constructor(private val tailscale: BuiltInTailscale) :
    ViewModel() {
    private val local = MutableStateFlow(TailnetSettingsUiState(deviceName = tailscale.deviceName))

    val uiState: StateFlow<TailnetSettingsUiState> =
        combine(tailscale.enabled, tailscale.status, local) { enabled, status, l ->
            l.copy(enabled = enabled, status = status)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            local.value.copy(enabled = tailscale.enabled.value, status = tailscale.status.value)
        )

    init {
        viewModelScope.launch {
            tailscale.status.collect { status ->
                if (status.state == "running") local.update { it.copy(page = null) }
                if (status.state == "running" || status.state == "needsApproval") {
                    local.update { it.copy(failure = null) }
                }
            }
        }
    }

    fun setEnabled(on: Boolean) {
        tailscale.setEnabled(on)
        local.update { it.copy(failure = null) }
    }

    fun tryAgain() {
        tailscale.setEnabled(false)
        tailscale.setEnabled(true)
    }

    fun signIn() = attempt {
        tailscale.signIn()?.let { url -> local.update { it.copy(page = url) } }
    }

    fun signOut() = attempt { tailscale.signOut() }

    fun openAdmin() = local.update { it.copy(page = BuiltInTailscale.ADMIN_URL) }

    fun pageShown() = local.update { it.copy(page = null) }

    private fun attempt(work: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, failure = null) }
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(failure = e.message ?: e.toString()) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}
