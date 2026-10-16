package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.LinkStatusLine
import cx.lpm.mobile.core.data.link.MacReach
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.session.MacSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ManageMacsUiState(
    val macs: List<MacRecord> = emptyList(),
    val activeMacId: String? = null,
    val statusLine: LinkStatusLine? = null,
    val reach: Map<String, MacReach> = emptyMap()
) {
    fun record(id: String): MacRecord? = macs.firstOrNull { it.localId == id }

    /** The Mac a machine that isn't on this phone opens through. */
    fun via(record: MacRecord): MacRecord? = record.via?.let { v ->
        macs.firstOrNull {
            it.localId ==
                v.macId
        }
    }
}

/**
 * Every saved machine in one place: whether each answers right now, and its name,
 * addresses and removal — for the ones that aren't live as well as the one that is.
 */
@HiltViewModel
class ManageMacsViewModel @Inject constructor(private val session: MacSession) : ViewModel() {
    val uiState: StateFlow<ManageMacsUiState> =
        combine(session.state, session.link.state, session.link.views) { s, link, view ->
            ManageMacsUiState(
                macs = s.macs,
                activeMacId = s.activeMacId,
                statusLine = view.statusLine,
                reach = link.reach
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ManageMacsUiState(
                macs = session.state.value.macs,
                activeMacId = session.state.value.activeMacId
            )
        )

    init {
        session.link.refreshReach(force = true)
    }

    /** "Add a machine…" once this sheet has gone. */
    fun addMachineAfterClose() = session.link.afterSheetCloses { session.beginAddMac() }

    fun rename(id: String, name: String) = session.renameMac(id, name)

    fun remove(id: String) = session.removeMac(id)

    fun switchTo(record: MacRecord) {
        session.switchTo(record)
        session.link.update { it.copy(manageOpen = false) }
    }
}
