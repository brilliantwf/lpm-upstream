package cx.lpm.mobile.feature.claudeaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.claude.ClaudeAccountsStore
import cx.lpm.mobile.core.data.projects.ProjectsState
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.usage.UsageState
import cx.lpm.mobile.core.data.usage.UsageStore
import cx.lpm.mobile.core.model.ClaudeAccountChoice
import cx.lpm.mobile.core.model.ClaudeAccountInfo
import cx.lpm.mobile.core.model.ClaudeAccounts
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.model.claudeUsageLine
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * A list the Claude Account menu wants to save, held until the person confirms that
 * the accounts joining it are theirs alone.
 */
data class ClaudeConsentRequest(
    val project: String,
    val ids: List<String>,
    val accounts: List<ClaudeAccountInfo>
)

data class ClaudeAccountMenuUiState(
    /** Null until the Mac first answers `claudeAccounts`. */
    val accounts: ClaudeAccounts? = null,
    val project: Project? = null,
    /** What a copy's parent chooses, which "Same as parent" names. */
    val parentChoice: ClaudeOwnChoice = ClaudeOwnChoice(),
    /** Each account's "5h 72% · 7d 41%", for those with a current reading. */
    val usage: Map<String, String> = emptyMap(),
    val consent: ClaudeConsentRequest? = null
) {
    /** The menu appears once an account beyond the main login is added, as on the desktop. */
    val shown: Boolean get() = project != null && accounts?.hasAdded == true
}

/**
 * The project menu's "Claude Account" submenu (iOS `ClaudeAccountMenu` and its consent
 * prompt): which login new Claude sessions in this project use. Signing in, adding
 * accounts and turning switching on stay on the Mac.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ClaudeAccountMenuViewModel.Factory::class)
class ClaudeAccountMenuViewModel @AssistedInject constructor(
    @Assisted private val project: String,
    @Assisted private val sessionKey: SessionKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    private val consent = MutableStateFlow<ClaudeConsentRequest?>(null)

    // The menu is built when it opens, so it starts from what the stores hold now
    // rather than an empty frame.
    val uiState: StateFlow<ClaudeAccountMenuUiState> = sessions.session(sessionKey)
        .flatMapLatest { s -> s?.let(::menuState) ?: flowOf(ClaudeAccountMenuUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    private fun menuState(s: MacSession): Flow<ClaudeAccountMenuUiState> = combine(
        s.store<ClaudeAccountsStore>().state,
        s.store<ProjectsStore>().state,
        s.store<UsageStore>().state,
        consent
    ) { accounts, projects, usage, request ->
        uiState(accounts.accounts, projects, usage, request)
    }

    private fun current(): ClaudeAccountMenuUiState {
        val s = sessions[sessionKey] ?: return ClaudeAccountMenuUiState()
        return uiState(
            s.store<ClaudeAccountsStore>().state.value.accounts,
            s.store<ProjectsStore>().state.value,
            s.store<UsageStore>().state.value,
            consent.value
        )
    }

    private fun uiState(
        accounts: ClaudeAccounts?,
        projects: ProjectsState,
        usage: UsageState,
        request: ClaudeConsentRequest?
    ): ClaudeAccountMenuUiState {
        val live = projects.project(project)
        val now = clock.millis().toDouble()
        return ClaudeAccountMenuUiState(
            accounts = accounts,
            project = live,
            parentChoice = live?.let(projects::parentProject)?.claude ?: ClaudeOwnChoice(),
            usage = accounts?.accounts.orEmpty().mapNotNull { a ->
                claudeUsageLine(usage.snapshot, a.id, now)?.let { a.id to it }
            }.toMap(),
            consent = request
        )
    }

    private val store: ClaudeAccountsStore? get() = sessions[sessionKey]?.store()

    /** Sets [choice] unless it is what the project already has; true when it was sent. */
    fun pick(choice: ClaudeAccountChoice): Boolean {
        val live = uiState.value.project ?: return false
        if (choice.own(isCopy = live.isDuplicate) == live.claude) return false
        store?.setClaudeAccount(project, choice)
        return true
    }

    /**
     * Saves the project's list of accounts to take turns on, once any signed-in account
     * joining it is confirmed as the person's own, as on the desktop; true when it was
     * sent rather than held for that.
     */
    fun setList(ids: List<String>): Boolean {
        val accounts = uiState.value.accounts ?: return false
        val unconfirmed = ids.mapNotNull(accounts::account).filter { it.signedIn && !it.confirmed }
        if (unconfirmed.isEmpty()) return pick(ClaudeAccountChoice.AccountList(ids))
        consent.value = ClaudeConsentRequest(project, ids, unconfirmed)
        return false
    }

    fun confirmConsent() {
        val request = consent.value ?: return
        consent.value = null
        store?.setClaudeAccount(
            request.project,
            ClaudeAccountChoice.AccountList(request.ids),
            confirm = request.accounts.map { it.id }
        )
    }

    fun dismissConsent() {
        consent.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(project: String, sessionKey: SessionKey): ClaudeAccountMenuViewModel
    }
}
