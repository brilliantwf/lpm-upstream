package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.SheetScaffold
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.terminal.R

/** A chip's preview: the image itself, or its name and type for a video or file. */
@Composable
internal fun AttachmentPreviewSheet(attachment: Attachment, onDismiss: () -> Unit) {
    val sheet = rememberModalSheetState()
    LpmTheme(darkTheme = true) {
        ModalSheet(onDismissRequest = onDismiss, state = sheet) {
            SheetScaffold(
                title = "",
                onDone = { sheet.dismiss(onDismiss) },
                doneLabel = stringResource(R.string.attachment_done)
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val image = attachment.thumbnail
                    if (attachment.kind == Attachment.Kind.Image && image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = attachment.filename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        FileSummary(attachment)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSummary(attachment: Attachment) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painterResource(
                if (attachment.kind ==
                    Attachment.Kind.Video
                ) {
                    DesignR.drawable.ic_movie
                } else {
                    DesignR.drawable.ic_description_fill
                }
            ),
            contentDescription = null,
            tint = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = attachment.filename,
            style = LpmTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = attachment.mime,
            style = LpmTheme.typography.subheadline,
            color = LpmTheme.colors.secondaryLabel
        )
    }
}
