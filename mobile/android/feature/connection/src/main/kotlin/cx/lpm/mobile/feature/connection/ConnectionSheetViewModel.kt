package cx.lpm.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.AddressKind
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.session.RepairReason
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import cx.lpm.mobile.core.tailnet.TailnetStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How Built-in Tailscale reads in one word on a settings row. */
enum class TailscaleSummary { Off, Connected, SignIn, Waiting, Problem, Connecting }

data class ConnectionSheetUiState(
    val record: MacRecord? = null,
    /** The Mac the active machine is reached at: itself, or the Mac it opens through. */
    val reached: MacRecord? = null,
    /** The Mac a machine that isn't on this phone opens through. */
    val via: MacRecord? = null,
    val link: LinkUiState? = null,
    val isVisit: Boolean = false,
    val tailscale: TailscaleSummary = TailscaleSummary.Off,
    val nextAfterRemoval: MacRecord? = null,
    val lastError: String? = null
) {
    /** Its addresses, the one that works from anywhere first. */
    val hosts: List<String>
        get() = reached?.hosts.orEmpty().sortedBy { AddressKind.of(it).sortRank }

    val hasAwayAddress: Boolean get() = hosts.any { AddressKind.of(it).worksAway }
}

/** Behind the status line: how the phone reaches the active Mac, and the Mac's own actions. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ConnectionSheetViewModel.Factory::class)
class ConnectionSheetViewModel @AssistedInject constructor(
    @Assisted private val key: SessionKey,
    private val sessions: MacSessions,
    tailscale: BuiltInTailscale,
    device: ThisDevice
) : ViewModel() {
    val uiState: StateFlow<ConnectionSheetUiState> = sessions.session(key)
        .flatMapLatest { session ->
            session?.sheetState(device.noun, tailscale.enabled, tailscale.status)
                ?: flowOf(ConnectionSheetUiState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionSheetUiState())

    private val session: MacSession? get() = sessions[key]

    /** Check every address on its own, updating the issue when they all fail. */
    fun testAll() {
        val session = session ?: return
        val reached = session.state.value.linkRecord ?: return
        viewModelScope.launch {
            session.link.test(reached.hosts, reached.port, updatingIssue = true, mac = reached)
        }
    }

    fun rename(id: String, name: String) {
        session?.renameMac(id, name)
    }

    fun remove(id: String) {
        session?.removeMac(id)
    }

    /** Pair the active Mac again once this sheet has gone. */
    fun pairAgainAfterClose() {
        val session = session ?: return
        session.link.afterSheetCloses { session.repairActiveMac(RepairReason.Chosen) }
    }

    fun perform(action: IssueAction, issue: ConnectionIssue) {
        session?.perform(action, issue)
    }

    /** An action that puts up a sheet of its own, which can only appear once this one has gone. */
    fun performAfterClose(action: IssueAction, issue: ConnectionIssue) {
        val session = session ?: return
        session.link.afterSheetCloses { session.perform(action, issue) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SessionKey): ConnectionSheetViewModel
    }

    companion object {
        fun opensSheet(action: IssueAction): Boolean = action in setOf(
            IssueAction.PairAgain,
            IssueAction.CompareCode,
            IssueAction.TurnOnTailscale,
            IssueAction.SignInTailscale,
            IssueAction.TailscaleSettings
        )
    }
}

private fun MacSession.sheetState(
    device: String,
    enabled: Flow<Boolean>,
    status: Flow<TailnetStatus>
): Flow<ConnectionSheetUiState> = combine(linkUi(device), clientFlow, enabled, status) {
        link,
        client,
        on,
        tailnet
    ->
    val s = state.value
    val record = s.activeRecord
    ConnectionSheetUiState(
        record = record,
        reached = record?.let(s::linkRecord),
        via = record?.let(s::viaMac),
        link = link,
        isVisit = isVisit,
        tailscale = tailscaleSummary(on, tailnet.state),
        nextAfterRemoval = s.nextMacAfterRemoval,
        lastError = client?.lastTransportErrorChain
    )
}

internal fun tailscaleSummary(enabled: Boolean, state: String): TailscaleSummary = when {
    !enabled -> TailscaleSummary.Off
    state == "running" -> TailscaleSummary.Connected
    state == "needsLogin" -> TailscaleSummary.SignIn
    state == "needsApproval" -> TailscaleSummary.Waiting
    state == "error" || state == "stopped" -> TailscaleSummary.Problem
    else -> TailscaleSummary.Connecting
}
