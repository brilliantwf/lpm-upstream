package cx.lpm.mobile.feature.notes.composer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R

/** A file queued for the next note: thumbnail or glyph, its name, and a remove badge. */
@Composable
internal fun NotePendingChip(
    item: PendingNoteAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    Box(modifier) {
        Row(
            Modifier
                .size(width = 150.dp, height = 54.dp)
                .background(colors.secondarySystemBackground, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(item)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = LpmTheme.typography.caption1.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                if (!item.ready) {
                    Text(
                        text = stringResource(R.string.notes_composer_preparing),
                        style = LpmTheme.typography.caption2.copy(fontSize = 10.sp),
                        color = colors.secondaryLabel
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .clickableNoIndication(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_close),
                contentDescription = stringResource(R.string.notes_composer_remove),
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun Thumbnail(item: PendingNoteAttachment) {
    val thumbnail = item.thumbnail
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        when {
            thumbnail != null -> Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            !item.ready -> ActivityIndicator(size = ActivityIndicatorSize.Small)

            else -> Icon(
                painter = painterResource(DesignR.drawable.ic_description_fill),
                contentDescription = null,
                tint = LpmTheme.colors.secondaryLabel,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
