package cx.lpm.mobile.core.data.actions

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.cancelActionBackground
import cx.lpm.mobile.core.data.client.requestActionBgOutput
import cx.lpm.mobile.core.data.client.requestBackgroundRuns
import cx.lpm.mobile.core.data.client.runAction
import cx.lpm.mobile.core.data.client.runActionBackground
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.data.terminals.TerminalsStore
import cx.lpm.mobile.core.model.ActionBgOutput
import cx.lpm.mobile.core.model.BackgroundRunInfo
import cx.lpm.mobile.core.model.BackgroundRunSummary
import cx.lpm.mobile.core.model.TerminalInfo
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Headless background runs the phone started or re-attached to, each keyed by its runId. */
data class ActionsState(
    /** The latest polled snapshot of each run. */
    val outputs: Map<String, ActionBgOutput> = emptyMap(),
    /** What's known of each run from its start, so it shows before the first poll lands. */
    val runs: Map<String, BackgroundRunInfo> = emptyMap(),
    /** Why the Mac refused to start a run. */
    val startErrors: Map<String, String> = emptyMap()
) {
    /** A project's runs, newest first. */
    fun runs(project: String): List<BackgroundRunInfo> =
        runs.values.filter { it.project == project }.sortedByDescending { it.startedAt }

    /** Until its first poll a started run counts as running; a refused start doesn't. */
    fun isRunning(runId: String): Boolean =
        runId in runs && runId !in startErrors && outputs[runId]?.running ?: true
}

/**
 * Running a project's actions (iOS `AppModel`'s action area): terminal and command
 * actions are relayed to the Mac's terminal flow, every other action runs headless
 * on the Mac, and its output is polled from there.
 */
class ActionsStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(ActionsState())
    val state: StateFlow<ActionsState> = mutable.asStateFlow()

    private val client: LpmClient? get() = session.client

    override fun wire(client: LpmClient) {
        client.onActionBgOutput = { runId, snapshot ->
            // Not found: the Mac reaped it (or never had it). Keeping it would leave
            // the row spinning on "Running" and polled forever.
            if (snapshot == null) {
                prune(runId)
            } else {
                mutable.update {
                    it.copy(
                        outputs = it.outputs + (runId to snapshot),
                        runs = it.runs + (runId to (it.runs[runId] ?: snapshot.info(runId)))
                    )
                }
            }
        }
        client.onActionBgStartFailed = { runId, error ->
            mutable.update { it.copy(startErrors = it.startErrors + (runId to error)) }
        }
        client.onBackgroundRuns = ::reattach
    }

    override fun reset() {
        mutable.value = ActionsState()
    }

    /**
     * Run a terminal or command action on the Mac, waiting for the link if it's down.
     * [onSpawn] is told about the terminal it opens. [confirmed] says the phone already
     * asked for its inputs and confirmation, so the Mac doesn't ask again.
     */
    fun runAction(
        project: String,
        action: String,
        inputValues: Map<String, String> = emptyMap(),
        confirmed: Boolean = false,
        onSpawn: ((TerminalInfo) -> Unit)? = null
    ) {
        val label = session.store<ProjectsStore>().state.value.project(project)?.actions
            ?.flatMap { it.runnableLeaves }
            ?.firstOrNull { it.name == action }
            ?.label ?: action
        session.whenConnected("Run “$label”", project) { c ->
            c.runAction(project, action, inputValues, confirmed)
            val terminals = session.store<TerminalsStore>()
            terminals.markTerminalCreating(project, onSpawn)
            terminals.reloadTerminalsSoon(project)
        }
    }

    /** Start a non-terminal action headless on the Mac; poll its runId for the output. */
    fun startBackground(
        project: String,
        action: String,
        label: String,
        inputValues: Map<String, String>
    ): String {
        val runId = UUID.randomUUID().toString().uppercase()
        val info = BackgroundRunInfo(runId, project, label, nowSeconds())
        mutable.update {
            it.copy(runs = it.runs + (runId to info), startErrors = it.startErrors - runId)
        }
        client?.runActionBackground(project, action, inputValues, runId)
        return runId
    }

    fun loadOutput(project: String, runId: String) {
        client?.requestActionBgOutput(project, runId)
    }

    fun cancel(runId: String) {
        client?.cancelActionBackground(runId)
    }

    /** Ask for the project's runs on the Mac, to re-attach to one started before a relaunch. */
    fun loadRuns(project: String) {
        client?.requestBackgroundRuns(project)
    }

    /** Poll one run every 2 s while it runs, as its open sheet does. Returns once it ends. */
    suspend fun follow(project: String, runId: String) {
        loadOutput(project, runId)
        while (state.value.isRunning(runId)) {
            delay(FOLLOW_INTERVAL_MS)
            if (state.value.isRunning(runId)) loadOutput(project, runId)
        }
    }

    /**
     * Keep a project's runs current while its screen shows them: every 3 s, poll each
     * one still running (or not yet polled) while the link is up. Never returns.
     */
    suspend fun watch(project: String) {
        while (true) {
            if (session.state.value.isReady) {
                val s = state.value
                s.runs(project).filter { s.isRunning(it.runId) }
                    .forEach { loadOutput(project, it.runId) }
            }
            delay(WATCH_INTERVAL_MS)
        }
    }

    // The Mac's list is authoritative (running plus finished within its TTL): a run of
    // ours it no longer carries was reaped. Pruning here also bounds a long session.
    private fun reattach(project: String, listed: List<BackgroundRunSummary>) {
        val ids = listed.map { it.runId }.toSet()
        state.value.runs.values
            .filter { it.project == project && it.runId !in ids }
            .forEach { prune(it.runId) }
        mutable.update { s ->
            val found = listed.filter { it.runId !in s.runs }
                .associate {
                    it.runId to
                        BackgroundRunInfo(it.runId, project, it.label, it.startedAt)
                }
            s.copy(runs = s.runs + found)
        }
    }

    // Drop a run the Mac no longer knows, except one that failed to start (its error
    // stays up) and one started under 10 s ago with no snapshot yet: the Mac may not
    // have registered it yet.
    private fun prune(runId: String) {
        val s = state.value
        if (runId in s.startErrors) return
        val info = s.runs[runId]
        if (info != null && runId !in s.outputs && nowSeconds() - info.startedAt < GRACE_SECONDS) {
            return
        }
        mutable.update { it.copy(runs = it.runs - runId, outputs = it.outputs - runId) }
    }

    private fun nowSeconds(): Long = session.env.clock.millis() / MILLIS_PER_SECOND

    private fun ActionBgOutput.info(id: String) = BackgroundRunInfo(id, project, label, startedAt)

    private companion object {
        const val FOLLOW_INTERVAL_MS = 2_000L
        const val WATCH_INTERVAL_MS = 3_000L
        const val GRACE_SECONDS = 10L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
