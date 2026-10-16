package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The queued attachment chips above the field. Each shows a thumbnail or a file
 * glyph with its name, its upload state and a remove button; tapping previews it,
 * or retries a failed upload. The Mac path an upload returns goes into the message
 * on send — never into the field.
 */
@Composable
internal fun ComposerAttachments(
    attachments: List<Attachment>,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    var previewing by remember { mutableStateOf<Attachment?>(null) }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(attachments, key = { it.id }) { attachment ->
            AttachmentChip(
                attachment = attachment,
                onTap = {
                    if (attachment.isFailed) {
                        onRetry(attachment.id)
                    } else {
                        previewing =
                            attachment
                    }
                },
                onRemove = { onRemove(attachment.id) }
            )
        }
    }
    previewing?.let { AttachmentPreviewSheet(attachment = it, onDismiss = { previewing = null }) }
}
