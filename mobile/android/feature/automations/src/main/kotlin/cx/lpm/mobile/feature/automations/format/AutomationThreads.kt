package cx.lpm.mobile.feature.automations.format

import cx.lpm.mobile.core.model.AutomationHistoryEntry
import cx.lpm.mobile.core.model.AutomationJob

/**
 * A run plus the follow-up replies that grew out of it: one row in the run history,
 * one page in the conversation.
 */
data class AutomationThread(
    val root: AutomationHistoryEntry,
    val replies: List<AutomationHistoryEntry>
) {
    val id: Long get() = root.at
    val entries: List<AutomationHistoryEntry> get() = listOf(root) + replies
    val tail: AutomationHistoryEntry get() = replies.lastOrNull() ?: root

    /** A run stays unread while any message in it is. */
    val unread: Boolean get() = root.unread || replies.any { it.unread }
}

fun automationThreads(entries: List<AutomationHistoryEntry>): List<AutomationThread> {
    val roots = mutableListOf<AutomationHistoryEntry>()
    val replies = mutableListOf<MutableList<AutomationHistoryEntry>>()
    val bySession = mutableMapOf<String, Int>()
    val byAt = mutableMapOf<Long, Int>()
    for (entry in entries) {
        val resumed = if (entry.resumed.isEmpty()) null else bySession[entry.resumed]
        val follows = entry.follows?.let { byAt[it] }
        val parent = resumed ?: follows
        val index = if (parent != null) {
            replies[parent] += entry
            parent
        } else {
            roots += entry
            replies.add(mutableListOf())
            roots.lastIndex
        }
        if (entry.session.isNotEmpty()) bySession[entry.session] = index
        byAt[entry.at] = index
    }
    return roots.mapIndexed { i, root -> AutomationThread(root, replies[i].toList()) }
}

/**
 * The job a page opened for [project] is about. A shared job is one row over several
 * projects, so it answers to any of them.
 */
fun automationMatching(jobs: List<AutomationJob>, project: String, jobId: String): AutomationJob? =
    jobs.firstOrNull { it.id == jobId && (project in it.runTargets || it.runProject == project) }

/**
 * Newest activity first, with everything unread ahead of everything read — the list
 * is a feed of what the automations did, not a directory of what exists.
 */
fun automationsSortedForList(jobs: List<AutomationJob>): List<AutomationJob> = jobs.sortedWith(
    compareByDescending<AutomationJob> { it.unread > 0 }
        .thenByDescending { it.running }
        .thenByDescending { it.lastRunAt ?: 0L }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
)

/**
 * Where a job runs, in a few words for its row: the project's own name, a count when
 * there are several, and "All projects" when that count is every project there is.
 */
fun automationScopeLabel(job: AutomationJob, projectCount: Int): String {
    val targets = job.runsIn
    return when {
        targets.isEmpty() -> "No project"
        targets.size == 1 -> targets[0]
        targets.size >= projectCount -> "All projects"
        else -> "${targets.size} projects"
    }
}

/** Where a job runs, for its own page: the project, the count, or none. */
fun automationScopeText(job: AutomationJob): String = when {
    job.standalone -> "No project"
    job.runsIn.size > 1 -> "${job.runsIn.size} projects"
    else -> job.runsIn.firstOrNull().orEmpty()
}

/** "Claude · opus · high effort"; an unpinned job says "Default", which the app picks. */
fun automationAgentText(job: AutomationJob): String {
    val parts = mutableListOf(agentName(job.agent))
    if (job.model.isNotEmpty()) parts += job.model
    if (job.effort.isNotEmpty()) parts += "${job.effort} effort"
    return parts.joinToString(" · ")
}

private fun agentName(agent: String): String = when (agent) {
    "" -> "Default"
    "claude" -> "Claude"
    "codex" -> "Codex"
    "gemini" -> "Gemini"
    "opencode" -> "OpenCode"
    else -> agent.replaceFirstChar(Char::uppercaseChar)
}

/**
 * The opening line of a run's answer or a job's prompt, for the rows that stand in
 * for them. Markdown bullets and headings lose their markers.
 */
fun automationFirstLine(text: String): String? {
    for (raw in text.split("\n")) {
        if (raw.isEmpty()) continue
        var line = raw.trim(' ', '\t')
        while (line.isNotEmpty() && line.first() in "#>-*•") {
            line = line.drop(1).trim(' ', '\t')
        }
        line = line.replace("**", "")
        if (line.length > 1) return line.take(FIRST_LINE_MAX)
    }
    return null
}

private const val FIRST_LINE_MAX = 140
