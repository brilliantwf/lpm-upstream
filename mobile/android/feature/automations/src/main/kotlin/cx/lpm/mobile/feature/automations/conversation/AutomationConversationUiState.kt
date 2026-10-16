package cx.lpm.mobile.feature.automations.conversation

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.data.automations.AutomationsState
import cx.lpm.mobile.core.data.automations.automationKey
import cx.lpm.mobile.core.model.AutomationHistoryEntry
import cx.lpm.mobile.core.model.AutomationJob
import cx.lpm.mobile.feature.automations.format.AutomationClock
import cx.lpm.mobile.feature.automations.format.AutomationThread
import cx.lpm.mobile.feature.automations.format.AutomationTone
import cx.lpm.mobile.feature.automations.format.automationCurrencyText
import cx.lpm.mobile.feature.automations.format.automationDurationText
import cx.lpm.mobile.feature.automations.format.automationEntryLabel
import cx.lpm.mobile.feature.automations.format.automationMatching
import cx.lpm.mobile.feature.automations.format.automationResultTone
import cx.lpm.mobile.feature.automations.format.automationThreads

/** One turn of the conversation: what it was asked, how it went, what it said. */
@Immutable
data class MessageUi(
    val at: Long,
    val question: String,
    val output: String,
    /** A reply that simply finished keeps its status line under the answer, quietly. */
    val quiet: Boolean,
    val label: String,
    val tone: AutomationTone,
    val duration: String?,
    val cost: String?,
    val condensed: Boolean,
    val clock: String
)

@Immutable
data class AutomationConversationUiState(
    val job: AutomationJob? = null,
    val messages: List<MessageUi> = emptyList(),
    /** A reply sent and not in the history yet. */
    val pendingMessage: String? = null,
    /** The run's output so far. */
    val live: String = "",
    /** Replies wait while one is out or the automation runs. */
    val sending: Boolean = false,
    val followupError: String? = null
) {
    val running: Boolean get() = job?.running == true

    /** Only an agent's run can be answered. */
    val showsComposer: Boolean get() = job?.runKind == "prompt"
}

internal fun AutomationsState.thread(
    project: String,
    jobId: String,
    rootAt: Long
): AutomationThread? = automationThreads(history[automationKey(project, jobId)].orEmpty())
    .firstOrNull { it.root.at == rootAt }

internal fun AutomationsState.conversationState(
    project: String,
    jobId: String,
    rootAt: Long,
    pendingMessage: String?,
    clock: AutomationClock
): AutomationConversationUiState {
    val key = automationKey(project, jobId)
    val job = automationMatching(jobs, project, jobId)
    val entries = thread(project, jobId, rootAt)?.entries.orEmpty()
    return AutomationConversationUiState(
        job = job,
        messages = entries.map { it.message(isRoot = it.at == rootAt, clock) },
        pendingMessage = pendingMessage,
        live = liveOutput[key]?.text.orEmpty(),
        sending = pendingMessage != null || job?.running == true || key in pending,
        followupError = followupError[key]
    )
}

private fun AutomationHistoryEntry.message(isRoot: Boolean, clock: AutomationClock) = MessageUi(
    at = at,
    question = question,
    output = output,
    quiet = !isRoot && result == "completed",
    label = automationEntryLabel(this),
    tone = automationResultTone(result),
    duration = durationSecs?.let(::automationDurationText),
    cost = costUsd?.let(::automationCurrencyText),
    condensed = compacted,
    clock = clock.clock(at)
)
