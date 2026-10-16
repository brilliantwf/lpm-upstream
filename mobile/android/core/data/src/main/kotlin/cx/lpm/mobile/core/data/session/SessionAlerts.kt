package cx.lpm.mobile.core.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The alerts a session raises from any of its screens: a request the machine
 * refused ("Couldn't complete that") and a one-off notice ("Heads up").
 */
class SessionAlerts {
    data class Alerts(val actionError: String? = null, val notice: String? = null)

    private val state = MutableStateFlow(Alerts())
    val alerts: StateFlow<Alerts> = state.asStateFlow()

    fun showError(message: String) = state.update { it.copy(actionError = message) }

    fun dismissError() = state.update { it.copy(actionError = null) }

    fun showNotice(message: String) = state.update { it.copy(notice = message) }

    fun dismissNotice() = state.update { it.copy(notice = null) }

    companion object {
        const val ERROR_TITLE = "Couldn't complete that"
        const val NOTICE_TITLE = "Heads up"
    }
}
