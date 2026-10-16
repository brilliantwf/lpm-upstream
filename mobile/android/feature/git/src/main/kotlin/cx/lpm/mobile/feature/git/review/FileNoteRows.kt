package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/** Where a plain list's rows start and end. */
internal val PlainRowInset = ListDefaults.RowPadding

/** "CHANGED FILES (4)" as a plain row (a header would pin while scrolling). */
@Composable
internal fun ChangedFilesHeader(row: ReviewRow.ChangedHeader, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(
            start = PlainRowInset,
            end = PlainRowInset,
            top = 34.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.git_review_changed_files, row.count).uppercase(),
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = LpmTheme.colors.secondaryLabel
        )
        if (row.reviewed > 0) {
            Text(
                text = stringResource(R.string.git_review_reviewed, row.reviewed, row.count),
                style = LpmTheme.typography.caption2,
                color = LpmTheme.colors.tertiaryLabel
            )
        }
    }
}

@Composable
internal fun NoChangesRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ListDefaults.RowMinHeight)
            .padding(horizontal = PlainRowInset, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_check_circle_fill),
            contentDescription = null,
            tint = LpmTheme.colors.green,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(R.string.git_review_no_changes),
            style = LpmTheme.typography.body,
            color = LpmTheme.colors.secondaryLabel
        )
    }
}

/** What a file shows instead of its diff: binary, nothing to show, loading, or a failure. */
@Composable
internal fun FileNoteRow(note: FileNote, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val footnote = LpmTheme.typography.footnote
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ListDefaults.RowMinHeight)
            .padding(
                horizontal = 16.dp,
                vertical = if (note is FileNote.Binary ||
                    note is FileNote.Empty
                ) {
                    10.dp
                } else {
                    12.dp
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(
            if (note is FileNote.Binary ||
                note is FileNote.Empty
            ) {
                8.dp
            } else {
                10.dp
            }
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (note) {
            FileNote.Binary, FileNote.Empty -> {
                Icon(
                    painter = painterResource(
                        if (note ==
                            FileNote.Binary
                        ) {
                            DesignR.drawable.ic_file_present
                        } else {
                            DesignR.drawable.ic_description
                        }
                    ),
                    contentDescription = null,
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(
                        if (note ==
                            FileNote.Binary
                        ) {
                            R.string.git_file_binary
                        } else {
                            R.string.git_file_no_changes
                        }
                    ),
                    style = footnote,
                    color = colors.secondaryLabel
                )
            }

            FileNote.Loading -> {
                ActivityIndicator(size = ActivityIndicatorSize.Small)
                Text(
                    stringResource(R.string.git_file_loading),
                    style = footnote,
                    color = colors.secondaryLabel
                )
            }

            is FileNote.Failed -> {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_warning),
                    contentDescription = null,
                    tint = colors.orange,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = note.message,
                    style = footnote,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LinkButton(
                    text = stringResource(R.string.git_retry),
                    onClick = onRetry,
                    textStyle = footnote.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/** "↓ Show all N lines" under a file cut at the soft cap. */
@Composable
internal fun ShowAllRow(
    row: ReviewRow.ShowAll,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = LpmTheme.colors.tint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ListDefaults.RowMinHeight)
            .background(LpmTheme.colors.secondarySystemGroupedBackground)
            .clickableNoIndication(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_arrow_downward),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.git_file_show_all, row.total),
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
            color = tint
        )
    }
}
