package cx.lpm.mobile.core.data.claude

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.requestClaudeAccounts
import cx.lpm.mobile.core.data.client.requestLimits
import cx.lpm.mobile.core.data.client.requestProjects
import cx.lpm.mobile.core.data.client.setClaudeAccount
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.data.usage.UsageStore
import cx.lpm.mobile.core.model.ClaudeAccountChoice
import cx.lpm.mobile.core.model.ClaudeAccounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClaudeAccountsState(
    /** Null until the Mac first answers `claudeAccounts`. */
    val accounts: ClaudeAccounts? = null
)

/**
 * The Mac's Claude logins and where switching stands (iOS `AppModel+ClaudeAccount`):
 * what the Claude Account menu lists and what names and tags the usage rows.
 */
class ClaudeAccountsStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(ClaudeAccountsState())
    val state: StateFlow<ClaudeAccountsState> = mutable.asStateFlow()

    override fun wire(client: LpmClient) {
        client.onClaudeAccounts = { accounts ->
            if (accounts != null) mutable.value = ClaudeAccountsState(accounts)
        }
        // A refused choice leaves the optimistic menu showing something the Mac never
        // wrote, so re-ask for the list. Either way the pools may have moved, so the
        // accounts are read again too.
        client.onSetClaudeAccount = { _, error ->
            if (error != null) {
                session.alerts.showError(error)
                session.client?.requestProjects()
            }
            session.client?.requestClaudeAccounts()
        }
    }

    override fun reset() {
        mutable.value = ClaudeAccountsState()
    }

    /**
     * Read the accounts the Claude Account menu lists, and the usage readings its rows
     * show when none have arrived yet.
     */
    fun load() {
        session.client?.requestClaudeAccounts()
        if (session.store<UsageStore>().state.value.snapshot == null) {
            session.client?.requestLimits()
        }
    }

    /**
     * Set which login new Claude sessions in a project use, first confirming `confirm`
     * as the person's own. The menu shows it at once; the projects-changed push
     * replaces it with what the Mac wrote.
     */
    fun setClaudeAccount(
        project: String,
        choice: ClaudeAccountChoice,
        confirm: List<String> = emptyList()
    ) {
        val projects = session.store<ProjectsStore>()
        projects.updateProject(project) { it.withClaude(choice.own(isCopy = it.isDuplicate)) }
        session.client?.setClaudeAccount(project, choice, confirm)
    }
}
