package cx.lpm.mobile.feature.activity

/** One project's rows while the list is grouped. */
data class ActivityGroup(val id: String, val project: ActivityIdentity, val rows: List<ActivityRow>)

fun activityGroups(rows: List<ActivityRow>): List<ActivityGroup> {
    val byProject = linkedMapOf<String, ActivityGroup>()
    for (row in rows) {
        // Rows naming no project have no name to group on, and the sets they stand
        // for differ — keep "No project" and "Multiple projects" apart.
        val key = row.project.name.ifEmpty { row.project.label }
        val group = byProject[key] ?: ActivityGroup(key, row.project, emptyList())
        byProject[key] = group.copy(rows = group.rows + row)
    }
    return byProject.values.toList()
}

/** The order frozen while the list is being dragged, so rows never move out from under a thumb. */
fun freezeOrder(rows: List<ActivityRow>): Map<String, Int> =
    rows.withIndex().associate { (index, row) -> row.id to index }

/**
 * Rows the frozen order never saw sort to the bottom in attention order: a row that
 * arrives mid-hold has no place to land without moving something.
 */
fun heldOrder(rows: List<ActivityRow>, ranks: Map<String, Int>?): List<ActivityRow> {
    if (ranks == null) return rows
    return rows.sortedWith(
        compareBy<ActivityRow> { ranks[it.id] ?: Int.MAX_VALUE }.then(ActivityAttentionOrder)
    )
}

/** Whether some row belongs above the one ahead of it, which only a hold can cause. */
fun orderIsStale(rows: List<ActivityRow>): Boolean =
    rows.zipWithNext().any { (above, row) -> activityRowPrecedes(row, above) }
