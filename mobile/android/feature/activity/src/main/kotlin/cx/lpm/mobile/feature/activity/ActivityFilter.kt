package cx.lpm.mobile.feature.activity

import cx.lpm.mobile.core.model.trimmingWhitespace

private fun String.hit(query: String): Boolean = contains(query, ignoreCase = true)

private fun matches(row: ActivityRow, query: String): Boolean =
    row.project.label.hit(query) || row.title.hit(query) || row.tabTitle.orEmpty().hit(query)

/**
 * A group whose project matches keeps everything it offers; otherwise only the
 * matching profiles and services stay, and a group left with none drops out.
 */
private fun filtered(group: ActivityServiceGroup, query: String): ActivityServiceGroup? {
    if (group.project.label.hit(query)) return group
    val narrowed = group.copy(
        running = group.running.filter { it.hit(query) },
        declared = group.declared.filter { it.hit(query) },
        chips = group.chips.filter { it.name.hit(query) }
    )
    val empty = narrowed.running.isEmpty() && narrowed.declared.isEmpty() &&
        narrowed.chips.isEmpty()
    return if (empty) null else narrowed
}

fun filterActivity(activity: Activity, query: String, kind: ActivityKindFilter): VisibleActivity {
    val needle = query.trimmingWhitespace()
    val rows = when (kind) {
        ActivityKindFilter.All -> activity.rows

        ActivityKindFilter.Services -> emptyList()

        ActivityKindFilter.Agents -> activity.rows.filter { it.kind == ActivityRowKind.Agent }

        ActivityKindFilter.Automations ->
            activity.rows.filter { it.kind == ActivityRowKind.Automation }
    }
    val services = if (kind == ActivityKindFilter.All || kind == ActivityKindFilter.Services) {
        activity.services
    } else {
        emptyList()
    }
    if (needle.isEmpty()) return VisibleActivity(rows, services)
    return VisibleActivity(
        rows = rows.filter { matches(it, needle) },
        services = services.mapNotNull { filtered(it, needle) }
    )
}
