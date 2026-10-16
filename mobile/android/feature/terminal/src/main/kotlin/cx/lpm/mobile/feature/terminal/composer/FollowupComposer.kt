package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The composer as a reply box — an automation run's follow-up: the same prompt
 * tabs, attachments, history, @-mentions and AI rewrite as a terminal's, on the
 * screen's grouped background, without the terminal's tools (special keys, slash
 * commands, its output as context, memory, running in duplicates). A message goes
 * to [onSend]; [target] keys its drafts, as a terminal id does. [onPreviewFile]
 * opens a mentioned file (left out when null).
 */
@Composable
fun FollowupComposer(
    target: ComposerTarget,
    disabled: Boolean,
    placeholder: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPreviewFile: ((String) -> Unit)? = null
) {
    val colors = LpmTheme.colors
    ComposerHost(
        target = target.copy(terminalTools = false),
        modifier = modifier,
        mode = ComposerMode(
            look = ComposerLook(
                background = colors.systemGroupedBackground,
                field = colors.secondarySystemGroupedBackground,
                placeholder = placeholder,
                enabled = !disabled
            ),
            onSend = onSend,
            onPreviewFile = onPreviewFile
        )
    )
}
