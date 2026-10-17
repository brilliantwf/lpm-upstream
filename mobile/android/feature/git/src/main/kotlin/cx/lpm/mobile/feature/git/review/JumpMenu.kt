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
import cx.lpm.mobile.feature.git.R

/**
 * The bar's list button: every changed file by name over its folder, checked once
 * viewed, each scrolling to its diff.
 */
@Composable
internal fun JumpMenu(
    files: List<ReviewRow.FileHeader>,
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
            files.forEach { header ->
                ContextMenuItem(
                    title = header.file.name,
                    subtitle = header.file.directory.ifEmpty { null },
                    onClick = { onJump(header.path) },
                    icon = painterResource(
                        if (header.viewed) {
                            DesignR.drawable.ic_check_circle_fill
                        } else {
                            GitFileStatus.of(header.file.status).icon
                        }
                    )
                )
            }
        }
    }
}
