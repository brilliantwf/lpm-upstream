package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/**
 * The review's one action, pinned under the list so it stays in reach however far
 * the diffs scroll: "Commit N files", which opens the commit sheet.
 */
@Composable
internal fun CommitBar(
    selected: Int,
    committing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val enabled = !committing
    val content = if (enabled) Color.White else colors.tertiaryLabel
    Box(
        modifier
            .fillMaxWidth()
            .background(
                colors.systemBackground.copy(alpha = BAR_ALPHA)
                    .compositeOver(colors.secondarySystemBackground)
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .alpha(if (pressed) 0.75f else 1f)
                .clip(CircleShape)
                .background(if (enabled) colors.tint else colors.tertiarySystemFill)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (committing) {
                ActivityIndicator(size = ActivityIndicatorSize.Small, color = Color.White)
            } else {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = if (selected > 0) {
                    pluralStringResource(R.plurals.git_review_commit_files, selected, selected)
                } else {
                    stringResource(R.string.git_review_commit)
                },
                style = LpmTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = content
            )
        }
    }
}

/** iOS's `.bar` material over the plain list, with nothing scrolled beneath it. */
private const val BAR_ALPHA = 0.6f
