package cx.lpm.mobile.feature.automations.list

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.data.automations.AutomationsState
import cx.lpm.mobile.core.model.AutomationJob
import cx.lpm.mobile.feature.automations.format.AutomationClock
import cx.lpm.mobile.feature.automations.format.AutomationTone
import cx.lpm.mobile.feature.automations.format.automationRowStatusText
import cx.lpm.mobile.feature.automations.format.automationScopeLabel
import cx.lpm.mobile.feature.automations.format.automationStatusTone
import cx.lpm.mobile.feature.automations.format.automationsSortedForList

/** One automation's row: what it is, where it runs, and the line under its name. */
@Immutable
data class AutomationRowUi(
    val job: AutomationJob,
    /** The projects it runs in, in a tag; empty when the page's project says it already. */
    val scope: String,
    val status: String,
    val statusTone: AutomationTone
) {
    val unread: Boolean get() = job.unread > 0
    val emoji: String get() = job.emoji.ifEmpty { "⏱️" }
}

@Immutable
data class AutomationsUiState(
    val loaded: Boolean = false,
    /** A project's own Automations page, or null for every job. */
    val project: String? = null,
    /** Everything unread first, newest activity first inside each half. */
    val unread: List<AutomationRowUi> = emptyList(),
    val read: List<AutomationRowUi> = emptyList(),
    val error: String? = null
) {
    val isEmpty: Boolean get() = unread.isEmpty() && read.isEmpty()
}

/**
 * One flat feed rather than a directory. A project's page lists only the jobs that
 * run in it, and a job that runs only there needs no scope tag.
 */
internal fun AutomationsState.listState(
    project: String?,
    projectCount: Int,
    clock: AutomationClock
): AutomationsUiState {
    val shown = if (project == null) jobs else jobs.filter { project in it.runTargets }
    val rows = automationsSortedForList(shown).map { job ->
        AutomationRowUi(
            job = job,
            scope = if (project != null && job.runsIn.size <= 1) {
                ""
            } else {
                automationScopeLabel(job, projectCount)
            },
            status = automationRowStatusText(job, clock),
            statusTone = automationStatusTone(job)
        )
    }
    return AutomationsUiState(
        loaded = loaded,
        project = project,
        unread = rows.filter { it.unread },
        read = rows.filterNot { it.unread },
        error = error
    )
}
