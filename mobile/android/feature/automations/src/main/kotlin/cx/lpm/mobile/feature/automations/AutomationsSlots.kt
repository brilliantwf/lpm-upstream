package cx.lpm.mobile.feature.automations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.automations.conversation.ReplyBar

/** The reply box under a run's conversation (iOS `TerminalComposer` without terminal tools). */
@Immutable
data class FollowupComposer(
    val session: SessionKey,
    /** The composer's own id (`automation:<project>:<job>:<run>`), so a draft outlives the page. */
    val composerId: String,
    val project: String,
    /** The automation's name, what the composer files a saved draft under. */
    val label: String,
    val disabled: Boolean,
    val placeholder: String,
    val onSend: (String) -> Unit
)

/** One message the reader can read aloud. */
@Immutable
data class ReadAloudRequest(
    /** Tells the reader whether the voice playing now is this message's. */
    val sourceId: String,
    val title: String,
    val subtitle: String,
    val markdown: String,
    /** Whose Mac renders the audio when the Mac engine reads. */
    val session: SessionKey = SessionKey.Live
)

/**
 * The parts of the automations screens other features own. The app fills each in as
 * its feature is ported; the composer falls back to a plain reply field until then.
 */
@Immutable
class AutomationsSlots(
    /** The reply box under a prompt automation's run (`:feature:composer`). */
    val followupComposer: @Composable (FollowupComposer) -> Unit = { ReplyBar(it) },
    /** "Read aloud" / "Stop reading" beside a message's copy button (`:feature:speech`). */
    val readAloudButton: @Composable (ReadAloudRequest) -> Unit = {},
    /** The reader's bar under the navigation bar while it speaks (`:feature:speech`). */
    val speechBar: @Composable () -> Unit = {},
    /** The block of a message (by its read-aloud source id) being spoken, for its highlight. */
    val speakingBlock: @Composable (sourceId: String) -> Int? = { null }
)
