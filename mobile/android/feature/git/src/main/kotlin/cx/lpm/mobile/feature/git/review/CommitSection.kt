package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/**
 * The "Commit" section: the message, "Generate message" (from the selected files'
 * changes), and "Commit N files" — only the selected files go in.
 */
@Composable
internal fun CommitSection(
    state: GitReviewUiState,
    actions: ReviewActions,
    modifier: Modifier = Modifier
) {
    // A plain list's section: its header and footer in body-size type.
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.git_review_commit_header),
            style = LpmTheme.typography.headline,
            color = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.padding(
                start = PlainRowInset,
                end = PlainRowInset,
                top = 31.dp,
                bottom = 12.dp
            )
        )
        PlainTextField(
            value = state.message,
            onValueChange = actions.setMessage,
            placeholder = stringResource(R.string.git_review_commit_message),
            minLines = 3,
            maxLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlainRowInset)
                .padding(top = 11.dp, bottom = 13.dp)
        )
        PlainSeparator()
        GenerateMessageButton(state, actions.generateMessage)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListDefaults.RowMinHeight)
                .padding(horizontal = PlainRowInset),
            contentAlignment = Alignment.Center
        ) {
            CommitButton(state = state, onClick = actions.commit)
        }
        Text(
            text = stringResource(R.string.git_review_commit_footer),
            style = LpmTheme.typography.body,
            modifier = Modifier.padding(
                start = PlainRowInset,
                end = PlainRowInset,
                top = 16.dp,
                bottom = 14.dp
            )
        )
    }
}

/** `.borderedProminent` across the row: "Commit N files", a spinner while it runs. */
@Composable
private fun CommitButton(
    state: GitReviewUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val enabled = state.canCommit
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = COMMIT_BUTTON_HEIGHT)
            .clip(CircleShape)
            .background(if (enabled) colors.tint else colors.secondarySystemFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (state.committing) {
            ActivityIndicator(size = ActivityIndicatorSize.Small)
        } else {
            Text(
                text = pluralStringResource(
                    R.plurals.git_review_commit_files,
                    state.selectedCount,
                    state.selectedCount
                ),
                style = LpmTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) Color.White else colors.tertiaryLabel
            )
        }
    }
}

@Composable
private fun GenerateMessageButton(state: GitReviewUiState, onClick: () -> Unit) {
    val colors = LpmTheme.colors
    val enabled = !state.generating && state.selectedCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListDefaults.RowMinHeight)
            .clickableNoIndication(onClick = onClick, enabled = enabled)
            .alpha(if (enabled || state.generating) 1f else DISABLED_ALPHA)
            .padding(horizontal = PlainRowInset, vertical = ListDefaults.RowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.generating) {
            ActivityIndicator(size = ActivityIndicatorSize.Small)
        } else {
            Icon(
                painter = painterResource(DesignR.drawable.ic_auto_awesome),
                contentDescription = null,
                tint = colors.label,
                modifier = Modifier.size(19.dp)
            )
        }
        val label = if (state.generating) {
            R.string.git_review_generating
        } else {
            R.string.git_review_generate_message
        }
        Text(
            text = stringResource(label),
            style = LpmTheme.typography.body,
            color = if (state.generating) colors.secondaryLabel else colors.label
        )
    }
}

/** "Create Pull Request", when the Mac has the GitHub CLI: a `Label` row, its icon tinted. */
@Composable
internal fun CreatePullRequestRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val slot = ListDefaults.IconSlot
    Column(modifier) {
        PlainSeparator()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListDefaults.RowMinHeight)
                .clickableNoIndication(onClick = onClick)
                .padding(horizontal = PlainRowInset, vertical = ListDefaults.RowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(slot.spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(slot.width), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_merge),
                    contentDescription = null,
                    tint = LpmTheme.colors.tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(R.string.git_review_create_pr),
                style = LpmTheme.typography.body
            )
        }
        PlainSeparator(Modifier.padding(start = slot.width + slot.spacing))
    }
}

/** A plain list's separator between rows, inset like the rows' content. */
@Composable
internal fun PlainSeparator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = PlainRowInset),
        thickness = ListDefaults.SeparatorThickness,
        color = LpmTheme.colors.separator
    )
}

private const val DISABLED_ALPHA = 0.4f
private val COMMIT_BUTTON_HEIGHT = 34.dp
