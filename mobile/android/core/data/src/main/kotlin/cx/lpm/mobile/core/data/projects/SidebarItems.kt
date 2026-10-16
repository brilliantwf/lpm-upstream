package cx.lpm.mobile.core.data.projects

import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.ProjectFolder

/** A project row: the project plus whether it's a duplicate nested under its parent. */
data class SidebarRow(val project: Project, val isChild: Boolean) {
    val id: String get() = project.name
}

/** One row of the projects screen: a project, a folder with its members, or a machine. */
sealed interface SidebarItem {
    val id: String

    data class ProjectItem(val row: SidebarRow) : SidebarItem {
        override val id: String get() = "p:" + row.project.name
    }

    data class Folder(val folder: ProjectFolder, val rows: List<SidebarRow>) : SidebarItem {
        override val id: String get() = "g:" + folder.id
    }

    /** A connected machine's section, at the slot the Mac's sidebar gives it. */
    data class Machine(val slug: String) : SidebarItem {
        override val id: String get() = "m:$slug"
    }
}

/**
 * The projects arranged exactly like the desktop sidebar (a port of Sidebar.tsx's
 * tree build): walk `sidebarOrder`, emitting each folder with its members, each
 * loose (non-duplicate, non-member) project, and each connected machine at its
 * `peer:<slug>` slot, with every duplicate nested right after its parent. Anything
 * missing from the order is appended so nothing vanishes. A duplicate whose parent
 * is gone counts as top-level.
 */
fun ProjectsState.sidebarItems(machineSlugs: List<String> = emptyList()): List<SidebarItem> {
    val byName = projects.distinctBy { it.name }.associateBy { it.name }
    val membership = mutableMapOf<String, String>()
    for (g in groups) for (m in g.members) membership[m] = g.id

    fun isDup(p: Project) = p.parentName.isNotEmpty() && byName[p.parentName] != null

    // Duplicates not placed in a folder nest under their parent, in list order.
    val childrenByParent = mutableMapOf<String, MutableList<Project>>()
    for (p in projects) {
        if (isDup(p) &&
            membership[p.name] == null
        ) {
            childrenByParent.getOrPut(p.parentName) { mutableListOf() } +=
                p
        }
    }
    val groupsById = groups.distinctBy { it.id }.associateBy { it.id }
    val out = mutableListOf<SidebarItem>()
    val rendered = mutableSetOf<String>()

    fun rows(p: Project): List<SidebarRow> {
        rendered += p.name
        val r = mutableListOf(SidebarRow(p, isChild = false))
        for (child in childrenByParent[p.name].orEmpty()) {
            rendered += child.name
            r += SidebarRow(child, isChild = true)
        }
        return r
    }

    val machines = machineSlugs.distinct().toMutableList()
    val seenGroups = mutableSetOf<String>()
    fun emitGroup(g: ProjectFolder) {
        seenGroups += g.id
        out += SidebarItem.Folder(g, g.members.mapNotNull { byName[it] }.flatMap(::rows))
    }

    for (token in sidebarOrder) {
        when {
            token.startsWith("group:") -> {
                val gid = token.removePrefix("group:")
                val g = groupsById[gid]
                if (g != null && gid !in seenGroups) emitGroup(g)
            }

            token.startsWith("peer:") -> {
                val slug = token.removePrefix("peer:")
                if (machines.remove(slug)) out += SidebarItem.Machine(slug)
            }

            else -> {
                val p = byName[token]
                if (p != null && token !in rendered && !isDup(p) && membership[token] == null) {
                    out += rows(p).map(SidebarItem::ProjectItem)
                }
            }
        }
    }
    // Folders missing from the order, then brand-new loose projects not yet in it.
    for (g in groups) if (g.id !in seenGroups) emitGroup(g)
    for (p in projects) {
        if (p.name !in rendered && !isDup(p) && membership[p.name] == null) {
            out += rows(p).map(SidebarItem::ProjectItem)
        }
    }
    machines.forEach { out += SidebarItem.Machine(it) }
    return out
}
