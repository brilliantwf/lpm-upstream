package cx.lpm.mobile.feature.notes.composer

import android.view.KeyCharacterMap
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R
import cx.lpm.mobile.feature.notes.chat.NoteDraft
import cx.lpm.mobile.feature.notes.ui.barMaterial

/** What the composer's field, attach menu and buttons do; a null camera leaves it out. */
@Immutable
class NotesComposerActions(
    val setText: (String) -> Unit = {},
    val send: () -> Unit = {},
    val photoLibrary: () -> Unit = {},
    val takePhoto: (() -> Unit)? = null,
    val chooseFiles: () -> Unit = {},
    val remove: (Long) -> Unit = {}
)

/**
 * The note composer under a transcript (iOS `NotesComposer`): a growing field, the
 * attach menu (Photo Library / Take Photo / Choose Files), chips for what's queued,
 * and Send. A hardware Return sends; Shift-Return types a newline.
 */
@Composable
internal fun NotesComposer(
    draft: NoteDraft,
    sending: Boolean,
    canSend: Boolean,
    actions: NotesComposerActions,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(barMaterial())
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { 1.toDp() })
                .background(colors.separator.dimmed(0.5f))
        )
        if (draft.pending.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                draft.pending.forEach { item ->
                    NotePendingChip(item, onRemove = { actions.remove(item.id) })
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AttachMenu(actions)
            Box(
                Modifier
                    .weight(1f)
                    .background(colors.secondarySystemBackground, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                PlainTextField(
                    value = draft.text,
                    onValueChange = actions.setText,
                    placeholder = stringResource(R.string.notes_composer_placeholder),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    maxLines = MAX_LINES,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            val sends = event.sendsNote
                            val down = event.nativeKeyEvent.action == NativeKeyEvent.ACTION_DOWN
                            if (sends && down && canSend) actions.send()
                            sends
                        }
                )
            }
            SendButton(
                sending = sending,
                hasContent = draft.hasContent,
                enabled = canSend,
                onSend = actions.send
            )
        }
    }
}

@Composable
private fun AttachMenu(actions: NotesComposerActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(34.dp)
                .clickableNoIndication(onClick = { open = true }),
            contentAlignment = Alignment.Center
        ) {
            // A menu's label takes the tint, at the secondary level `.secondary` asks for.
            Icon(
                painter = painterResource(DesignR.drawable.ic_add),
                contentDescription = stringResource(R.string.notes_composer_attach),
                tint = LpmTheme.colors.tint.dimmed(0.5f),
                modifier = Modifier.size(26.dp)
            )
        }
        ContextMenu(expanded = open, onDismissRequest = { open = false }) {
            ContextMenuItem(
                title = stringResource(R.string.notes_composer_photo_library),
                icon = painterResource(DesignR.drawable.ic_photo_library),
                onClick = actions.photoLibrary
            )
            actions.takePhoto?.let {
                ContextMenuItem(
                    title = stringResource(R.string.notes_composer_take_photo),
                    icon = painterResource(DesignR.drawable.ic_photo_camera),
                    onClick = it
                )
            }
            ContextMenuItem(
                title = stringResource(R.string.notes_composer_choose_files),
                icon = painterResource(DesignR.drawable.ic_description),
                onClick = actions.chooseFiles
            )
        }
    }
}

@Composable
private fun SendButton(
    sending: Boolean,
    hasContent: Boolean,
    enabled: Boolean,
    onSend: () -> Unit
) {
    val colors = LpmTheme.colors
    Box(
        Modifier
            .size(34.dp)
            .clickableNoIndication(onClick = onSend, enabled = enabled),
        contentAlignment = Alignment.Center
    ) {
        if (sending) {
            ActivityIndicator(size = ActivityIndicatorSize.Small)
        } else {
            val tint = if (hasContent) colors.tint else colors.secondaryLabel.dimmed(0.5f)
            Icon(
                painter = painterResource(DesignR.drawable.ic_arrow_circle_up_fill),
                contentDescription = stringResource(R.string.notes_composer_send),
                tint = if (enabled) tint else tint.dimmed(0.5f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** A physical keyboard's Return without Shift; the on-screen Return types a newline. */
private val KeyEvent.sendsNote: Boolean
    get() = key == Key.Enter && isHardware && !isShiftPressed

/** A key from a physical keyboard, not the on-screen one. */
private val KeyEvent.isHardware: Boolean
    get() = nativeKeyEvent.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD &&
        nativeKeyEvent.flags and NativeKeyEvent.FLAG_SOFT_KEYBOARD == 0

private const val MAX_LINES = 6

/** SwiftUI's `.opacity(_:)` on a color that already has some: the alphas multiply. */
private fun Color.dimmed(opacity: Float): Color = copy(alpha = alpha * opacity)
