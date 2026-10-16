package cx.lpm.mobile.feature.usage.rows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.claude.ClaudeAccountsStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.data.usage.UsageStore
import cx.lpm.mobile.core.model.CLAUDE_MAIN_LOGIN
import cx.lpm.mobile.core.model.ClaudeAccountChoice
import cx.lpm.mobile.core.model.ClaudeInUse
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.inUse
import cx.lpm.mobile.feature.usage.ui.clockTicks
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Which list the usage rows sit under: the projects list, or a project's screen. */
data class UsageRowsKey(val session: SessionKey = SessionKey.Live, val project: String? = null)

enum class UsageAccountMark {
    /** New sessions use this account. */
    Current,

    /** The rest of the pool, which switching can move to. */
    Standby
}

data class UsageRowItem(val row: UsageRow, val mark: UsageAccountMark?)

data class UsageRowsUiState(
    val rows: List<UsageRowItem> = emptyList(),
    /** Which account new sessions use, for the main accounts or the project. */
    val inUse: ClaudeInUse? = null,
    /** A project's screen with an account beyond the main login can switch accounts. */
    val switches: Boolean = false,
    /** Unix millis on the phone's clock, for the windows the tools stamped. */
    val now: Double = 0.0,
    /** The same moment on the Mac's clock, for the `updatedAt` it stamped. */
    val macNow: Double = 0.0,
    /**
     * The list above has loaded, so the rows' spot won't move again; until then they
     * stay hidden rather than show mid-list and slide down.
     */
    val shown: Boolean = false
)

/**
 * The usage rows (iOS `UsageRowsSection`): on the projects list the tags speak for
 * the main accounts, since nothing is selected; on a project's screen they speak for
 * that project, and an expanded Claude account row switches the project to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = UsageRowsViewModel.Factory::class)
class UsageRowsViewModel @AssistedInject constructor(
    @Assisted val key: UsageRowsKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    val uiState: StateFlow<UsageRowsUiState> = sessions.session(key.session)
        .flatMapLatest { s -> s?.let(::rowsState) ?: flowOf(UsageRowsUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageRowsUiState())

    private fun rowsState(s: MacSession): Flow<UsageRowsUiState> = combine(
        s.store<UsageStore>().state,
        s.store<ClaudeAccountsStore>().state,
        s.store<ProjectsStore>().state,
        s.store<TerminalsStore>().state,
        clockTicks(clock)
    ) { usage, accountsState, projects, terminals, now ->
        val accounts = accountsState.accounts
        val project = key.project?.let(projects::project)
        val inUse = accounts?.inUse(project, project?.let(projects::parentProject))
        val macNow = usage.macMillis(now)
        val rows =
            usageRows(
                usage.snapshot,
                usage.today,
                accounts?.accounts.orEmpty(),
                now.toDouble(),
                macNow
            )
        // As on the desktop, a lone account needs no tag to tell it apart.
        val tagged = rows.count { it.account != null } >= 2
        UsageRowsUiState(
            rows = rows.map { UsageRowItem(it, if (tagged) mark(it, inUse) else null) },
            inUse = inUse,
            switches = project != null && accounts?.hasAdded == true,
            now = now.toDouble(),
            macNow = macNow,
            shown = key.project?.let { it in terminals.terminals } ?: projects.projectsLoaded
        )
    }

    private fun mark(row: UsageRow, inUse: ClaudeInUse?): UsageAccountMark? {
        val account = row.account ?: return null
        inUse ?: return null
        return when (account) {
            inUse.current -> UsageAccountMark.Current
            in inUse.standby -> UsageAccountMark.Standby
            else -> null
        }
    }

    /**
     * Reads the meters, today's spend and the Claude accounts the rows need while they
     * are on screen, again whenever the link comes back, and refreshes the spend every
     * three minutes as the desktop does. Runs until cancelled.
     */
    suspend fun feed() {
        val session = sessions[key.session] ?: return
        val usage = session.store<UsageStore>()
        session.state.map { it.isReady }.distinctUntilChanged().collectLatest { ready ->
            if (!ready) return@collectLatest
            usage.rowsDidAppear()
            try {
                while (true) {
                    delay(REST_REFRESH_MS)
                    usage.refreshExtras()
                }
            } finally {
                usage.rowsDidDisappear()
            }
        }
    }

    /** Pins the project's new Claude sessions to an account; the main login is pinned as "". */
    fun switchTo(account: String) {
        val project = key.project ?: return
        val accounts = sessions[key.session]?.store<ClaudeAccountsStore>() ?: return
        accounts.setClaudeAccount(
            project,
            ClaudeAccountChoice.Pin(if (account == CLAUDE_MAIN_LOGIN) "" else account)
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(key: UsageRowsKey): UsageRowsViewModel
    }

    private companion object {
        const val REST_REFRESH_MS = 180_000L
    }
}
