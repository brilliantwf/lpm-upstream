package cx.lpm.mobile.core.data.automations

import cx.lpm.mobile.core.model.AutomationHistoryEntry
import cx.lpm.mobile.core.model.AutomationJob
import cx.lpm.mobile.core.model.AutomationLiveOutput
import kotlinx.serialization.json.JsonObject

/**
 * Everything the automations screens read (iOS `AppModel.automation*` and `job*`).
 * Per-job maps are keyed by [automationKey]: a shared job keeps its runs per project.
 */
data class AutomationsState(
    val jobs: List<AutomationJob> = emptyList(),
    /** False until the first list arrives. */
    val loaded: Boolean = false,
    /** The last request the Mac refused ("Couldn't update automation"). */
    val error: String? = null,
    val history: Map<String, List<AutomationHistoryEntry>> = emptyMap(),
    val historyLoading: Set<String> = emptySet(),
    val liveOutput: Map<String, AutomationLiveOutput> = emptyMap(),
    /** Run, stop, enable or a follow-up sent and not answered yet. */
    val pending: Set<String> = emptySet(),
    val followupError: Map<String, String> = emptyMap(),
    /** The raw YAML body fetched to seed the editor, and which job it's loading for. */
    val jobConfigBody: JsonObject? = null,
    val jobConfigError: String? = null,
    val jobConfigLoadingKey: String? = null,
    /** A save or delete is out (the Save spinner); a reply bumps [mutationDoneToken]. */
    val mutationInFlight: Boolean = false,
    val mutationError: String? = null,
    val mutationDoneToken: Int = 0
) {
    /** Automations with runs nobody has read yet, the count the "…" menu carries. */
    val unread: Int get() = jobs.count { it.unread > 0 }

    /** One project's automations with new runs, under its menu's "Automations". */
    fun unread(project: String): Int = jobs.count { it.unread > 0 && project in it.runTargets }
}

/** How the per-job maps address one job's runs in one project ("" is a standalone job's). */
fun automationKey(project: String, jobId: String): String = project + "\n" + jobId
