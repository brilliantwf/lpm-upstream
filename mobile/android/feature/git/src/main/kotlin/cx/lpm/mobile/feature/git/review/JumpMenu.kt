package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.feature.git.R

/** The bar's list button: every changed file by name, each scrolling to its diff. */
@Composable
internal fun JumpMenu(
    files: List<GitFile>,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassIconButton(
            icon = painterResource(DesignR.drawable.ic_format_list_bulleted),
            contentDescription = stringResource(R.string.git_review_jump),
            onClick = { open = true },
            iconSize = 26.dp
        )
        ContextMenu(expanded = open, onDismissRequest = { open = false }) {
            files.forEach { file ->
                ContextMenuItem(
                    title = lastPathComponent(file.path),
                    onClick = { onJump(file.path) },
                    icon = painterResource(statusIcon(file.status))
                )
            }
        }
    }
}

private fun statusIcon(status: String): Int = when (status) {
    "added" -> DesignR.drawable.ic_add_circle
    "deleted" -> DesignR.drawable.ic_do_not_disturb_on
    "renamed" -> DesignR.drawable.ic_arrow_circle_right
    "untracked" -> DesignR.drawable.ic_help
    else -> DesignR.drawable.ic_edit_circle
}
