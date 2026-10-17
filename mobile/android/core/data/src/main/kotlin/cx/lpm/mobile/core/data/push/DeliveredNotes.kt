package cx.lpm.mobile.core.data.push

import cx.lpm.mobile.core.model.Project

/** What a posted notification carries to be found again later. */
data class DeliveredNote(
    val tag: String?,
    val id: Int,
    val project: String?,
    val statusKey: String?,
    val status: String?,
    val serverId: String?
)

object DeliveredNotes {
    /** The notes a withdrawal names, posted by the Mac it came from. */
    fun cleared(delivered: List<DeliveredNote>, clear: PushClear): List<DeliveredNote> =
        delivered.filter { note ->
            val project = note.project ?: return@filter false
            val key = note.statusKey ?: return@filter false
            StatusIdentity(project, key) in clear.entries &&
                serverIdsMatch(note.serverId, clear.serverId)
        }

    /**
     * The active Mac's notes whose news is over (iOS `reconcileNotifications`): the
     * status entry is gone, or has moved on from the one announced. Only projects in
     * this list are judged, an automation alert (no status key) stays until it's
     * read, and a note from another Mac is left alone.
     */
    fun stale(
        delivered: List<DeliveredNote>,
        projects: List<Project>,
        activeServerId: String?
    ): List<DeliveredNote> {
        val live = projects.associate { p ->
            p.name to p.statusEntries.groupBy { it.key }.mapValues { it.value.first().value }
        }
        return delivered.filter { note ->
            val project = note.project ?: return@filter false
            val key = note.statusKey?.takeIf { it.isNotEmpty() } ?: return@filter false
            val entries = live[project] ?: return@filter false
            val current = entries[key]
            if (current != null && (note.status == null || note.status == current)) {
                return@filter false
            }
            note.serverId.isNullOrEmpty() || note.serverId == activeServerId
        }
    }
}
