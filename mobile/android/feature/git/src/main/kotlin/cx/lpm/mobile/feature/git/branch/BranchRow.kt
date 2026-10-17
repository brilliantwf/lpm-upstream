package cx.lpm.mobile.feature.git.branch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.CapsuleTag
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitBranch
import java.time.Instant

/**
 * A branch to check out: a checkmark on the current one, its name over how long ago
 * it was committed to, and the remote's name on a remote-only branch (a spinner
 * while it's being checked out). The current branch, or any while one is being
 * checked out or the list is refreshing, can't be picked, and is dimmed like a
 * disabled button: until the refresh lands the checkmark can be on the branch it
 * was the last time the sheet opened.
 */
@Composable
internal fun BranchRow(
    branch: GitBranch,
    current: Boolean,
    checkingOut: String?,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val enabled = !current && checkingOut == null && !loading
    val dim = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)
    val subtitle = remember(branch.committerDate) {
        branchSubtitle(branch.committerDate, Instant.now())
    }
    ListRow(
        modifier = modifier.semantics { selected = current },
        onClick = onClick,
        enabled = enabled,
        leadingSlot = ListLeadingSlot(width = 22.dp, spacing = 12.dp),
        verticalPadding = 14.dp,
        leading = {
            Icon(
                painter = painterResource(if (current) CHECKED else UNCHECKED),
                contentDescription = null,
                tint = if (current) colors.tint else colors.secondaryLabel,
                modifier = dim.size(22.dp)
            )
        },
        trailing = {
            Spacer(Modifier.width(8.dp))
            when {
                checkingOut == branch.name -> ActivityIndicator(size = ActivityIndicatorSize.Small)

                branch.isRemote -> CapsuleTag(
                    text = branch.remote,
                    containerColor = colors.tertiarySystemFill,
                    textStyle = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
                    modifier = dim
                )
            }
        }
    ) {
        Column(
            modifier = dim.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = branch.name,
                style = LpmTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis
            )
            subtitle?.let {
                Text(text = it, style = LpmTheme.typography.caption1, color = colors.secondaryLabel)
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
private val CHECKED = DesignR.drawable.ic_check_circle_fill
private val UNCHECKED = DesignR.drawable.ic_circle
