package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R
import cx.lpm.mobile.feature.git.ui.BarSpinner
import cx.lpm.mobile.feature.git.ui.SheetBar

/** What the commit sheet's controls do. */
class CommitActions(
    val setMessage: (String) -> Unit = {},
    val generateMessage: () -> Unit = {},
    val toggleSelected: (String) -> Unit = {},
    val selectAll: (Boolean) -> Unit = {},
    val commit: () -> Unit = {}
)

/**
 * The commit sheet (iOS `GitCommitSheet`), the phone's take on the desktop's Commit
 * dialog: the message with "Generate message", every changed file to include or
 * leave out, and Commit. The message and selection live with the review, so closing
 * the sheet keeps the draft; a successful commit closes it.
 */
@Composable
internal fun GitCommitSheet(
    state: GitReviewUiState,
    actions: CommitActions,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalSheetState()
    val opened = remember { state.commitTick }
    LaunchedEffect(state.commitTick) {
        if (state.commitTick != opened) sheet.dismiss(onDismiss)
    }
    ModalSheet(onDismissRequest = onDismiss, state = sheet) {
        SheetBar(
            title = stringResource(R.string.git_commit_title),
            leading = {
                GlassCapsuleButton(
                    text = stringResource(R.string.cancel),
                    onClick = { sheet.dismiss(onDismiss) }
                )
            },
            trailing = {
                if (state.committing) {
                    BarSpinner()
                } else {
                    GlassCapsuleButton(
                        text = stringResource(R.string.git_review_commit),
                        onClick = actions.commit,
                        enabled = state.canCommit,
                        textStyle = LpmTheme.typography.headline
                    )
                }
            }
        )
        CommitForm(state, actions)
    }
}

@Composable
private fun CommitForm(state: GitReviewUiState, actions: CommitActions) {
    val allSelected = state.commitFiles.all { it.selected }
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(top = ListDefaults.ContentTopPadding, bottom = 24.dp)
    ) {
        if (state.conflicted.isNotEmpty()) {
            ListSection {
                ListRow(
                    leadingSlot = ListLeadingSlot(width = 20.dp, spacing = 12.dp),
                    leading = {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_warning_fill),
                            contentDescription = null,
                            tint = LpmTheme.colors.red,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text(
                        text = conflictNotice(state.conflicted),
                        style = LpmTheme.typography.footnote,
                        color = LpmTheme.colors.red
                    )
                }
            }
        }
        ListSection {
            TextFieldRow(
                value = state.message,
                onValueChange = actions.setMessage,
                placeholder = stringResource(R.string.git_commit_message),
                minLines = 3,
                maxLines = 8
            )
            GenerateRow(
                generating = state.generating,
                enabled = !state.generating && state.selectedCount > 0,
                onClick = actions.generateMessage
            )
        }
        ListSection(
            header = stringResource(R.string.git_commit_files),
            headerStyle = ListHeaderStyle.Title,
            headerAction = {
                val label = if (allSelected) {
                    R.string.git_commit_deselect_all
                } else {
                    R.string.git_commit_select_all
                }
                LinkButton(
                    text = stringResource(label),
                    onClick = { actions.selectAll(!allSelected) },
                    enabled = state.commitFiles.isNotEmpty()
                )
            },
            footer = stringResource(R.string.git_commit_footer)
        ) {
            state.commitFiles.forEach { file ->
                CommitFileRow(file, onToggle = { actions.toggleSelected(file.file.path) })
            }
        }
    }
}

/** "Generate message", a spinner and "Generating…" while the Mac writes it. */
@Composable
private fun GenerateRow(generating: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LpmTheme.colors
    ListRow(
        onClick = onClick,
        enabled = enabled,
        leadingSlot = ListLeadingSlot(width = 20.dp, spacing = 8.dp),
        leading = {
            if (generating) {
                ActivityIndicator(size = ActivityIndicatorSize.Small)
            } else {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_auto_awesome),
                    contentDescription = null,
                    tint = if (enabled) colors.tint else colors.tertiaryLabel,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    ) {
        val label = if (generating) {
            R.string.git_commit_generating
        } else {
            R.string.git_commit_generate_message
        }
        Text(
            text = stringResource(label),
            style = LpmTheme.typography.body,
            color = if (enabled) colors.tint else colors.tertiaryLabel
        )
    }
}

/**
 * A file in the commit sheet: its checkmark, status letter, name over folder, and
 * +N −M. The whole row includes or leaves it out, and reads as the file's path.
 */
@Composable
private fun CommitFileRow(file: CommitFile, onToggle: () -> Unit) {
    val colors = LpmTheme.colors
    val path = file.file.path
    val value = stringResource(
        if (file.selected) R.string.git_commit_included else R.string.git_commit_excluded
    )
    ListRow(
        onClick = onToggle,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = path
            stateDescription = value
        },
        leadingSlot = ListLeadingSlot(width = 24.dp, spacing = 12.dp),
        leading = {
            Icon(
                painter = painterResource(if (file.selected) CHECKED_ICON else UNCHECKED_ICON),
                contentDescription = null,
                tint = if (file.selected) colors.tint else colors.secondaryLabel,
                modifier = Modifier.size(24.dp)
            )
        },
        trailing = {
            if (file.added != null && file.removed != null) {
                DiffStat(file.added, file.removed, Modifier.padding(start = 8.dp))
            }
        }
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileStatusBadge(file.file.status)
            FileName(file.file, Modifier.weight(1f))
        }
    }
}

/** The desktop's ConflictNotice: which files still have merge conflicts. */
@Composable
private fun conflictNotice(paths: List<String>): String {
    val named = if (paths.size > SHOWN_CONFLICTS) {
        stringResource(
            R.string.git_commit_conflicts_more,
            paths.take(SHOWN_CONFLICTS).joinToString(", "),
            paths.size - SHOWN_CONFLICTS
        )
    } else {
        paths.joinToString(", ")
    }
    return pluralStringResource(R.plurals.git_commit_conflicts, paths.size, named)
}

private const val SHOWN_CONFLICTS = 3
private val CHECKED_ICON = DesignR.drawable.ic_check_circle_fill
private val UNCHECKED_ICON = DesignR.drawable.ic_circle
