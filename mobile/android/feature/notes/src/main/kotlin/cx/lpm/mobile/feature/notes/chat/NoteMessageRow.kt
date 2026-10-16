package cx.lpm.mobile.feature.notes.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.MarkdownText
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.NoteMessage
import cx.lpm.mobile.feature.notes.R
import cx.lpm.mobile.feature.notes.attachment.NoteAttachmentActions
import cx.lpm.mobile.feature.notes.attachment.NoteAttachmentView
import cx.lpm.mobile.feature.notes.data.NoteAttachmentsState

/** What a note offers behind a long press. */
class NoteMenuActions(
    val edit: (NoteMessage) -> Unit = {},
    val copy: (NoteMessage) -> Unit = {},
    val delete: (NoteMessage) -> Unit = {}
)

/**
 * One note in a transcript: its markdown, attachments, time and an "Edited" mark,
 * with Edit / Copy / Delete behind a long press. Every note is the user's own, so the
 * bubble sits trailing like a message you sent.
 */
@Composable
internal fun NoteMessageRow(
    note: NoteChatItem.Note,
    attachments: NoteAttachmentsState,
    attachmentActions: NoteAttachmentActions,
    menu: NoteMenuActions,
    modifier: Modifier = Modifier
) {
    val message = note.message
    val colors = LpmTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.padding(start = 40.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .alpha(if (note.busy) BUSY_ALPHA else 1f)
                .background(colors.secondarySystemBackground, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures(onLongPress = { menuOpen = true }) }
                .semantics {
                    onLongClick {
                        menuOpen = true
                        true
                    }
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.text.isNotEmpty()) MarkdownText(message.text, selectable = false)
            message.attachments?.takeIf { it.isNotEmpty() }?.let { list ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { NoteAttachmentView(it, attachments, attachmentActions) }
                }
            }
            Footer(note)
        }
        ContextMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ContextMenuItem(
                title = stringResource(R.string.notes_note_edit),
                onClick = { menu.edit(message) },
                icon = painterResource(DesignR.drawable.ic_edit)
            )
            ContextMenuItem(
                title = stringResource(R.string.notes_note_copy),
                onClick = { menu.copy(message) },
                icon = painterResource(DesignR.drawable.ic_content_copy)
            )
            ContextMenuItem(
                title = stringResource(R.string.notes_delete),
                onClick = { menu.delete(message) },
                icon = painterResource(DesignR.drawable.ic_delete),
                destructive = true
            )
        }
    }
}

@Composable
private fun Footer(note: NoteChatItem.Note) {
    val style = LpmTheme.typography.caption2.copy(
        fontSize = 10.sp,
        color = LpmTheme.colors.tertiaryLabel
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        if (note.busy) ActivityIndicator(size = ActivityIndicatorSize.Mini)
        if (note.message.editedAt !=
            null
        ) {
            Text(stringResource(R.string.notes_note_edited), style = style)
        }
        Text(note.time, style = style)
    }
}

private const val BUSY_ALPHA = 0.5f
