package cx.lpm.mobile.feature.memory.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.SwipeAction
import cx.lpm.mobile.core.designsystem.component.SwipeActionsBox
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.memory.R

/**
 * "Remember this conversation": the brain, the title and what the agent will do. A
 * button row in an iOS list wears the tint, its secondary text at half strength.
 */
@Composable
internal fun RememberRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = LpmTheme.colors.tint
    ListRow(modifier = modifier, onClick = onClick, verticalPadding = 16.5.dp) {
        Box(
            Modifier
                .width(24.dp)
                .align(Alignment.Top),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_psychology),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.memory_picker_remember),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
            Text(
                text = stringResource(R.string.memory_picker_remember_description),
                fontSize = 12.sp,
                lineHeight = 14.5.sp,
                color = tint.copy(alpha = SECONDARY_ALPHA)
            )
        }
    }
}

/**
 * A saved session: tap continues it, the eye previews it, Delete is swiped from the
 * right. The chevron is the eye's: iOS marks the row's link.
 */
@Composable
internal fun PickerSessionRow(
    row: PickerRowUi,
    onPick: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val secondary = colors.tint.copy(alpha = SECONDARY_ALPHA)
    SwipeActionsBox(
        modifier = modifier,
        trailing = listOf(
            SwipeAction(
                stringResource(R.string.delete),
                DesignR.drawable.ic_delete,
                colors.red,
                onDelete
            )
        )
    ) {
        // The eye's 44 dp target sets the row's height: iOS's 63 pt.
        ListRow(onClick = onPick, verticalPadding = 9.5.dp) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.title,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    color = colors.tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = row.updated, fontSize = 11.sp, lineHeight = 13.sp, color = secondary)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clickableNoIndication(onClick = onPreview),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_visibility),
                    contentDescription = stringResource(R.string.memory_picker_preview),
                    tint = secondary,
                    modifier = Modifier.size(17.dp)
                )
            }
            DisclosureChevron()
        }
    }
}

private const val SECONDARY_ALPHA = 0.5f
