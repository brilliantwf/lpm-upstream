package cx.lpm.mobile.feature.mac.update

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.BusyAgentPlace
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.mac.data.MacUpdatePhase
import cx.lpm.mobile.feature.mac.data.MacUpdateState
import cx.lpm.mobile.feature.mac.data.MacUpdateStore
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

data class MacUpdateUiState(
    val update: MacUpdateState = MacUpdateState(),
    /** The link is up: the Mac can take a check or an install. */
    val ready: Boolean = false,
    /** How a sentence names the Mac ("Studio Mac", "your Mac"). */
    val macLabel: String = ""
) {
    /** The bar is off while the Mac is away, except for the restart that takes it away. */
    val showsBar: Boolean
        get() {
            val phase = update.phase
            val leaving = phase is MacUpdatePhase.Restarting || phase is MacUpdatePhase.Updated
            return update.showsBar && (leaving || ready)
        }

    /** "Update" starts an install only while the Mac is there to take it. */
    val canStartInstall: Boolean get() = update.canInstall && ready

    val canCheckNow: Boolean get() = update.canCheck && ready
}

/** The update bar, button, menu row and section of one session's Mac. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MacUpdateViewModel.Factory::class)
class MacUpdateViewModel @AssistedInject constructor(
    @Assisted val session: SessionKey,
    private val sessions: MacSessions
) : ViewModel() {
    private val store: MacUpdateStore? get() = sessions[session]?.store()

    val uiState: StateFlow<MacUpdateUiState> = sessions.session(session)
        .flatMapLatest { s -> s?.let(::stateOf) ?: flowOf(MacUpdateUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MacUpdateUiState())

    private fun stateOf(s: MacSession) = combine(
        s.store<MacUpdateStore>().state,
        s.state,
        s.link.views
    ) { update, session, view ->
        MacUpdateUiState(update = update, ready = session.isReady, macLabel = view.macLabel)
    }

    /** Where agents are mid-task right now, read when Update is tapped. */
    fun busyPlaces(): List<BusyAgentPlace> = store?.busyPlaces.orEmpty()

    fun install() {
        store?.install()
    }

    fun checkNow() {
        store?.checkNow()
    }

    fun cancel() {
        store?.cancel()
    }

    fun dismiss() {
        store?.dismiss()
    }

    @AssistedFactory
    interface Factory {
        fun create(session: SessionKey): MacUpdateViewModel
    }
}

/** The update ViewModel of `session`'s Mac, one per session wherever it is asked for. */
@Composable
internal fun macUpdateViewModel(session: SessionKey): MacUpdateViewModel =
    hiltViewModel<MacUpdateViewModel, MacUpdateViewModel.Factory>(key = "mac-update-$session") {
        it.create(session)
    }
