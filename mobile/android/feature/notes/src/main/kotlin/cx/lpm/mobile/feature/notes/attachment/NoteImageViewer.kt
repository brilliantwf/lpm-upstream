package cx.lpm.mobile.feature.notes.attachment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R

/** An attachment's picture full size on black, with Share and Done (iOS `NoteAttachmentViewer`). */
@Composable
internal fun NoteImageViewer(
    name: String,
    bytes: ByteArray?,
    onShare: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheet = rememberModalSheetState()
    val image by rememberNoteImage(bytes, FULL_PIXELS)
    ModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        state = sheet,
        contentInsets = WindowInsets(0)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val shown = image
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else if (bytes == null) {
                EmptyState(
                    title = stringResource(R.string.notes_attachment_no_preview),
                    icon = painterResource(DesignR.drawable.ic_description),
                    modifier = Modifier.align(Alignment.Center),
                    description = {
                        Text(
                            text = stringResource(R.string.notes_attachment_no_preview_hint),
                            style = LpmTheme.typography.body,
                            color = LpmTheme.colors.secondaryLabel,
                            textAlign = TextAlign.Center
                        )
                    }
                )
            }
            LpmTopBar(
                title = name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                windowInsets = WindowInsets(0),
                leading = {
                    if (onShare != null) {
                        GlassIconButton(
                            icon = painterResource(DesignR.drawable.ic_ios_share),
                            contentDescription = stringResource(R.string.notes_attachment_share),
                            onClick = onShare
                        )
                    }
                },
                trailing = {
                    GlassCapsuleButton(
                        text = stringResource(R.string.notes_done),
                        onClick = { sheet.dismiss(onDismiss) },
                        textStyle = LpmTheme.typography.headline
                    )
                }
            )
        }
    }
}

private const val FULL_PIXELS = 2048
