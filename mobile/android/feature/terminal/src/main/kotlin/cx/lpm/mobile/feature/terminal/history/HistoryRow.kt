package cx.lpm.mobile.feature.terminal.history

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.HistoryItem
import cx.lpm.mobile.feature.terminal.R

/** What a history row's tap and long-press menu do. */
internal class HistoryRowActions(
    val onLoad: (HistoryItem) -> Unit,
    val onSendNow: (HistoryItem) -> Unit,
    val onToggleFavorite: (HistoryItem) -> Unit,
    val onMove: (HistoryItem) -> Unit,
    val onDelete: (HistoryItem) -> Unit
)

/** A message: up to three lines of it over its draft badge, project, star and age. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryRow(
    item: HistoryItem,
    actions: HistoryRowActions,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = {
                    menu = true
                }, onClick = { actions.onLoad(item) })
                .listSeparator()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = item.text.ifEmpty { stringResource(R.string.history_empty_text) },
                fontSize = 15.sp,
                color = colors.label,
                maxLines = 3
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isDraft) {
                    Text(
                        text = stringResource(R.string.history_draft),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.orange,
                        modifier = Modifier
                            .background(colors.orange.copy(alpha = 0.18f), CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (item.project.isNotEmpty()) {
                    Text(
                        item.project,
                        fontSize = 11.sp,
                        color = colors.secondaryLabel,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                if (item.favorite) {
                    Icon(
                        painterResource(DesignR.drawable.ic_star_fill),
                        contentDescription = null,
                        tint = colors.yellow,
                        modifier = Modifier.size(12.dp)
                    )
                }
                relativeTime(item.timestamp)?.let {
                    Text(it, fontSize = 11.sp, color = colors.tertiaryLabel)
                }
            }
        }
        ContextMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ContextMenuItem(
                title = stringResource(R.string.history_send_now),
                icon = painterResource(DesignR.drawable.ic_send),
                onClick = { actions.onSendNow(item) }
            )
            ContextMenuItem(
                title = stringResource(R.string.history_load),
                icon = painterResource(DesignR.drawable.ic_edit_square),
                onClick = { actions.onLoad(item) }
            )
            ContextMenuItem(
                title = stringResource(
                    if (item.favorite) R.string.history_unfavorite else R.string.history_favorite
                ),
                icon = painterResource(
                    if (item.favorite) DesignR.drawable.ic_star_fill else DesignR.drawable.ic_star
                ),
                onClick = { actions.onToggleFavorite(item) }
            )
            ContextMenuItem(
                title = stringResource(R.string.history_move_to_folder),
                icon = painterResource(DesignR.drawable.ic_folder),
                onClick = { actions.onMove(item) }
            )
            ContextMenuItem(
                title = stringResource(R.string.history_delete),
                icon = painterResource(DesignR.drawable.ic_delete),
                destructive = true,
                onClick = { actions.onDelete(item) }
            )
        }
    }
}

/** "5 min. ago", "2 wk. ago": iOS's abbreviated relative date; nothing for an unstamped row. */
private fun relativeTime(millis: Long): String? {
    if (millis <= 0) return null
    val seconds = ((System.currentTimeMillis() - millis) / MILLIS_PER_SECOND).coerceAtLeast(0)
    val (amount, unit) = when {
        seconds < MINUTE -> seconds to RelativeDateTimeFormatter.RelativeUnit.SECONDS
        seconds < HOUR -> seconds / MINUTE to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        seconds < DAY -> seconds / HOUR to RelativeDateTimeFormatter.RelativeUnit.HOURS
        seconds < WEEK -> seconds / DAY to RelativeDateTimeFormatter.RelativeUnit.DAYS
        seconds < MONTH -> seconds / WEEK to RelativeDateTimeFormatter.RelativeUnit.WEEKS
        seconds < YEAR -> seconds / MONTH to RelativeDateTimeFormatter.RelativeUnit.MONTHS
        else -> seconds / YEAR to RelativeDateTimeFormatter.RelativeUnit.YEARS
    }
    return relativeFormatter.format(
        amount.toDouble(),
        RelativeDateTimeFormatter.Direction.LAST,
        unit
    )
}

private val relativeFormatter: RelativeDateTimeFormatter by lazy {
    RelativeDateTimeFormatter.getInstance(
        ULocale.getDefault(),
        null,
        RelativeDateTimeFormatter.Style.SHORT,
        DisplayContext.CAPITALIZATION_NONE
    )
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MINUTE = 60L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY
private const val MONTH = 30 * DAY
private const val YEAR = 365 * DAY
