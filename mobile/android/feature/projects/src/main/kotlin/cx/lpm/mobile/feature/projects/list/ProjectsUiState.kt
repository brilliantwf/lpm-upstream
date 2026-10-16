package cx.lpm.mobile.feature.projects.list

import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.LinkStatusLine
import cx.lpm.mobile.core.data.link.MacReach
import cx.lpm.mobile.core.data.link.QueuedAction
import cx.lpm.mobile.core.data.machines.MachineSection
import cx.lpm.mobile.core.data.projects.DuplicateProgress
import cx.lpm.mobile.core.data.projects.ProjectsState
import cx.lpm.mobile.core.data.projects.SidebarItem
import cx.lpm.mobile.core.data.projects.SidebarRow
import cx.lpm.mobile.core.data.projects.sidebarItems
import cx.lpm.mobile.core.model.DuplicateOptions
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.ProjectAgentRow
import cx.lpm.mobile.core.model.ProjectFolder
import cx.lpm.mobile.core.model.WorkStatusChoice
import cx.lpm.mobile.core.model.projectAgentRows
import java.time.Instant

data class ProjectsUiState(
    val title: MacTitleState = MacTitleState(),
    val macs: List<MacChoice> = emptyList(),
    val demoMode: Boolean = false,
    val rows: List<ProjectsListRow> = emptyList(),
    /** No projects at all: something takes the list's place. */
    val listIsEmpty: Boolean = true,
    /** False until the first list arrives: a skeleton, not "No projects". */
    val loaded: Boolean = false,
    /** A connection problem worth showing in place of an empty list. */
    val issue: ConnectionIssue? = null,
    /** When the saved list on screen was last live, once a reconnect drags on. */
    val listAge: Instant? = null,
    val folders: List<ProjectFolder> = emptyList(),
    val duplicateDefaults: DuplicateOptions = DuplicateOptions(),
    val duplicateProgress: DuplicateProgress? = null,
    val badge: MoreBadge = MoreBadge.None,
    val automationsUnread: Int = 0,
    val workStatusMenu: List<WorkStatusChoice> = emptyList()
)

/** The title: the active Mac's name over how the phone reaches it. */
data class MacTitleState(
    val name: String = "Mac",
    val status: LinkStatusLine = LinkStatusLine("", LinkStatusLine.Tone.Waiting),
    /** "2 waiting" or "Details" after the status words. */
    val detail: MacTitleDetail? = null,
    /** The status line opens the connection details, except in Demo Mode. */
    val opensDetails: Boolean = true
)

sealed interface MacTitleDetail {
    data class Waiting(val count: Int) : MacTitleDetail

    data object Details : MacTitleDetail
}

/** One saved Mac in the switcher, and whether it answers right now. */
data class MacChoice(
    val id: String,
    val name: String,
    val active: Boolean,
    val linuxHost: Boolean,
    val status: MacStatus
)

sealed interface MacStatus {
    /** The live Mac: its own status line. */
    data class Live(val text: String) : MacStatus

    data class Reach(val reach: MacReach?, val lastConnected: Instant?) : MacStatus
}

/** The dot on the "…" button: an agent waiting outranks automations with news. */
enum class MoreBadge { None, NeedsYou, Problem, Automations }

/**
 * One row of the projects list: a project (with its terminal deck), a folder, or one
 * of the live Mac's machines with its projects under it.
 */
sealed interface ProjectsListRow {
    val key: String

    data class ProjectEntry(
        val project: Project,
        val indented: Boolean,
        val pending: Boolean,
        val stale: Boolean,
        val queued: List<QueuedAction>,
        val agents: List<ProjectAgentRow>,
        /** The folder it sits in, so its menu can skip that one and offer "No folder". */
        val folderId: String?
    ) : ProjectsListRow {
        override val key: String get() = "p:" + project.name
    }

    data class FolderEntry(val folder: ProjectFolder, val count: Int, val expanded: Boolean) :
        ProjectsListRow {
        override val key: String get() = "g:" + folder.id
    }

    /** A machine's header: which machine it is and whether it is on this phone yet. */
    data class MachineEntry(
        val slug: String,
        val name: String,
        val linuxHost: Boolean,
        val detail: MachineDetail?,
        /** How many projects it has; null when the Mac didn't list them. */
        val count: Int?,
        /** A Mac too old to list a machine's projects still gets you there: "Open". */
        val opensOnlyBySwitching: Boolean,
        val canOpen: Boolean,
        val expanded: Boolean
    ) : ProjectsListRow {
        override val key: String get() = "m:$slug"
    }

    /** A machine's project, opened in a session of its own while this Mac stays live. */
    data class MachineProjectEntry(
        val slug: String,
        val project: Project,
        val agents: List<ProjectAgentRow>,
        val canOpen: Boolean,
        val stale: Boolean
    ) : ProjectsListRow {
        override val key: String get() = "m:$slug/" + project.name
    }
}

/** The line under a machine's name. */
sealed interface MachineDetail {
    data object OpensThroughMac : MachineDetail

    data object Adding : MachineDetail

    data class Failed(val message: String) : MachineDetail

    data object NotOnPhone : MachineDetail

    data object NeedsUpdate : MachineDetail
}

/** What the list rows are built from besides the projects themselves. */
data class RowInputs(
    val terminalTitles: Map<String, Map<String, String>> = emptyMap(),
    val queued: List<QueuedAction> = emptyList(),
    val stale: Boolean = false,
    /** Folder id (or `m:<slug>` for a machine) → open or shut, where toggled on this phone. */
    val expanded: Map<String, Boolean> = emptyMap(),
    val now: Long = 0,
    /** The live Mac's connected machines, each at its `peer:` slot. */
    val machines: List<MachineSection> = emptyList(),
    val activeMacId: String? = null
)

/**
 * The projects list, arranged like the desktop sidebar: folders (open unless the
 * Mac saved them collapsed or the user shut them here) with their members indented,
 * each project followed by the agents it has going.
 */
fun ProjectsState.listRows(inputs: RowInputs): List<ProjectsListRow> {
    val folderOf = groups.flatMap { g -> g.members.map { it to g.id } }.toMap()
    fun entry(row: SidebarRow, indented: Boolean) = ProjectsListRow.ProjectEntry(
        project = row.project,
        indented = indented,
        pending = row.project.name in pendingRun,
        stale = inputs.stale,
        queued = inputs.queued.filter { it.project == row.project.name },
        agents = projectAgentRows(
            row.project,
            inputs.now,
            inputs.terminalTitles[row.project.name].orEmpty()
        ),
        folderId = folderOf[row.project.name]
    )
    val machines = inputs.machines.associateBy { it.id }
    return sidebarItems(inputs.machines.map { it.id }).flatMap { item ->
        when (item) {
            is SidebarItem.ProjectItem -> listOf(entry(item.row, indented = false))

            is SidebarItem.Folder -> {
                val open = inputs.expanded[item.folder.id] ?: !item.folder.collapsed
                val header = ProjectsListRow.FolderEntry(
                    folder = item.folder,
                    count = item.rows.count { !it.isChild },
                    expanded = open
                )
                listOf(header) +
                    if (open) item.rows.map { entry(it, indented = true) } else emptyList()
            }

            is SidebarItem.Machine -> machines[item.slug]?.let {
                machineListRows(it, inputs)
            }.orEmpty()
        }
    }
}
