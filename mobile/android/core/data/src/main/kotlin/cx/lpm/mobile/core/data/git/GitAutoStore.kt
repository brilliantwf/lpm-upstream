package cx.lpm.mobile.core.data.git

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.gitAuto
import cx.lpm.mobile.core.data.client.gitAutoCancel
import cx.lpm.mobile.core.data.client.gitAutoState
import cx.lpm.mobile.core.data.client.gitAutoSwitchBase
import cx.lpm.mobile.core.data.client.requestProjects
import cx.lpm.mobile.core.data.client.requestSidebar
import cx.lpm.mobile.core.data.session.GenerationTimeout
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.GitAutoKind
import cx.lpm.mobile.core.model.GitAutoRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Each project's Auto Commit / Auto Commit and Push / Auto Create PR run, and the
 * "Switch to <base> and pull" after a pull request lands: in flight, its failure, and
 * a count of successes the open sheet closes on.
 */
data class GitAutoState(
    val runs: Map<String, GitAutoRun> = emptyMap(),
    val switching: Set<String> = emptySet(),
    val switchError: Map<String, String> = emptyMap(),
    val switchedTick: Map<String, Int> = emptyMap()
) {
    fun isRunning(project: String): Boolean = runs[project]?.running == true
}

/**
 * The desktop's AI git shortcuts (iOS `GitAutoStore`). The Mac runs the whole chain
 * and broadcasts every change, so a locked phone loses nothing: after a reconnect it
 * asks for the current state of any run it still shows as running.
 */
class GitAutoStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(GitAutoState())
    val state: StateFlow<GitAutoState> = mutable.asStateFlow()

    // Projects whose run sheet is on screen; a run that fails while its sheet is
    // closed surfaces through the project screen's Git alert instead.
    private val presented = mutableSetOf<String>()
    private val timeout = GenerationTimeout<String>(session.scope)

    private val client: LpmClient? get() = session.client
    private val git: GitStore get() = session.store()

    override fun wire(client: LpmClient) {
        client.onGitAuto = ::apply
        client.onGitAutoSwitchBase = ::finishSwitch
    }

    override fun reset() {
        timeout.cancelAll()
        presented.clear()
        mutable.value = GitAutoState()
    }

    /**
     * The socket was replaced: re-ask for every run still showing as running, and drop
     * the switch spinner, whose reply can't survive the reconnect.
     */
    override fun onConnectionReset() {
        state.value.runs.filterValues { it.running }.keys.forEach { client?.gitAutoState(it) }
        state.value.switching.forEach { timeout.cancel(switchKey(it)) }
        mutable.update { it.copy(switching = emptySet()) }
    }

    /**
     * Start a run, or keep watching the one already going: the Mac allows one per
     * project and answers a second start with the run in progress.
     */
    fun start(project: String, kind: GitAutoKind) {
        if (state.value.isRunning(project)) return
        val placeholder = GitAutoRun.placeholder(kind)
        mutable.update { it.copy(runs = it.runs + (project to placeholder)) }
        client?.gitAuto(project, kind.rawValue, placeholder.nonce)
        timeout.arm(project, VERIFY_MS) { verifyStarted(project) }
    }

    fun cancel(project: String) {
        val run = state.value.runs[project] ?: return
        if (!run.running || run.canceling) return
        mutable.update { it.copy(runs = it.runs + (project to run.copy(canceling = true))) }
        client?.gitAutoCancel(project)
    }

    fun sheetAppeared(project: String) {
        presented += project
    }

    fun sheetDisappeared(project: String) {
        presented -= project
    }

    fun switchToBase(project: String, base: String) {
        if (base.isEmpty() || project in state.value.switching) return
        mutable.update {
            it.copy(switching = it.switching + project, switchError = it.switchError - project)
        }
        client?.gitAutoSwitchBase(project, base)
        timeout.arm(switchKey(project), SWITCH_TIMEOUT_MS) {
            if (project !in state.value.switching) return@arm
            mutable.update {
                it.copy(
                    switching = it.switching - project,
                    switchError =
                        it.switchError + (project to "Switching branch timed out. Try again.")
                )
            }
        }
    }

    fun dismissSwitchError(project: String) =
        mutable.update { it.copy(switchError = it.switchError - project) }

    /**
     * The start went unanswered. That can be a phone that slept through the reply,
     * so ask the Mac what it has before giving up; a Mac too old to know either verb
     * never answers at all.
     */
    private fun verifyStarted(project: String) {
        if (!waitingOnStart(project)) return
        client?.gitAutoState(project)
        timeout.arm(project, VERIFY_MS) {
            val run = state.value.runs[project] ?: return@arm
            if (!waitingOnStart(project)) return@arm
            val failed = run.copy(
                phase = GitAutoRun.Phase.Failed,
                error = "Your Mac didn't respond. " +
                    "Make sure lpm on your Mac is up to date, then try again."
            )
            finish(project, failed, tracked = true)
        }
    }

    private fun waitingOnStart(project: String): Boolean {
        val run = state.value.runs[project] ?: return false
        return run.running && run.runId == 0
    }

    private fun apply(project: String, run: GitAutoRun?, error: String?) {
        val current = state.value.runs[project]
        val tracked = current?.running == true
        if (error != null) {
            timeout.cancel(project)
            if (current != null && tracked) {
                finish(project, current.copy(phase = GitAutoRun.Phase.Failed, error = error), true)
            }
            return
        }
        // The Mac holds no run: the start never reached it, or it restarted mid-run.
        if (run == null) return lose(project)
        // Waiting on our own start, a finished run that isn't ours is the project's
        // previous one: the start never reached the Mac.
        if (current != null && tracked && current.runId == 0 && !run.running &&
            run.nonce != current.nonce
        ) {
            return lose(project)
        }
        timeout.cancel(project)
        if (current != null && current.runId > run.runId) return
        if (run.running || (!tracked && current?.runId == run.runId)) {
            mutable.update { it.copy(runs = it.runs + (project to run)) }
        } else {
            finish(project, run, tracked)
        }
    }

    private fun lose(project: String) {
        val lost = state.value.runs[project]?.takeIf { it.running } ?: return
        timeout.cancel(project)
        val error = if (lost.runId == 0) {
            "Couldn't start on your Mac. Try again."
        } else {
            "Lost track of this run on your Mac. Check the project before trying again."
        }
        finish(
            project,
            lost.copy(phase = GitAutoRun.Phase.Failed, canceling = false, error = error),
            tracked = true
        )
    }

    /**
     * A run reached its end: record it, and refresh everything it may have changed.
     * A failure the phone was following shows on the project's screen unless its
     * sheet is up to show it.
     */
    private fun finish(project: String, run: GitAutoRun, tracked: Boolean) {
        mutable.update { it.copy(runs = it.runs + (project to run)) }
        if (tracked && run.phase == GitAutoRun.Phase.Failed && project !in presented) {
            git.showOpError(project, GitOpError("${run.kind.title} failed", run.error))
        }
        refresh(project)
    }

    private fun finishSwitch(project: String, base: String, error: String?, pullError: String?) {
        timeout.cancel(switchKey(project))
        mutable.update { it.copy(switching = it.switching - project) }
        if (error != null) {
            mutable.update { it.copy(switchError = it.switchError + (project to error)) }
            return
        }
        if (pullError != null) {
            git.showOpError(
                project,
                GitOpError("Pull failed", "Switched to $base, but the pull failed: $pullError")
            )
        }
        mutable.update {
            it.copy(
                switchedTick =
                    it.switchedTick + (project to (it.switchedTick[project] ?: 0) + 1)
            )
        }
        refresh(project)
    }

    private fun refresh(project: String) {
        git.load(project)
        client?.requestProjects()
        client?.requestSidebar()
    }

    private fun switchKey(project: String) = "switch\u0000$project"

    private companion object {
        const val VERIFY_MS = 20_000L
        const val SWITCH_TIMEOUT_MS = 120_000L
    }
}
