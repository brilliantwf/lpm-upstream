package cx.lpm.mobile.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether "Add a machine" is up. However the sheet goes — Cancel, a swipe, or a
 * pairing that landed — the session hears about it once (iOS `onDismiss:
 * cancelAddMac`), which reconnects the Mac the list showed or shows the summary.
 */
@HiltViewModel
class AddMacViewModel @Inject constructor(private val session: MacSession) : ViewModel() {
    val adding: StateFlow<Boolean> = session.state.map { it.addingMac }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value.addingMac)

    private var open = false

    init {
        viewModelScope.launch {
            adding.collect { now ->
                if (now) {
                    open = true
                } else if (open) {
                    open = false
                    session.cancelAddMac()
                }
            }
        }
    }

    fun close() {
        open = false
        session.cancelAddMac()
    }
}
