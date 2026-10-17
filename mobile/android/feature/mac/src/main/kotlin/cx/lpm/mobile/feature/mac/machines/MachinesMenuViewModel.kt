package cx.lpm.mobile.feature.mac.machines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.machines.MachineImportRow
import cx.lpm.mobile.core.data.machines.MachineImportStatus
import cx.lpm.mobile.core.data.machines.MachineImporter
import cx.lpm.mobile.core.data.machines.MachineSection
import cx.lpm.mobile.core.data.session.MacSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MachinesMenuUiState(
    /** The live Mac's name, for "Connected to …". */
    val macName: String? = null,
    /** The live Mac's machines that aren't on this phone, for the Mac switcher. */
    val pending: List<MachineImportRow> = emptyList(),
    /**
     * The machines this phone opens through the live Mac that it could pair with
     * directly: one removed from it, one whose automatic pairing failed, or one that
     * removed this phone, which only pairing on the machine itself can bring back.
     */
    val pairDirectly: List<MachineSection> = emptyList()
)

/** The live Mac's machines in the title's switcher and the projects "…" menu. */
@HiltViewModel
class MachinesMenuViewModel @Inject constructor(private val session: MacSession) : ViewModel() {
    private val importer: MachineImporter = session.store()

    val uiState: StateFlow<MachinesMenuUiState> = combine(
        session.state,
        importer.state,
        importer.sections
    ) { s, imports, sections ->
        MachinesMenuUiState(
            macName = s.activeRecord?.displayName,
            pending = imports.rows(s.macs),
            pairDirectly = sections.filter {
                it.pending == MachineImportStatus.Removed ||
                    it.pending == MachineImportStatus.Revoked ||
                    it.pending is MachineImportStatus.Failed
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachinesMenuUiState())

    /** Add a machine here; one that removed this phone is paired on the machine itself. */
    fun add(slug: String) {
        val revoked = importer.state.value.rows(session.state.value.macs).any {
            it.id == slug && it.status == MachineImportStatus.Revoked
        }
        if (revoked) session.beginAddMac() else importer.add(slug)
    }
}
