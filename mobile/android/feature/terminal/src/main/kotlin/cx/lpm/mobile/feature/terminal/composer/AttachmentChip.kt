package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.terminal.R

private val ChipShape = RoundedCornerShape(12.dp)
private val ThumbShape = RoundedCornerShape(8.dp)

@Composable
internal fun AttachmentChip(
    attachment: Attachment,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.padding(top = 6.dp, end = 6.dp)) {
        Row(
            modifier = Modifier
                .size(116.dp, 56.dp)
                .clip(ChipShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), ChipShape)
                .clickable(role = Role.Button, onClick = onTap)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(attachment)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = attachment.filename,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LpmTheme.colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                StatusLine(attachment)
            }
        }
        Icon(
            painter = painterResource(DesignR.drawable.ic_cancel_fill),
            contentDescription = stringResource(R.string.attachment_remove),
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
        )
    }
}

@Composable
private fun Thumbnail(attachment: Attachment) {
    val image = attachment.thumbnail
    if (image != null) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(38.dp).clip(ThumbShape)
            )
            if (attachment.kind == Attachment.Kind.Video) {
                Icon(
                    painterResource(DesignR.drawable.ic_play_arrow_fill),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ThumbShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(
                    if (attachment.kind == Attachment.Kind.Video) {
                        DesignR.drawable.ic_movie
                    } else {
                        DesignR.drawable.ic_description_fill
                    }
                ),
                contentDescription = null,
                tint = LpmTheme.colors.secondaryLabel,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun StatusLine(attachment: Attachment) {
    val colors = LpmTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (attachment.status) {
            Attachment.Status.Uploading -> {
                ActivityIndicator(size = ActivityIndicatorSize.Mini, color = colors.secondaryLabel)
                val progress = attachment.progress
                Text(
                    text = if (progress != null) {
                        stringResource(R.string.attachment_percent, (progress * PERCENT).toInt())
                    } else {
                        stringResource(R.string.attachment_uploading)
                    },
                    fontSize = 10.sp,
                    color = colors.secondaryLabel,
                    maxLines = 1
                )
            }

            is Attachment.Status.Uploaded -> {
                Icon(
                    painterResource(DesignR.drawable.ic_check_circle_fill),
                    contentDescription = null,
                    tint = colors.green,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    stringResource(R.string.attachment_ready),
                    fontSize = 10.sp,
                    color = colors.green,
                    maxLines = 1
                )
            }

            is Attachment.Status.Failed -> {
                Icon(
                    painterResource(DesignR.drawable.ic_refresh),
                    contentDescription = null,
                    tint = colors.orange,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    stringResource(R.string.attachment_retry),
                    fontSize = 10.sp,
                    color = colors.orange,
                    maxLines = 1
                )
            }
        }
    }
}

private const val PERCENT = 100
