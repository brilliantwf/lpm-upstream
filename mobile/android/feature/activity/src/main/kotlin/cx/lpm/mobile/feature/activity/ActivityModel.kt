package cx.lpm.mobile.feature.activity

import cx.lpm.mobile.core.model.AgentState
import cx.lpm.mobile.core.model.Project

/**
 * `name` is the routing key every request takes, `label` the only thing safe to
 * show. An empty `name` means the row belongs to no single project.
 */
data class ActivityIdentity(
    val name: String,
    val label: String,
    val isCopy: Boolean = false,
    val isWorktree: Boolean = false,
    val isRemote: Boolean = false,
    /** The Mac's connected machine the project lives on, by slug and name; null for the Mac's own. */
    val machine: String? = null,
    val machineName: String? = null
) {
    /**
     * Exactly one surface shows these per layout — the row when the list is flat,
     * the section header when it is grouped.
     */
    val tags: List<String>
        get() = if (name.isEmpty()) {
            emptyList()
        } else {
            listOfNotNull(
                COPY_TAG.takeIf { isCopy },
                WORKTREE_TAG.takeIf { isWorktree },
                SSH_TAG.takeIf { isRemote },
                machineName
            )
        }

    companion object {
        fun of(project: Project, machine: ActivityMachine? = null) = ActivityIdentity(
            name = project.name,
            label = project.label,
            isCopy = project.parentName.isNotEmpty() && !project.worktree,
            isWorktree = project.worktree,
            isRemote = project.isRemote,
            machine = machine?.slug,
            machineName = machine?.name
        )

        /** A row that belongs to no single project: `label` stands for the set instead. */
        fun nameless(label: String) = ActivityIdentity(name = "", label = label)

        private const val COPY_TAG = "Copy"
        private const val WORKTREE_TAG = "Worktree"
        private const val SSH_TAG = "SSH"
    }
}

/** One of the Mac's connected machines and its projects, as Activity lists them. */
data class ActivityMachine(val slug: String, val name: String, val projects: List<Project>)

const val MULTI_PROJECT_LABEL = "Multiple projects"
const val STANDALONE_PROJECT_LABEL = "No project"

enum class ActivityRowKind { Agent, Automation }

data class ActivityRow(
    val id: String,
    val kind: ActivityRowKind,
    val project: ActivityIdentity,
    val title: String,
    /**
     * What the terminal tab is called — the agent's session title once it names one.
     * Null when the tab has no name of its own beyond the agent.
     */
    val tabTitle: String? = null,
    val state: AgentState,
    val statusValue: String? = null,
    val terminalId: String? = null,
    val jobId: String? = null,
    /**
     * The automation's owning project, as the automations list keys it — empty for a
     * global-layer job, which belongs to no one project.
     */
    val jobProject: String? = null,
    /**
     * Millis: when the row entered the state it is in. Null when nothing stamped it —
     * an unstamped row can't claim an elapsed time it doesn't know.
     */
    val stateSince: Long? = null,
    val detail: String? = null,
    val dismissable: Boolean = false,
    /** Why it cannot be dismissed, ready to show; null when it can. */
    val dismissBlocked: String? = null
)

/**
 * Something the project row can start or stop: a profile shortcut when one matches
 * the running set, or an individual service in a custom running set.
 */
data class ActivityChip(val kind: Kind, val name: String, val running: Boolean) {
    enum class Kind { Profile, Service }

    val id: String get() = (if (kind == Kind.Profile) "profile/" else "service/") + name
    val noun: String get() = if (kind == Kind.Profile) "profile" else "service"
}

data class ActivityServiceGroup(
    val project: ActivityIdentity,
    val running: List<String>,
    val declared: List<String>,
    /** The port each service declares, when it declares one. */
    val ports: Map<String, Int>,
    val chips: List<ActivityChip>
) {
    val id: String get() = project.name
}

data class Activity(
    val rows: List<ActivityRow> = emptyList(),
    val services: List<ActivityServiceGroup> = emptyList(),
    /** Projects with nothing running at all — one footer line, not N dead rows. */
    val quietProjectCount: Int = 0
)

/** What the list shows once the search and the kind filter have had their say. */
data class VisibleActivity(
    val rows: List<ActivityRow> = emptyList(),
    val services: List<ActivityServiceGroup> = emptyList()
) {
    val isEmpty: Boolean get() = rows.isEmpty() && services.isEmpty()
}

enum class ActivityKindFilter(val label: String) {
    All("All"),
    Agents("Agents"),
    Services("Services"),
    Automations("Automations")
}

/**
 * Needs-you, then problems, then working, then done, then silent; longest in that
 * state first; ties broken on the stable id so nothing swaps between renders.
 */
val ActivityAttentionOrder: Comparator<ActivityRow> =
    compareBy<ActivityRow> { it.state.rank }
        // Longest in that state first; an unstamped row can't out-rank a timed one.
        .thenBy { it.stateSince ?: Long.MAX_VALUE }
        .thenBy { it.id }

fun activityRowPrecedes(a: ActivityRow, b: ActivityRow): Boolean =
    ActivityAttentionOrder.compare(a, b) < 0

/**
 * "waiting 4m" / "working 2m" — how long the row has held its state. Empty when
 * nothing stamped the row, rather than a made-up "0s" that never grows.
 */
fun activityElapsedLabel(row: ActivityRow, now: Long): String {
    val since = row.stateSince ?: return ""
    val span = formatActivityDuration(maxOf(0, (now - since) / 1000))
    val word = when (row.state) {
        AgentState.NeedsYou -> "waiting"
        AgentState.Error -> "error"
        AgentState.Working -> "working"
        AgentState.Done -> "done"
        AgentState.Idle -> "open"
    }
    return "$word $span"
}

/** "12s", "4m", "4m 30s", "1h 12m" — matches the desktop's formatDuration. */
fun formatActivityDuration(secs: Long): String {
    if (secs < 60) return "${maxOf(0, secs)}s"
    val mins = secs / 60
    if (mins < 60) {
        val rest = secs % 60
        return if (rest > 0) "${mins}m ${rest}s" else "${mins}m"
    }
    val rest = mins % 60
    return if (rest > 0) "${mins / 60}h ${rest}m" else "${mins / 60}h"
}
