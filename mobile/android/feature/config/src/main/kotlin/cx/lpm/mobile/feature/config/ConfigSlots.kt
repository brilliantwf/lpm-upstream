package cx.lpm.mobile.feature.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import cx.lpm.mobile.core.model.SessionKey

/** One of a project's items in its own feature's editor sheet; a null key creates one. */
typealias ConfigEditorSheet = @Composable (
    project: String,
    editingKey: String?,
    session: SessionKey,
    onDismiss: () -> Unit
) -> Unit

/**
 * The parts of the config editors other features own; the app fills them in, and
 * until then they stay empty.
 */
@Immutable
class ConfigSlots(
    /** The service editor (`:feature:projects`). */
    val serviceEditor: ConfigEditorSheet = { _, _, _, _ -> },
    /** The action editor (`:feature:actions`). */
    val actionEditor: ConfigEditorSheet = { _, _, _, _ -> }
)
