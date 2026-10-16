package cx.lpm.mobile.feature.actions.button

import cx.lpm.mobile.core.model.Action

/** A group's `primary` that runs whichever child ran last. */
const val LAST_USED = "last-used"

/** The label as the desktop writes it, emoji first. */
val Action.displayLabel: String get() = if (emoji.isEmpty()) label else "$emoji $label"

/** The child's own name, without its `parent:` path. */
val Action.childKey: String get() = name.split(':').lastOrNull { it.isNotEmpty() } ?: name

/**
 * The child a split button runs: the one `primary` names (or the [remembered] one for
 * `last-used`), else the first runnable child. Null without `primary`.
 */
fun Action.primaryChild(remembered: String?): Action? {
    if (primary.isEmpty()) return null
    val target = if (primary == LAST_USED) remembered else primary
    return children.firstOrNull { target != null && it.childKey == target }
        ?: children.firstOrNull { it.isRunnable }
}
