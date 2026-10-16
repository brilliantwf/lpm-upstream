package cx.lpm.mobile.feature.projects.menu

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ContextMenuDivider
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.ContextMenuSubmenu
import cx.lpm.mobile.core.model.WorkStatus
import cx.lpm.mobile.core.model.WorkStatusChoice
import cx.lpm.mobile.core.model.sameWorkStatus
import cx.lpm.mobile.feature.projects.R as ProjectsR

/**
 * "Status ▸" in a project's menu: every status the app ships with and the user's own
 * from the Mac, in the order the Mac's Settings put them, then Clear once something
 * is set. [onPick] gets null for Clear; a choice that asks for a line is the
 * caller's to prompt for.
 */
@Composable
fun ColumnScope.WorkStatusSubmenu(
    current: WorkStatus?,
    choices: List<WorkStatusChoice>,
    onPick: (WorkStatusChoice?) -> Unit
) {
    ContextMenuSubmenu(
        title = stringResource(ProjectsR.string.status_menu),
        icon = painterResource(R.drawable.ic_sentiment_satisfied)
    ) {
        choices.forEach { choice ->
            ContextMenuItem(
                title = choiceTitle(choice),
                onClick = { onPick(choice) },
                icon = if (sameWorkStatus(current, choice.input)) {
                    painterResource(R.drawable.ic_check)
                } else {
                    null
                }
            )
        }
        if (current != null) {
            ContextMenuDivider()
            ContextMenuItem(
                title = stringResource(ProjectsR.string.status_clear, current.displayLabel),
                onClick = { onPick(null) },
                icon = painterResource(R.drawable.ic_cancel),
                destructive = true
            )
        }
    }
}

/** How the menu lists a choice: "⏳  In progress". */
fun choiceTitle(choice: WorkStatusChoice): String =
    if (choice.emoji.isEmpty()) choice.label else "${choice.emoji}  ${choice.label}"
