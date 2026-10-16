package cx.lpm.mobile.feature.projects.detail

import cx.lpm.mobile.core.data.projects.ProjectsState
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.ProjectAgentRow
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.model.agentRowsByTerminal
import cx.lpm.mobile.core.model.projectAgentRows

/** One terminal tab and what the agent in it reports (null for a plain shell). */
data class TabEntry(val terminal: TerminalInfo, val agent: ProjectAgentRow?)

/** One configured service, whether it runs, and whether a toggle is out for it. */
data class ServiceEntry(val service: Service, val running: Boolean, val pending: Boolean)

/** The line under the title: the project's state, or the link's while it's down. */
sealed interface DetailSubtitle {
    data object Running : DetailSubtitle

    data object Stopped : DetailSubtitle

    data object InvalidConfiguration : DetailSubtitle

    data class Link(val text: String) : DetailSubtitle
}

data class ProjectDetailUiState(
    val project: Project? = null,
    /** The status a person set leads the name, as in the projects list. */
    val title: String = "",
    val subtitle: DetailSubtitle? = null,
    /** Null until the tab list loads: a skeleton, not "No Terminals". */
    val tabs: List<TabEntry>? = null,
    /** A new tab is on its way: a placeholder row, and New tab waits. */
    val creating: Boolean = false,
    val services: List<ServiceEntry> = emptyList(),
    /** A Start/Stop is out for the whole project: Stop all waits. */
    val stopping: Boolean = false,
    /** The project has background runs listed under its services. */
    val hasBackgroundRuns: Boolean = false
) {
    val runningServices: Int get() = services.count { it.running }

    /** Stop all, while the project runs. */
    val canStopAll: Boolean get() = project?.running == true

    /** Nothing to list at all: the screen offers a new terminal instead. */
    val isEmpty: Boolean
        get() = tabs != null && tabs.isEmpty() && !creating && services.isEmpty() &&
            !hasBackgroundRuns

    /** Behind "No Terminals" or the config error there's no list for usage to follow. */
    val showsUsage: Boolean get() = !isEmpty && project?.configError == null
}

/**
 * A project's tabs, services and title from the session's stores. A visited
 * machine's project shows as the live Mac lists it ([listed]) until the machine answers.
 */
fun ProjectsState.detail(
    name: String,
    terminals: List<TerminalInfo>?,
    creating: Boolean,
    hasBackgroundRuns: Boolean,
    subtitleWhenLive: Boolean,
    linkText: String,
    now: Long,
    listed: Project? = null
): ProjectDetailUiState {
    val project =
        project(name) ?: listed ?: return ProjectDetailUiState(
            tabs = terminals?.map { TabEntry(it, null) },
            hasBackgroundRuns = hasBackgroundRuns
        )
    val agents = agentRowsByTerminal(projectAgentRows(project, now, emptyMap()))
    val running = if (project.running) project.services.map { it.name }.toSet() else emptySet()
    val toggles = pendingServiceToggle[name].orEmpty()
    val mark = project.workStatus?.displayEmoji.orEmpty()
    return ProjectDetailUiState(
        project = project,
        title = if (mark.isEmpty()) project.label else "$mark ${project.label}",
        subtitle = when {
            !subtitleWhenLive -> DetailSubtitle.Link(linkText)
            project.configError != null -> DetailSubtitle.InvalidConfiguration
            project.running -> DetailSubtitle.Running
            else -> DetailSubtitle.Stopped
        },
        tabs = terminals?.map { TabEntry(it, agents[it.id]) },
        creating = creating,
        services = project.allServices.map {
            ServiceEntry(it, running = it.name in running, pending = it.name in toggles)
        },
        stopping = name in pendingRun,
        hasBackgroundRuns = hasBackgroundRuns
    )
}
