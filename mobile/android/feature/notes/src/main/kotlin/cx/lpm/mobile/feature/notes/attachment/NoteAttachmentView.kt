package cx.lpm.mobile.feature.notes.attachment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.NoteAttachment
import cx.lpm.mobile.feature.notes.R
import cx.lpm.mobile.feature.notes.data.NoteAttachments
import cx.lpm.mobile.feature.notes.data.NoteAttachmentsState
import cx.lpm.mobile.feature.notes.format.noteSizeText

/** What an attachment under a note can do. */
class NoteAttachmentActions(
    val bytes: (String) -> ByteArray? = { null },
    val fetch: (NoteAttachment) -> Unit = {},
    val share: (NoteAttachment, ByteArray) -> Unit = { _, _ -> },
    val view: (NoteAttachment) -> Unit = {}
)

/**
 * One attachment under a note: an inline picture for images (tap for the viewer), a
 * glyph, name and size for anything else with Download, then Share once the bytes are
 * here. A file over the link's ceiling shows why instead of a spinner that never ends.
 */
@Composable
internal fun NoteAttachmentView(
    attachment: NoteAttachment,
    state: NoteAttachmentsState,
    actions: NoteAttachmentActions,
    modifier: Modifier = Modifier
) {
    val isImage = attachment.mimeType.startsWith("image/")
    val tooLarge = attachment.size > NoteAttachments.BYTE_LIMIT
    val bytes = if (attachment.hash in state.cached) actions.bytes(attachment.hash) else null
    val image by rememberNoteImage(bytes.takeIf { isImage }, INLINE_PIXELS)
    // An image is shown inline, so it's worth pulling up front; an over-cap file is
    // "fetched" only so the store answers with why it can't be.
    LaunchedEffect(attachment.hash) {
        if (isImage || tooLarge) actions.fetch(attachment)
    }
    val failure = state.error[attachment.hash]
    val shown = image
    when {
        failure != null -> Card(modifier) {
            Glyph(painterResource(DesignR.drawable.ic_warning))
            Labels(attachment.name, failure)
        }

        shown != null -> Image(
            bitmap = shown,
            contentDescription = attachment.name,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                .aspectRatio(shown.width.toFloat() / shown.height)
                .clip(RoundedCornerShape(12.dp))
                .clickableNoIndication(onClick = { actions.view(attachment) })
        )

        else -> Card(modifier) {
            Glyph(
                painterResource(
                    if (isImage) DesignR.drawable.ic_image else DesignR.drawable.ic_description_fill
                )
            )
            Labels(attachment.name, noteSizeText(attachment.size))
            Trailing(attachment, attachment.hash in state.loading, bytes, actions)
        }
    }
}

@Composable
private fun Trailing(
    attachment: NoteAttachment,
    loading: Boolean,
    bytes: ByteArray?,
    actions: NoteAttachmentActions
) {
    when {
        loading -> ActivityIndicator(size = ActivityIndicatorSize.Small)

        bytes != null -> CardButton(
            icon = painterResource(DesignR.drawable.ic_ios_share),
            label = stringResource(R.string.notes_attachment_share),
            onClick = { actions.share(attachment, bytes) }
        )

        else -> CardButton(
            icon = painterResource(DesignR.drawable.ic_arrow_circle_down),
            label = stringResource(R.string.notes_attachment_download),
            onClick = { actions.fetch(attachment) }
        )
    }
}

@Composable
private fun CardButton(icon: Painter, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clickableNoIndication(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = label,
            tint = LpmTheme.colors.label,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth()
            .background(LpmTheme.colors.tertiarySystemBackground, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun Glyph(icon: Painter) {
    Icon(
        painter = icon,
        contentDescription = null,
        tint = LpmTheme.colors.secondaryLabel,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun RowScope.Labels(name: String, detail: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = name,
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis
        )
        Text(
            text = detail,
            style = LpmTheme.typography.caption2,
            color = LpmTheme.colors.secondaryLabel
        )
    }
}

/** Pixels an inline picture is decoded to: 240 points at the densest screens, and some. */
private const val INLINE_PIXELS = 900
