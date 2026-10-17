package cx.lpm.mobile.core.data.git

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.gitCheckout
import cx.lpm.mobile.core.data.client.gitCreateBranch
import cx.lpm.mobile.core.data.client.requestGitBranches
import cx.lpm.mobile.core.data.client.requestProjects
import cx.lpm.mobile.core.data.client.requestSidebar
import cx.lpm.mobile.core.data.session.GenerationTimeout
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.model.GitBranch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The switch-branch sheet's half of [GitStore]: the branch list, checking one out
 * and creating one. Their failures stay in [GitState.branchError] so they show inside
 * the sheet; a switch bumps [GitState.checkoutTick] for the sheet to close, and
 * [onSwitched] refreshes the snapshot.
 */
internal class GitBranches(
    private val session: MacSession,
    private val state: MutableStateFlow<GitState>,
    private val timeout: GenerationTimeout<String>,
    private val onSwitched: (project: String) -> Unit
) {
    private val client: LpmClient? get() = session.client

    fun wire(c: LpmClient) {
        c.onGitBranches = ::apply
        c.onGitCheckout = { project, error ->
            timeout.cancel(gitTimeoutKey(CHECKOUT, project))
            state.update { it.copy(checkingOut = it.checkingOut - project) }
            finishSwitch(project, error)
        }
        c.onGitCreateBranch = { project, error ->
            timeout.cancel(gitTimeoutKey(GitOp.CreateBranch.name, project))
            state.update { GitOp.CreateBranch.mark(it, project, false) }
            finishSwitch(project, error)
        }
    }

    fun load(project: String) {
        val op = GitOp.Branches
        if (project in op.running(state.value)) return
        state.update { op.mark(it, project, true).copy(branchError = it.branchError - project) }
        client?.requestGitBranches(project)
        timeout.arm(gitTimeoutKey(op.name, project), op.timeoutMs) {
            if (project !in op.running(state.value)) return@arm
            state.update { s ->
                val error = if (s.branches[project] == null) {
                    s.branchError + (project to "Couldn't load branches. Try again.")
                } else {
                    s.branchError
                }
                op.mark(s, project, false).copy(branchError = error)
            }
        }
    }

    fun checkout(project: String, branch: String, remote: String) {
        if (state.value.checkingOut[project] != null) return
        state.update {
            it.copy(
                checkingOut = it.checkingOut + (project to branch),
                branchError = it.branchError - project
            )
        }
        client?.gitCheckout(project, branch, remote)
        timeout.arm(gitTimeoutKey(CHECKOUT, project), CHECKOUT_TIMEOUT_MS) {
            if (state.value.checkingOut[project] == null) return@arm
            state.update {
                it.copy(
                    checkingOut = it.checkingOut - project,
                    branchError =
                        it.branchError + (project to "Switching branch timed out. Try again.")
                )
            }
        }
    }

    fun create(project: String, name: String) {
        val op = GitOp.CreateBranch
        val trimmed = name.trim()
        if (trimmed.isEmpty() || project in op.running(state.value)) return
        state.update { op.mark(it, project, true).copy(branchError = it.branchError - project) }
        client?.gitCreateBranch(project, trimmed)
        timeout.arm(gitTimeoutKey(op.name, project), op.timeoutMs) {
            if (project !in op.running(state.value)) return@arm
            state.update {
                op.mark(it, project, false).copy(
                    branchError =
                        it.branchError + (project to "Creating the branch timed out. Try again.")
                )
            }
        }
    }

    private fun apply(project: String, current: String, branches: List<GitBranch>, error: String?) {
        timeout.cancel(gitTimeoutKey(GitOp.Branches.name, project))
        state.update { s ->
            val next = GitOp.Branches.mark(s, project, false)
            if (error != null) {
                next.copy(branchError = s.branchError + (project to error))
            } else {
                next.copy(
                    branches = s.branches + (project to branches),
                    currentBranch = s.currentBranch + (project to current),
                    branchError = s.branchError - project
                )
            }
        }
    }

    /**
     * A checkout or a new branch (which is checked out) settled. The list is stale
     * now: it loads afresh, and meanwhile the current branch is the refreshed
     * snapshot's.
     */
    private fun finishSwitch(project: String, error: String?) {
        if (error != null) {
            state.update { it.copy(branchError = it.branchError + (project to error)) }
            return
        }
        state.update {
            it.copy(
                checkoutTick =
                    it.checkoutTick + (project to (it.checkoutTick[project] ?: 0) + 1),
                branches = it.branches - project,
                currentBranch = it.currentBranch - project
            )
        }
        onSwitched(project)
        load(project)
        client?.requestProjects()
        client?.requestSidebar()
    }

    private companion object {
        const val CHECKOUT = "checkout"
        const val CHECKOUT_TIMEOUT_MS = 60_000L
    }
}
